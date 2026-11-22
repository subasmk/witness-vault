package com.subasmk.witnessvault;

import java.nio.ByteBuffer;

/** Copies only actual RGBA pixels, including cameras whose final row has no trailing padding. */
final class RgbaFrames {
  static ByteBuffer compact(
      ByteBuffer input, int width, int height, int rowStride, int pixelStride) {
    if (width < 1 || height < 1 || pixelStride != 4 || rowStride < width * 4)
      throw new IllegalArgumentException("Unsupported camera plane");
    long needed = (long) (height - 1) * rowStride + (long) width * 4;
    if (needed > input.limit()) throw new IllegalArgumentException("Incomplete camera plane");
    ByteBuffer source = input.duplicate();
    ByteBuffer result = ByteBuffer.allocateDirect(width * height * 4);
    byte[] row = new byte[width * 4];
    for (int y = 0; y < height; y++) {
      source.position(y * rowStride);
      source.get(row);
      result.put(row);
    }
    result.flip();
    return result;
  }
}
