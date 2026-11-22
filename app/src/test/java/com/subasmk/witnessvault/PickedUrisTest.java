package com.subasmk.witnessvault;

import static org.junit.Assert.*;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class PickedUrisTest {
  private static Uri uri(String s) {
    return Uri.parse("content://media/external/images/" + s);
  }

  @Test
  public void nullDataYieldsEmpty() {
    assertTrue(MainActivity.pickedUris(null).isEmpty());
  }

  @Test
  public void singlePickUsesDataUri() {
    Intent data = new Intent();
    data.setData(uri("100"));
    List<Uri> uris = MainActivity.pickedUris(data);
    assertEquals(1, uris.size());
    assertEquals(uri("100"), uris.get(0));
  }

  @Test
  public void multiPickReadsEveryClipItem() {
    ClipData clip =
        new ClipData(
            "picked",
            new String[] {"image/*"},
            new ClipData.Item(uri("1")));
    clip.addItem(new ClipData.Item(uri("2")));
    clip.addItem(new ClipData.Item(uri("3")));
    Intent data = new Intent();
    data.setClipData(clip);
    List<Uri> uris = MainActivity.pickedUris(data);
    assertEquals(3, uris.size());
    assertEquals(uri("1"), uris.get(0));
    assertEquals(uri("2"), uris.get(1));
    assertEquals(uri("3"), uris.get(2));
  }

  @Test
  public void multiPickSurvivesResultGuard() {
    // The old result guard rejected any picker result whose top-level data Uri was null,
    // which is exactly what a multi-select ClipData result can look like.
    ClipData clip =
        new ClipData("picked", new String[] {"image/*"}, new ClipData.Item(uri("7")));
    Intent data = new Intent();
    data.setClipData(clip);
    assertNull(data.getData());
    assertFalse(
        data.getData() == null && data.getClipData() == null);
    assertEquals(1, MainActivity.pickedUris(data).size());
  }
}
