package com.subasmk.witnessvault;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;

/**
 * Real Android checks. These must be run separately on a device, not counted as host test passes.
 */
@RunWith(AndroidJUnit4.class)
public class DeviceSmokeTest {
  @Test
  public void actualMlKitAndModelInitialize() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    try (FaceEngine e = new FaceEngine(c)) {
      Bitmap b = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888);
      b.eraseColor(Color.GRAY);
      assertEquals(0, e.inspect(b).count);
      b.recycle();
    }
  }

  @Test
  public void actualKeystoreEncryptedFolderItemAndRead() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File source = new File(c.getCacheDir(), "smoke-test.jpg");
    Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
    b.eraseColor(Color.GREEN);
    try (FileOutputStream out = new FileOutputStream(source)) {
      b.compress(Bitmap.CompressFormat.JPEG, 90, out);
    }
    b.recycle();
    VaultStore store = new VaultStore(c);
    float[] fakeIdentity = new float[128];
    fakeIdentity[0] = 1;
    VaultStore.Folder folder = store.createFolder("Smoke folder", fakeIdentity);
    VaultStore.Item i =
        store.addItem(folder.id, Uri.fromFile(source), "Disposable smoke-test image", "image/jpeg");
    assertEquals(128, store.getFolder(folder.id).face.length);
    assertEquals(1, store.listItems(folder.id).size());
    store.renameFolder(folder.id, "Smoke renamed");
    assertEquals("Smoke renamed", store.getFolder(folder.id).name);
    File preview = store.preview(folder.id, i.id);
    assertTrue(preview.length() > 0);
    assertNotNull(BitmapFactory.decodeFile(preview.getPath()));
    assertThrows(SecurityException.class, () -> store.deleteItem(folder.id, i.id, null, 5000));
    assertEquals(i.id, store.getItem(folder.id, i.id).id);
    // Remove only this instrumentation-created disposable fixture using the valid state machine.
    Challenge proof = new Challenge(i.id, 0);
    long t = 0;
    for (int k = 0; k < 3; k++) proof.accept(t += 250, 1, .99f, 0, 0, 1, 1);
    proof.accept(t += 250, 1, .99f, 0, 0, 0, 0);
    for (int k = 0; k < 3; k++) proof.accept(t += 250, 1, .99f, 0, 0, 1, 1);
    store.deleteItem(folder.id, i.id, proof, t);
    assertEquals(0, store.listItems(folder.id).size());
    Challenge folderProof = new Challenge(folder.id, 0);
    t = 0;
    for (int k = 0; k < 3; k++) folderProof.accept(t += 250, 1, .99f, 0, 0, 1, 1);
    folderProof.accept(t += 250, 1, .99f, 0, 0, 0, 0);
    for (int k = 0; k < 3; k++) folderProof.accept(t += 250, 1, .99f, 0, 0, 1, 1);
    store.deleteFolder(folder.id, folderProof, t);
    assertEquals(0, store.listFolders().size());
    assertTrue(source.exists());
    preview.delete();
    source.delete();
  }
}
