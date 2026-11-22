package com.subasmk.witnessvault;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.tensorflow.lite.Interpreter;

public final class FaceEngine implements AutoCloseable {
  private final Interpreter interpreter;
  private final FaceDetector detector;

  public FaceEngine(Context c) throws Exception {
    try (android.content.res.AssetFileDescriptor a = c.getAssets().openFd("facenet.tflite");
        FileInputStream in = new FileInputStream(a.getFileDescriptor())) {
      interpreter =
          new Interpreter(
              in.getChannel()
                  .map(FileChannel.MapMode.READ_ONLY, a.getStartOffset(), a.getDeclaredLength()),
              new Interpreter.Options().setNumThreads(2));
    }
    detector =
        FaceDetection.getClient(
            new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.2f)
                .build());
  }

  public static final class Sample {
    public final int count;
    public final float[] vector;
    public final float yaw, roll, left, right;

    Sample(int n, float[] v, float y, float r, float l, float e) {
      count = n;
      vector = v;
      yaw = y;
      roll = r;
      left = l;
      right = e;
    }
  }

  public Sample inspect(Bitmap bitmap) throws Exception {
    List<Face> faces =
        Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)), 8, TimeUnit.SECONDS);
    if (faces.size() != 1) return new Sample(faces.size(), null, 0, 0, -1, -1);
    Face f = faces.get(0);
    Rect b = f.getBoundingBox();
    if (b.width() < 90 || b.height() < 90) return new Sample(0, null, 0, 0, -1, -1);
    int side = (int) (Math.max(b.width(), b.height()) * 1.06);
    int x = Math.max(0, b.centerX() - side / 2), y = Math.max(0, b.centerY() - side / 2);
    int w = Math.min(side, bitmap.getWidth() - x), h = Math.min(side, bitmap.getHeight() - y);
    Bitmap crop = Bitmap.createBitmap(bitmap, x, y, w, h);
    Bitmap scaled = Bitmap.createScaledBitmap(crop, 160, 160, true);
    int[] pixels = new int[25600];
    scaled.getPixels(pixels, 0, 160, 0, 0, 160, 160);
    float[] rgb = new float[76800];
    double sum = 0;
    int pos = 0;
    for (int p : pixels) {
      rgb[pos++] = (p >> 16) & 255;
      rgb[pos++] = (p >> 8) & 255;
      rgb[pos++] = p & 255;
    }
    for (float f1 : rgb) sum += f1;
    float mean = (float) (sum / rgb.length);
    double variance = 0;
    for (float f1 : rgb) variance += (f1 - mean) * (f1 - mean);
    float std =
        Math.max((float) Math.sqrt(variance / rgb.length), 1f / (float) Math.sqrt(rgb.length));
    ByteBuffer input = ByteBuffer.allocateDirect(rgb.length * 4).order(ByteOrder.nativeOrder());
    for (float f1 : rgb) input.putFloat((f1 - mean) / std);
    input.rewind();
    float[][] out = new float[1][128];
    interpreter.run(input, out);
    double norm = 0;
    for (float v : out[0]) norm += v * v;
    if (!Double.isFinite(norm) || norm < 1e-9)
      throw new IOException("Face model returned no usable identity");
    norm = Math.sqrt(norm);
    for (int i = 0; i < 128; i++) out[0][i] /= (float) norm;
    if (scaled != crop) scaled.recycle();
    if (crop != bitmap) crop.recycle();
    return new Sample(
        1,
        out[0],
        f.getHeadEulerAngleY(),
        f.getHeadEulerAngleZ(),
        f.getLeftEyeOpenProbability() == null ? -1 : f.getLeftEyeOpenProbability(),
        f.getRightEyeOpenProbability() == null ? -1 : f.getRightEyeOpenProbability());
  }

  public static float similarity(float[] a, float[] b) {
    if (a == null || b == null || a.length != b.length) return -1;
    float s = 0;
    for (int i = 0; i < a.length; i++) s += a[i] * b[i];
    return s;
  }

  public static Bitmap load(Context c, Uri uri) throws Exception {
    BitmapFactory.Options o = new BitmapFactory.Options();
    o.inJustDecodeBounds = true;
    try (InputStream in = c.getContentResolver().openInputStream(uri)) {
      BitmapFactory.decodeStream(in, null, o);
    }
    if (o.outWidth < 1 || o.outHeight < 1) throw new IOException("Choose a readable face photo");
    o.inSampleSize = 1;
    while (Math.max(o.outWidth, o.outHeight) / o.inSampleSize > 1600) o.inSampleSize *= 2;
    o.inJustDecodeBounds = false;
    Bitmap bitmap;
    try (InputStream in = c.getContentResolver().openInputStream(uri)) {
      bitmap = BitmapFactory.decodeStream(in, null, o);
    }
    if (bitmap == null) throw new IOException("Cannot read photo");
    int orientation;
    try (InputStream in = c.getContentResolver().openInputStream(uri)) {
      orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
    }
    Matrix m = new Matrix();
    switch (orientation) {
      case 2:
        m.setScale(-1, 1);
        break;
      case 3:
        m.setRotate(180);
        break;
      case 4:
        m.setScale(1, -1);
        break;
      case 5:
        m.setRotate(90);
        m.postScale(-1, 1);
        break;
      case 6:
        m.setRotate(90);
        break;
      case 7:
        m.setRotate(270);
        m.postScale(-1, 1);
        break;
      case 8:
        m.setRotate(270);
        break;
    }
    Bitmap result =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
    if (result != bitmap) bitmap.recycle();
    return result;
  }

  public void close() {
    detector.close();
    interpreter.close();
  }
}
