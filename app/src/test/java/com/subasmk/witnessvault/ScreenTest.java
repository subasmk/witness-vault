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

  private MainActivity unlocked() throws Exception {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    Field f = MainActivity.class.getDeclaredField("unlocked");
    f.setAccessible(true);
    f.set(a, true);
    return a;
  }

  private void invoke(MainActivity a, String name) throws Exception {
    Method m = MainActivity.class.getDeclaredMethod(name);
    m.setAccessible(true);
    m.invoke(a);
    Thread.sleep(150);
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  }

  private android.widget.LinearLayout body(MainActivity a) throws Exception {
    Field body = MainActivity.class.getDeclaredField("body");
    body.setAccessible(true);
    return (android.widget.LinearLayout) body.get(a);
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
    MainActivity a = unlocked();
    invoke(a, "folders");
    Field progress = MainActivity.class.getDeclaredField("progress");
    progress.setAccessible(true);
    render(a, "empty-vault");
  }

  @Test
  public void renderFolderCard() throws Exception {
    MainActivity a = unlocked();
    invoke(a, "folders");
    Field progress = MainActivity.class.getDeclaredField("progress");
    progress.setAccessible(true);
    android.widget.LinearLayout content = body(a);
    content.removeAllViews();
    Ui.title(a, content, "Your vault");
    Method add = MainActivity.class.getDeclaredMethod("addFolderRow", VaultStore.Folder.class);
    add.setAccessible(true);
    add.invoke(a, new VaultStore.Folder(
        "00000000-0000-0000-0000-000000000000", "Mine", 3, null));
    render(a, "folder-card");
  }

  @Test
  public void renderItemCard() throws Exception {
    MainActivity a = unlocked();
    invoke(a, "folders");
    android.widget.LinearLayout content = body(a);
    content.removeAllViews();
    Method label = null;
    Ui.title(a, content, "Mine");
    VaultStore.Folder f =
        new VaultStore.Folder("00000000-0000-0000-0000-000000000000", "Mine", 3, null);
    Method add = MainActivity.class.getDeclaredMethod("addItemRow", VaultStore.Folder.class, VaultStore.Item.class);
    add.setAccessible(true);
    add.invoke(a, f, new VaultStore.Item(
        "11111111-1111-1111-1111-111111111111", "Disposable test video", "video/mp4", 2_400_000));
    add.invoke(a, f, new VaultStore.Item(
        "22222222-2222-2222-2222-222222222222", "scan_notes.jpg", "image/jpeg", 4_200_000));
    render(a, "item-card");
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
