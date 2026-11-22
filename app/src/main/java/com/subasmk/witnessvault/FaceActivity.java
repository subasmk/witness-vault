package com.subasmk.witnessvault;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.*;
import java.util.concurrent.*;

public class FaceActivity extends FragmentActivity {
  private TextView prompt, detail;
  private PreviewView preview;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private FaceEngine engine;
  private VaultStore store;
  private VaultStore.Item item;
  private Challenge challenge;
  private ProcessCameraProvider provider;
  private volatile boolean ended = false;
  private volatile boolean deletionStarted = false;
  private long last = 0;
  private String id;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    Ui.secure(this);
    id = getIntent().getStringExtra("id");
    LinearLayout l = Ui.column(this);
    l.addView(Ui.text(this, "F R E S H   F A C E   C H E C K", 12, Ui.GREEN));
    Ui.title(this, l, "Only this item's face");
    prompt = Ui.text(this, "Preparing camera...", 21, Ui.INK);
    l.addView(prompt);
    preview = new PreviewView(this);
    preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    l.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    detail =
        Ui.text(
            this,
            "Use good light. One face only. Follow the random prompts.\n"
                + "Experimental check, not replay/deepfake-proof.",
            13,
            Ui.MUTED);
    l.addView(detail);
    l.addView(Ui.button(this, "Cancel, keep the item", this::finish));
    setContentView(l);
    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
      prepare();
    else requestPermissions(new String[] {Manifest.permission.CAMERA}, 20);
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    if (r == 20 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) prepare();
    else fail("Camera permission is needed. The item was kept.");
  }

  private void prepare() {
    worker.execute(
        () -> {
          try {
            store = new VaultStore(this);
            item = store.get(id);
            engine = new FaceEngine(this);
            runOnUiThread(this::camera);
          } catch (Exception e) {
            runOnUiThread(() -> fail("Could not prepare face check. The item was kept."));
          }
        });
  }

  private void camera() {
    if (ended || isFinishing()) return;
    ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
    future.addListener(
        () -> {
          try {
            provider = future.get();
            Preview p = new Preview.Builder().build();
            p.setSurfaceProvider(preview.getSurfaceProvider());
            ImageAnalysis a =
                new ImageAnalysis.Builder()
                    .setTargetResolution(new android.util.Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build();
            startedAt = SystemClock.elapsedRealtime();
            challenge = new Challenge(id, startedAt);
            deadlineHandler.postDelayed(deadline, Challenge.TTL_MS);
            prompt.setText(challenge.prompt());
            a.setAnalyzer(worker, this::frame);
            provider.unbindAll();
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, p, a);
          } catch (Exception e) {
            fail("Front camera unavailable. The item was kept.");
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  private void frame(ImageProxy image) {
    Bitmap b = null;
    try {
      long now = SystemClock.elapsedRealtime();
      if (ended || now - last < 220) return;
      last = now;
      if (challenge == null) return;
      ImageProxy.PlaneProxy plane = image.getPlanes()[0];
      Bitmap raw =
          Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
      raw.copyPixelsFromBuffer(
          RgbaFrames.compact(
              plane.getBuffer(),
              image.getWidth(),
              image.getHeight(),
              plane.getRowStride(),
              plane.getPixelStride()));
      Matrix m = new Matrix();
      m.postRotate(image.getImageInfo().getRotationDegrees());
      b = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), m, true);
      if (raw != b) raw.recycle();
      FaceEngine.Sample s = engine.inspect(b);
      float similarity = FaceEngine.similarity(s.vector, item.face);
      boolean complete =
          challenge.accept(
              SystemClock.elapsedRealtime(), s.count, similarity, s.yaw, s.roll, s.left, s.right);
      String hint =
          s.count != 1
              ? "Keep one clear, large face in the frame."
              : similarity < Challenge.MIN_SIMILARITY
                  ? "Face did not match. Nothing will be deleted."
                  : "Face matched · step " + Math.min(challenge.step() + 1, 7) + " / 7";
      String next = challenge.prompt();
      runOnUiThread(
          () -> {
            if (!ended) {
              prompt.setText(next);
              detail.setText(
                  hint + "\nCamera-only check can be fooled by replay/deepfake attacks.");
            }
          });
      if (SystemClock.elapsedRealtime() - getStart() > Challenge.TTL_MS) {
        runOnUiThread(
            () -> fail("Check timed out. The item was kept. Start a fresh check to retry."));
        return;
      }
      if (complete && !ended) {
        ended = true;
        deletionStarted = true;
        store.delete(id, challenge, SystemClock.elapsedRealtime());
        runOnUiThread(
            () -> {
              if (provider != null) provider.unbindAll();
              setResult(RESULT_OK);
              new android.app.AlertDialog.Builder(this)
                  .setTitle("Vault copy deleted")
                  .setMessage("The original in your gallery was not changed.")
                  .setCancelable(false)
                  .setPositiveButton("Back to vault", (d, w) -> finish())
                  .show();
            });
      }
    } catch (Exception e) {
      runOnUiThread(
          () ->
              fail(
                  deletionStarted
                      ? "Deletion hit a storage error. Return to the vault to check the copy. Your"
                            + " original gallery file was not changed."
                      : "Face check could not finish. The item was kept."));
    } finally {
      if (b != null) b.recycle();
      image.close();
    }
  }

  private final Handler deadlineHandler = new Handler(Looper.getMainLooper());
  private final Runnable deadline =
      () -> {
        if (!ended && !isFinishing())
          fail("Check timed out. The item was kept. Start a fresh check to retry.");
      };
  private long startedAt = 0;

  private long getStart() {
    if (startedAt == 0) startedAt = SystemClock.elapsedRealtime();
    return startedAt;
  }

  private void fail(String s) {
    if (isFinishing()) return;
    ended = true;
    if (provider != null) provider.unbindAll();
    new android.app.AlertDialog.Builder(this)
        .setTitle(deletionStarted ? "Check vault copy" : "Item kept")
        .setMessage(s)
        .setCancelable(false)
        .setPositiveButton("Back", (d, w) -> finish())
        .show();
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (!isFinishing()) {
      ended = true;
      if (provider != null) provider.unbindAll();
      finish();
    }
  }

  @Override
  protected void onDestroy() {
    ended = true;
    deadlineHandler.removeCallbacks(deadline);
    worker.execute(
        () -> {
          if (engine != null) engine.close();
        });
    worker.shutdown();
    super.onDestroy();
  }
}
