package com.subasmk.witnessvault;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.*;

public class FaceActivity extends FragmentActivity {
  private TextView prompt, detail, stepMatch, stepBlink;
  private View barFill;
  private PreviewView preview;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private FaceEngine engine;
  private VaultStore store;
  private float[] face;
  private Challenge challenge;
  private ProcessCameraProvider provider;
  private volatile boolean ended = false;
  private volatile boolean deletionStarted = false;
  private long last = 0;
  private long startedAt = 0;
  private String mode, folderId, id;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    Ui.secure(this);
    mode = getIntent().getStringExtra("mode");
    folderId = getIntent().getStringExtra("folder");
    id = getIntent().getStringExtra("id");
    if (mode == null) mode = "item";
    LinearLayout l = Ui.column(this);
    l.addView(Ui.label(this, "LIVE FACE CHECK"));
    Ui.title(this, l, "folder".equals(mode) ? "Destroy folder" : "Delete item");
    prompt = Ui.text(this, "Preparing camera...", 21, Ui.INK);
    l.addView(prompt);
    FrameLayout frame = new FrameLayout(this);
    GradientDrawable stroke = new GradientDrawable();
    stroke.setColor(Ui.BG);
    stroke.setCornerRadius(Ui.dp(this, 18));
    stroke.setStroke(Ui.dp(this, 1.5f), Color.argb(140, 34, 211, 238));
    frame.setBackground(stroke);
    int pad = Ui.dp(this, 2);
    frame.setPadding(pad, pad, pad, pad);
    preview = new PreviewView(this);
    preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    preview.setOutlineProvider(
        new android.view.ViewOutlineProvider() {
          @Override
          public void getOutline(View v, android.graphics.Outline o) {
            o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(FaceActivity.this, 16));
          }
        });
    preview.setClipToOutline(true);
    frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    l.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout steps = new LinearLayout(this);
    steps.setOrientation(LinearLayout.HORIZONTAL);
    steps.setGravity(android.view.Gravity.CENTER);
    stepMatch = step(steps, "1 · FACE MATCH");
    TextView gap = new TextView(this);
    steps.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 16), 1));
    stepBlink = step(steps, "2 · LIVE SIGNAL");
    LinearLayout.LayoutParams sp =
        new LinearLayout.LayoutParams(-1, -2);
    sp.setMargins(0, Ui.dp(this, 12), 0, 0);
    steps.setLayoutParams(sp);
    l.addView(steps);
    FrameLayout bar = new FrameLayout(this);
    GradientDrawable track = new GradientDrawable();
    track.setColor(Color.argb(25, 255, 255, 255));
    track.setCornerRadius(Ui.dp(this, 99));
    bar.setBackground(track);
    barFill = new View(this);
    GradientDrawable fill =
        new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, new int[] {Ui.RED, Color.rgb(251, 146, 60)});
    fill.setCornerRadius(Ui.dp(this, 99));
    barFill.setBackground(fill);
    bar.addView(barFill, new FrameLayout.LayoutParams(1, -1));
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 5));
    bp.setMargins(0, Ui.dp(this, 10), 0, 0);
    bar.setLayoutParams(bp);
    l.addView(bar);
    detail =
        Ui.text(
            this,
            "Use good light. One face only.\nExperimental check, not replay/deepfake-proof.",
            13,
            Ui.MUTED);
    l.addView(detail);
    l.addView(Ui.line(this, "Cancel, keep everything", this::finish));
    setContentView(l);
    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
      prepare();
    else requestPermissions(new String[] {Manifest.permission.CAMERA}, 20);
  }

  private TextView step(LinearLayout into, String s) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(11);
    t.setTypeface(Typeface.MONOSPACE);
    t.setTextColor(Ui.MUTED);
    t.setGravity(android.view.Gravity.CENTER);
    t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
    into.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    return t;
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    if (r == 20 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) prepare();
    else fail("Camera permission is needed. Nothing was deleted.");
  }

  private void prepare() {
    worker.execute(
        () -> {
          try {
            store = new VaultStore(this);
            face = store.getFolder(folderId).face;
            if (face == null) throw new IllegalStateException("No face key");
            engine = new FaceEngine(this);
            runOnUiThread(this::camera);
          } catch (Exception e) {
            runOnUiThread(() -> fail("Could not prepare the face check. Nothing was deleted."));
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
            fail("Front camera unavailable. Nothing was deleted.");
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
      float similarity = FaceEngine.similarity(s.vector, face);
      boolean complete =
          challenge.accept(
              SystemClock.elapsedRealtime(), s.count, similarity, s.yaw, s.roll, s.left, s.right);
      String hint =
          s.count != 1
              ? "Keep one clear, large face in the frame."
              : similarity < Challenge.MIN_SIMILARITY
                  ? "Face did not match. Nothing will be deleted."
                  : "Face matched. Hold a natural look.";
      String next = challenge.prompt();
      float fill = challenge.progress();
      int stepNow = challenge.step();
      runOnUiThread(
          () -> {
            if (!ended) {
              prompt.setText(next);
              detail.setText(
                  hint + "\nStops still photos & wrong faces. A live video could pass.");
              stepMatch.setTextColor(stepNow >= 1 ? Ui.TEAL : Ui.CYAN);
              stepMatch.setText(stepNow >= 1 ? "✓ · FACE MATCH" : "1 · FACE MATCH");
              stepBlink.setTextColor(stepNow >= 2 ? Ui.TEAL : stepNow == 1 ? Ui.CYAN : Ui.MUTED);
              FrameLayout.LayoutParams fp =
                  (FrameLayout.LayoutParams) barFill.getLayoutParams();
              fp.width = Math.max(1, (int) (barWidth() * fill));
              barFill.setLayoutParams(fp);
            }
          });
      if (SystemClock.elapsedRealtime() - startedAt > Challenge.TTL_MS) {
        runOnUiThread(
            () ->
                fail("Check timed out. Nothing was deleted. Look naturally at the camera and"
                    + " try again."));
        return;
      }
      if (complete && !ended) {
        ended = true;
        deletionStarted = true;
        if ("folder".equals(mode)) store.deleteFolder(folderId, challenge, SystemClock.elapsedRealtime());
        else store.deleteItem(folderId, id, challenge, SystemClock.elapsedRealtime());
        runOnUiThread(
            () -> {
              if (provider != null) provider.unbindAll();
              setResult(
                  RESULT_OK,
                  new android.content.Intent().putExtra("folderGone", "folder".equals(mode)));
              new android.app.AlertDialog.Builder(this)
                  .setTitle("folder".equals(mode) ? "Folder destroyed" : "Vault copy deleted")
                  .setMessage("The originals in your gallery were not changed.")
                  .setCancelable(false)
                  .setPositiveButton("Back", (d, w) -> finish())
                  .show();
            });
      }
    } catch (Exception e) {
      runOnUiThread(
          () ->
              fail(
                  deletionStarted
                      ? "Deletion hit a storage error. Return to the vault to check. Your gallery"
                            + " originals were not changed."
                      : "Face check could not finish. Nothing was deleted."));
    } finally {
      if (b != null) b.recycle();
      image.close();
    }
  }

  private int barWidth() {
    View parent = (View) barFill.getParent();
    return parent.getWidth() > 0 ? parent.getWidth() : 1;
  }

  private final Handler deadlineHandler = new Handler(Looper.getMainLooper());
  private final Runnable deadline =
      () -> {
        if (!ended && !isFinishing())
          fail("Check timed out. Nothing was deleted. Look naturally at the camera and try again.");
      };

  private void fail(String s) {
    if (isFinishing()) return;
    ended = true;
    if (provider != null) provider.unbindAll();
    new android.app.AlertDialog.Builder(this)
        .setTitle(deletionStarted ? "Check vault copy" : "Kept safe")
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
