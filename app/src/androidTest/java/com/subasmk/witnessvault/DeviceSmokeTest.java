package com.subasmk.witnessvault;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
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
  public void actualKeystoreEncryptedImportAndRead() throws Exception {
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
    VaultStore.Item i =
        store.add(Uri.fromFile(source), "Disposable smoke-test image", "image/jpeg", fakeIdentity);
    assertEquals(128, store.get(i.id).face.length);
    File preview = store.preview(i.id);
    assertTrue(preview.length() > 0);
    assertNotNull(BitmapFactory.decodeFile(preview.getPath()));
    assertThrows(SecurityException.class, () -> store.delete(i.id, null, 5000));
    assertEquals(i.id, store.get(i.id).id);
    // Remove only this instrumentation-created disposable fixture using the valid state machine.
    Challenge proof = new Challenge(i.id, 0, true);
    long t = 0;
    for (float[] frame :
        new float[][] {{0, 1}, {-23, 1}, {0, 1}, {23, 1}, {0, 1}, {0, 0}, {0, 1}, {0, 1}}) {
      int n = frame[1] == 0 ? 1 : 3;
      for (int k = 0; k < n; k++) proof.accept(t += 250, 1, .99f, frame[0], 0, frame[1], frame[1]);
    }
    store.delete(i.id, proof, t);
    assertTrue(source.exists());
    preview.delete();
    source.delete();
  }
}
