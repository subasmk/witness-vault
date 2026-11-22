package com.subasmk.witnessvault;

import java.io.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Bounded-memory authenticated media format. Chunks and terminator are individually bound to item
 * and order.
 */
final class MediaCipher {
  private static Cipher cipher(SecretKey key, int mode, byte[] iv, String aad) throws Exception {
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    if (iv == null) c.init(mode, key);
    else c.init(mode, key, new GCMParameterSpec(128, iv));
    c.updateAAD(aad.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return c;
  }

  static long encrypt(InputStream in, File out, String aad, SecretKey key) throws Exception {
    long size = 0;
    int index = 0;
    try (FileOutputStream raw = new FileOutputStream(out);
        DataOutputStream data = new DataOutputStream(raw)) {
      data.writeInt(0x57563101);
      byte[] buffer = new byte[65536];
      int n;
      while ((n = in.read(buffer)) != -1) {
        if (n == 0) continue;
        size += n;
        if (size > 512L * 1024 * 1024) throw new IOException("Use files smaller than 512 MB");
        Cipher c = cipher(key, Cipher.ENCRYPT_MODE, null, aad + ":" + index++);
        byte[] encrypted = c.doFinal(buffer, 0, n);
        data.writeInt(encrypted.length);
        data.write(c.getIV());
        data.write(encrypted);
      }
      Cipher end = cipher(key, Cipher.ENCRYPT_MODE, null, aad + ":" + index + ":end");
      byte[] tag = end.doFinal();
      data.writeInt(0);
      data.write(end.getIV());
      data.write(tag);
      data.flush();
      raw.getFD().sync();
    }
    return size;
  }

  static byte[] decryptSmall(File file, String aad, SecretKey key) throws Exception {
    if (file.length() > 1024 * 1024) throw new IOException("Invalid metadata size");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    decrypt(file, out, aad, key);
    return out.toByteArray();
  }

  private static byte[] read(DataInputStream in, int length) throws IOException {
    byte[] b = new byte[length];
    in.readFully(b);
    return b;
  }

  static void decrypt(File file, OutputStream out, String aad, SecretKey key) throws Exception {
    try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
      if (in.readInt() != 0x57563101) throw new IOException("Invalid encrypted format");
      int index = 0;
      while (true) {
        int length = in.readInt();
        if (length < 0 || length > 65552) throw new IOException("Invalid encrypted chunk");
        byte[] iv = read(in, 12);
        if (length == 0) {
          cipher(key, Cipher.DECRYPT_MODE, iv, aad + ":" + index + ":end").doFinal(read(in, 16));
          if (in.read() != -1) throw new IOException("Extra encrypted bytes");
          return;
        }
        if (length < 17) throw new IOException("Invalid encrypted chunk");
        out.write(
            cipher(key, Cipher.DECRYPT_MODE, iv, aad + ":" + index++).doFinal(read(in, length)));
      }
    }
  }
}
