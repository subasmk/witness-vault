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

/** Per-item encrypted directories. A successful atomic rename is the import commit point. */
public final class VaultStore {
  private final Context context;
  private final File root;

  public static final class Item {
    public final String id, name, mime;
    public final long size;
    public final float[] face;

    Item(String id, String name, String mime, long size, float[] face) {
      this.id = id;
      this.name = name;
      this.mime = mime;
      this.size = size;
      this.face = face;
    }
  }

  public VaultStore(Context c) throws IOException {
    context = c.getApplicationContext();
    root = new File(context.getNoBackupFilesDir(), "vault");
    if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot open vault");
    for (File f : Objects.requireNonNull(root.listFiles()))
      if (f.getName().startsWith("pending-")) erase(f);
  }

  private SecretKey key() throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if (!ks.containsAlias("witness-v1")) {
      KeyGenerator kg =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
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

  public synchronized Item add(Uri uri, String name, String mime, float[] face) throws Exception {
    if (face == null || face.length != 128) throw new IllegalArgumentException("No enrolled face");
    if (mime == null || !(mime.startsWith("video/") || mime.startsWith("image/")))
      throw new IOException("Choose a photo or video");
    String id = UUID.randomUUID().toString();
    File dir = new File(root, "pending-" + id);
    if (!dir.mkdir()) throw new IOException("Cannot import");
    try {
      long size;
      try (InputStream in = context.getContentResolver().openInputStream(uri)) {
        if (in == null) throw new IOException("Cannot read original");
        size = MediaCipher.encrypt(in, new File(dir, "media"), id + ":media", key());
      }
      if (size == 0) throw new IOException("Empty file");
      JSONObject j = new JSONObject().put("name", name).put("mime", mime).put("size", size);
      JSONArray a = new JSONArray();
      for (float f : face) a.put(f);
      j.put("face", a);
      try (InputStream in =
          new ByteArrayInputStream(
              j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
        MediaCipher.encrypt(in, new File(dir, "meta"), id + ":meta", key());
      }
      if (!dir.renameTo(new File(root, id))) throw new IOException("Could not finish import");
      return new Item(id, name, mime, size, face.clone());
    } catch (Exception e) {
      erase(dir);
      throw e;
    }
  }

  private File dir(String id) {
    if (!id.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid item");
    return new File(root, id);
  }

  public synchronized Item get(String id) throws Exception {
    JSONObject j =
        new JSONObject(
            new String(
                MediaCipher.decryptSmall(new File(dir(id), "meta"), id + ":meta", key()),
                java.nio.charset.StandardCharsets.UTF_8));
    JSONArray a = j.getJSONArray("face");
    if (a.length() != 128) throw new IOException("Invalid identity record");
    float[] f = new float[128];
    for (int i = 0; i < 128; i++) f[i] = (float) a.getDouble(i);
    return new Item(id, j.getString("name"), j.getString("mime"), j.getLong("size"), f);
  }

  public synchronized List<Item> list() throws Exception {
    List<Item> result = new ArrayList<>();
    File[] files = root.listFiles();
    if (files == null) throw new IOException("Cannot list vault");
    for (File f : files) if (!f.getName().startsWith("pending-")) result.add(get(f.getName()));
    result.sort(Comparator.comparing(i -> i.name));
    return result;
  }

  public synchronized File preview(String id) throws Exception {
    clearPreviews(context);
    File out = new File(context.getCacheDir(), "view-" + id);
    try (FileOutputStream s = new FileOutputStream(out)) {
      MediaCipher.decrypt(new File(dir(id), "media"), s, id + ":media", key());
      return out;
    } catch (Exception e) {
      out.delete();
      throw e;
    }
  }

  public synchronized void delete(String id, Challenge proof, long now) throws Exception {
    if (proof == null || !proof.consume(id, now))
      throw new SecurityException("Fresh face check required");
    File d = dir(id);
    if (!d.isDirectory()) throw new IOException("Item missing");
    File tombstone = new File(root, "pending-delete-" + id);
    if (!d.renameTo(tombstone)) throw new IOException("Could not commit deletion");
    erase(tombstone);
    if (tombstone.exists()) throw new IOException("Vault entry removed; encrypted cleanup pending");
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
