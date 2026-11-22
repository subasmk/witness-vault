package com.subasmk.witnessvault;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import org.junit.Test;

public class RgbaFramesTest {
  @Test
  public void paddedLastRowNeedsNoExtraBytes() {
    byte[] b = {1, 2, 3, 4, 5, 6, 7, 8, 99, 99, 99, 99, 9, 10, 11, 12, 13, 14, 15, 16};
    ByteBuffer out = RgbaFrames.compact(ByteBuffer.wrap(b), 2, 2, 12, 4);
    byte[] actual = new byte[16];
    out.get(actual);
    assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}, actual);
  }

  @Test
  public void normalPlaneCopied() {
    ByteBuffer b = ByteBuffer.wrap(new byte[16]);
    assertEquals(16, RgbaFrames.compact(b, 2, 2, 8, 4).remaining());
    assertEquals(0, b.position());
  }

  @Test
  public void truncatedPlaneFails() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RgbaFrames.compact(ByteBuffer.allocate(15), 2, 2, 8, 4));
  }

  @Test
  public void unexpectedPixelStrideFails() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RgbaFrames.compact(ByteBuffer.allocate(16), 2, 2, 8, 3));
  }
}
