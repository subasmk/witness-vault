package com.subasmk.witnessvault;

import android.content.Context;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import org.json.*;

/**
 * Face-keyed folders of encrypted items. A folder owns one enrolled face template; deleting any
 * item (or the folder) needs a fresh live proof against it. Atomic rename is the commit point.
 */
public final class VaultStore {
  private final Context context;
  private final File root;

  public static final class Folder {
    public final String id, name;
    public final int count;
    public final float[] face;

    Folder(String id, String name, int count, float[] face) {
      this.id = id;
      this.name = name;
      this.count = count;
      this.face = face;
    }
  }

  public static final class Item {
    public final String id, name, mime;
    public final long size;

    Item(String id, String name, String mime, long size) {
      this.id = id;
      this.name = name;
      this.mime = mime;
      this.size = size;
    }
  }

  public VaultStore(Context c) throws IOException {
    context = c.getApplicationContext();
    root = new File(context.getNoBackupFilesDir(), "vault");
    if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot open vault");
    for (File f : Objects.requireNonNull(root.listFiles())) {
      if (f.getName().startsWith("pending-")) erase(f);
      File items = new File(f, "items");
      if (items.isDirectory())
        for (File i : Objects.requireNonNull(items.listFiles()))
          if (i.getName().startsWith("pending-")) erase(i);
    }
  }

  private SecretKey key() throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if (!ks.containsAlias("witness-v1")) {
      KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      kg.init(
          new KeyGenParameterSpec.Builder(
                  "witness-v1", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .setKeySize(256)
              .build());
      kg.generateKey();
    }
    return (SecretKey) ks.getKey("witness-v1", null);
  }

  private File folderDir(String folderId) {
    if (folderId == null || !folderId.matches("[0-9a-f-]{36}"))
      throw new IllegalArgumentException("Invalid folder");
    return new File(root, folderId);
  }

  private File itemDir(String folderId, String itemId) {
    if (itemId == null || !itemId.matches("[0-9a-f-]{36}"))
      throw new IllegalArgumentException("Invalid item");
    return new File(new File(folderDir(folderId), "items"), itemId);
  }

  public synchronized Folder createFolder(String name, float[] face) throws Exception {
    if (face == null || face.length != 128) throw new IllegalArgumentException("No enrolled face");
    name = cleanName(name);
    String id = UUID.randomUUID().toString();
    File dir = new File(root, "pending-" + id);
    if (!dir.mkdir() || !new File(dir, "items").mkdir()) throw new IOException("Cannot create");
    try {
      writeFolderMeta(dir, id, name, face);
      if (!dir.renameTo(folderDir(id))) throw new IOException("Could not finish");
      return new Folder(id, name, 0, face.clone());
    } catch (Exception e) {
      erase(dir);
      throw e;
    }
  }

  private static String cleanName(String name) throws IOException {
    if (name == null) throw new IOException("Name the folder");
    name = name.trim().replaceAll("\\s+", " ");
    if (name.isEmpty() || name.length() > 40) throw new IOException("Name the folder (1-40)");
    return name;
  }

  private void writeFolderMeta(File dir, String id, String name, float[] face) throws Exception {
    JSONObject j = new JSONObject().put("name", name).put("created", System.currentTimeMillis());
    JSONArray a = new JSONArray();
    for (float f : face) a.put(f);
    j.put("face", a);
    try (InputStream in =
        new ByteArrayInputStream(j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
      MediaCipher.encrypt(in, new File(dir, "meta"), id + ":folder", key());
    }
  }

  public synchronized Folder getFolder(String folderId) throws Exception {
    File dir = folderDir(folderId);
    JSONObject j =
        new JSONObject(
            new String(
                MediaCipher.decryptSmall(new File(dir, "meta"), folderId + ":folder", key()),
                java.nio.charset.StandardCharsets.UTF_8));
    JSONArray a = j.getJSONArray("face");
    if (a.length() != 128) throw new IOException("Invalid identity record");
    float[] f = new float[128];
    for (int i = 0; i < 128; i++) f[i] = (float) a.getDouble(i);
    return new Folder(folderId, j.getString("name"), itemCount(dir), f);
  }

  private int itemCount(File dir) {
    File[] items = new File(dir, "items").listFiles();
    int n = 0;
    if (items != null) for (File i : items) if (!i.getName().startsWith("pending-")) n++;
    return n;
  }

  public synchronized List<Folder> listFolders() throws Exception {
    List<Folder> result = new ArrayList<>();
    File[] files = root.listFiles();
    if (files == null) throw new IOException("Cannot list vault");
    for (File f : files) {
      if (f.getName().startsWith("pending-") || !new File(f, "items").isDirectory()) continue;
      try {
        result.add(getFolder(f.getName()));
      } catch (Exception damaged) {
        result.add(new Folder(f.getName(), "Damaged folder", itemCount(f), null));
      }
    }
    result.sort(Comparator.comparing(x -> x.name.toLowerCase(Locale.US)));
    return result;
  }

  public synchronized void renameFolder(String folderId, String name) throws Exception {
    name = cleanName(name);
    Folder folder = getFolder(folderId);
    writeFolderMeta(folderDir(folderId), folderId, name, folder.face);
  }

  public synchronized Item addItem(String folderId, Uri uri, String name, String mime)
      throws Exception {
    getFolder(folderId);
    if (mime == null || !(mime.startsWith("video/") || mime.startsWith("image/")))
      throw new IOException("Choose a photo or video");
    String id = UUID.randomUUID().toString();
    File items = new File(folderDir(folderId), "items");
    File dir = new File(items, "pending-" + id);
    if (!dir.mkdir()) throw new IOException("Cannot import");
    try {
      long size;
      try (InputStream in = context.getContentResolver().openInputStream(uri)) {
        if (in == null) throw new IOException("Cannot read original");
        size = MediaCipher.encrypt(in, new File(dir, "media"), id + ":media", key());
      }
      if (size == 0) throw new IOException("Empty file");
      JSONObject j = new JSONObject().put("name", name).put("mime", mime).put("size", size);
      try (InputStream in =
          new ByteArrayInputStream(
              j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
        MediaCipher.encrypt(in, new File(dir, "meta"), id + ":meta", key());
      }
      if (!dir.renameTo(new File(items, id))) throw new IOException("Could not finish import");
      return new Item(id, name, mime, size);
    } catch (Exception e) {
      erase(dir);
      throw e;
    }
  }

  public synchronized Item getItem(String folderId, String itemId) throws Exception {
    File dir = itemDir(folderId, itemId);
    JSONObject j =
        new JSONObject(
            new String(
                MediaCipher.decryptSmall(new File(dir, "meta"), itemId + ":meta", key()),
                java.nio.charset.StandardCharsets.UTF_8));
    return new Item(itemId, j.getString("name"), j.getString("mime"), j.getLong("size"));
  }

  public synchronized List<Item> listItems(String folderId) throws Exception {
    List<Item> result = new ArrayList<>();
    File[] files = new File(folderDir(folderId), "items").listFiles();
    if (files == null) return result;
    for (File f : files)
      if (!f.getName().startsWith("pending-")) result.add(getItem(folderId, f.getName()));
    result.sort(Comparator.comparing(i -> i.name.toLowerCase(Locale.US)));
    return result;
  }

  public synchronized File preview(String folderId, String itemId) throws Exception {
    clearPreviews(context);
    File out = new File(context.getCacheDir(), "view-" + itemId);
    try (FileOutputStream s = new FileOutputStream(out)) {
      MediaCipher.decrypt(new File(itemDir(folderId, itemId), "media"), s, itemId + ":media", key());
      return out;
    } catch (Exception e) {
      out.delete();
      throw e;
    }
  }

  public synchronized void deleteItem(String folderId, String itemId, Challenge proof, long now)
      throws Exception {
    if (proof == null || !proof.consume(itemId, now))
      throw new SecurityException("Fresh face check required");
    File d = itemDir(folderId, itemId);
    if (!d.isDirectory()) throw new IOException("Item missing");
    File tombstone = new File(new File(folderDir(folderId), "items"), "pending-delete-" + itemId);
    if (!d.renameTo(tombstone)) throw new IOException("Could not commit deletion");
    erase(tombstone);
    if (tombstone.exists()) throw new IOException("Vault entry removed; encrypted cleanup pending");
  }

  public synchronized void deleteFolder(String folderId, Challenge proof, long now)
      throws Exception {
    if (proof == null || !proof.consume(folderId, now))
      throw new SecurityException("Fresh face check required");
    File d = folderDir(folderId);
    if (!d.isDirectory()) throw new IOException("Folder missing");
    File tombstone = new File(root, "pending-delete-" + folderId);
    if (!d.renameTo(tombstone)) throw new IOException("Could not commit deletion");
    erase(tombstone);
    if (tombstone.exists()) throw new IOException("Folder removed; encrypted cleanup pending");
  }

  private static void erase(File f) {
    if (f.isDirectory()) {
      File[] a = f.listFiles();
      if (a != null) for (File x : a) erase(x);
    }
    f.delete();
  }

  public static void clearPreviews(Context c) {
    File[] a = c.getCacheDir().listFiles();
    if (a != null) for (File f : a) if (f.getName().startsWith("view-")) f.delete();
  }
}
