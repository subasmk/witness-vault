package com.subasmk.witnessvault;

import static org.junit.Assert.*;

import android.graphics.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenTest {
  private void render(MainActivity activity, String name) throws Exception {
    View v = activity.getWindow().getDecorView();
    int width = 1080, height = 2340;
    v.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    v.layout(0, 0, width, height);
    Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    v.draw(new Canvas(b));
    File dir = new File("build/screenshots");
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  @Test
  public void renderLockedScreen() throws Exception {
    MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
    render(activity, "locked");
    assertTrue(
        (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
  }

  @Test
  public void renderEmptyVault() throws Exception {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    Field f = MainActivity.class.getDeclaredField("unlocked");
    f.setAccessible(true);
    f.set(a, true);
    Method refresh = MainActivity.class.getDeclaredMethod("refresh");
    refresh.setAccessible(true);
    refresh.invoke(a);
    Thread.sleep(150);
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    render(a, "empty-vault");
  }

  @Test
  public void renderMediaCard() throws Exception {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    Field f = MainActivity.class.getDeclaredField("unlocked");
    f.setAccessible(true);
    f.set(a, true);
    Method refresh = MainActivity.class.getDeclaredMethod("refresh");
    refresh.setAccessible(true);
    refresh.invoke(a);
    Thread.sleep(150);
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    Field progress = MainActivity.class.getDeclaredField("progress");
    progress.setAccessible(true);
    ((android.widget.TextView) progress.get(a)).setText("1 protected item");
    Field body = MainActivity.class.getDeclaredField("body");
    body.setAccessible(true);
    android.widget.LinearLayout content = (android.widget.LinearLayout) body.get(a);
    content.removeViewAt(content.getChildCount() - 1);
    Method add = MainActivity.class.getDeclaredMethod("addRow", VaultStore.Item.class);
    add.setAccessible(true);
    add.invoke(
        a,
        new VaultStore.Item(
            "00000000-0000-0000-0000-000000000000",
            "Disposable test video",
            "video/mp4",
            2_400_000,
            new float[128]));
    render(a, "media-card");
  }

  @Test
  public void backgroundingRelocksTheVault() throws Exception {
    org.robolectric.android.controller.ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup();
    Field field = MainActivity.class.getDeclaredField("unlocked");
    field.setAccessible(true);
    field.set(c.get(), true);
    c.pause().stop();
    assertFalse(field.getBoolean(c.get()));
    c.destroy();
  }
}
