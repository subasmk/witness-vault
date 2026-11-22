package com.subasmk.witnessvault;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.*;

public class MediaCipherTest {
  File f;
  SecretKey key = new SecretKeySpec(new byte[32], "AES");

  @Before
  public void setup() throws Exception {
    f = File.createTempFile("witness", "test");
  }

  @After
  public void cleanup() {
    f.delete();
  }

  byte[] encrypt(byte[] bytes) throws Exception {
    assertEquals(
        bytes.length, MediaCipher.encrypt(new ByteArrayInputStream(bytes), f, "id:media", key));
    return Files.readAllBytes(f.toPath());
  }

  byte[] decrypt(String aad, SecretKey k) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    MediaCipher.decrypt(f, out, aad, k);
    return out.toByteArray();
  }

  @Test
  public void roundtripPhoto() throws Exception {
    byte[] b = new byte[32000];
    new Random(1).nextBytes(b);
    encrypt(b);
    assertArrayEquals(b, decrypt("id:media", key));
  }

  @Test
  public void roundtripMultichunkVideo() throws Exception {
    byte[] b = new byte[900000];
    new Random(2).nextBytes(b);
    encrypt(b);
    assertArrayEquals(b, decrypt("id:media", key));
  }

  @Test
  public void roundtripEmpty() throws Exception {
    encrypt(new byte[0]);
    assertEquals(0, decrypt("id:media", key).length);
  }

  @Test
  public void randomizedEncryption() throws Exception {
    byte[] a = encrypt(new byte[50]), b = encrypt(new byte[50]);
    assertFalse(Arrays.equals(a, b));
  }

  @Test
  public void doesNotStoreReadablePayload() throws Exception {
    byte[] b = "PRIVATE TEST CONTENT PRIVATE TEST CONTENT".getBytes();
    byte[] c = encrypt(b);
    assertFalse(new String(c).contains("PRIVATE TEST CONTENT"));
  }

  @Test
  public void changedCiphertextRejected() throws Exception {
    byte[] b = encrypt(new byte[500]);
    b[40] ^= 1;
    Files.write(f.toPath(), b);
    assertThrows(Exception.class, () -> decrypt("id:media", key));
  }

  @Test
  public void missingTerminatorRejected() throws Exception {
    byte[] b = encrypt(new byte[500]);
    Files.write(f.toPath(), Arrays.copyOf(b, b.length - 32));
    assertThrows(Exception.class, () -> decrypt("id:media", key));
  }

  @Test
  public void missingEntireFinalChunkRejected() throws Exception {
    byte[] b = encrypt(new byte[70000]);
    Files.write(f.toPath(), Arrays.copyOf(b, 65572));
    assertThrows(Exception.class, () -> decrypt("id:media", key));
  }

  @Test
  public void extraBytesRejected() throws Exception {
    byte[] b = encrypt(new byte[500]);
    Files.write(f.toPath(), Arrays.copyOf(b, b.length + 1));
    assertThrows(Exception.class, () -> decrypt("id:media", key));
  }

  @Test
  public void wrongItemRejected() throws Exception {
    encrypt(new byte[500]);
    assertThrows(Exception.class, () -> decrypt("other:media", key));
  }

  @Test
  public void wrongRecordTypeRejected() throws Exception {
    encrypt(new byte[500]);
    assertThrows(Exception.class, () -> decrypt("id:meta", key));
  }

  @Test
  public void wrongKeyRejected() throws Exception {
    encrypt(new byte[500]);
    byte[] other = new byte[32];
    other[0] = 1;
    assertThrows(Exception.class, () -> decrypt("id:media", new SecretKeySpec(other, "AES")));
  }

  @Test
  public void corruptHeaderRejected() throws Exception {
    byte[] b = encrypt(new byte[500]);
    b[0] = 0;
    Files.write(f.toPath(), b);
    assertThrows(Exception.class, () -> decrypt("id:media", key));
  }

  @Test
  public void chunkReorderingRejected() throws Exception {
    byte[] b = encrypt(new byte[131072]);
    byte[] a = Arrays.copyOfRange(b, 4, 65572), c = Arrays.copyOfRange(b, 65572, 131140);
    System.arraycopy(c, 0, b, 4, c.length);
    System.arraycopy(a, 0, b, 65572, a.length);
    Files.write(f.toPath(), b);
    assertThrows(Exception.class, () -> decrypt("id:media", key));
  }
}
