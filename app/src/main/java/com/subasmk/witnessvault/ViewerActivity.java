package com.subasmk.witnessvault;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import androidx.fragment.app.FragmentActivity;
import java.io.File;
import java.util.concurrent.*;

public class ViewerActivity extends FragmentActivity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private File current;
  private VideoView video;
  private boolean authenticating = false;

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    Ui.secure(this);
    LinearLayout l = Ui.column(this);
    l.addView(Ui.label(this, "PRIVATE VIEWER"));
    Ui.title(this, l, "Open this copy");
    l.addView(Ui.text(this, "Confirm your phone lock to view this item.", 15, Ui.MUTED));
    l.addView(Ui.primary(this, "Continue", this::auth));
    l.addView(Ui.line(this, "Back", this::finish));
    setContentView(l);
  }

  private void auth() {
    KeyguardManager k = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    Intent i = k.createConfirmDeviceCredentialIntent("View vault item", "Unlock with your phone lock");
    if (i == null) {
      finish();
      return;
    }
    authenticating = true;
    startActivityForResult(i, 30);
  }

  @Override
  protected void onActivityResult(int r, int result, Intent data) {
    super.onActivityResult(r, result, data);
    authenticating = false;
    if (result != RESULT_OK) {
      finish();
      return;
    }
    load();
  }

  private void load() {
    LinearLayout l = Ui.column(this);
    l.addView(Ui.label(this, "DECRYPTING"));
    Ui.title(this, l, "Opening your copy");
    l.addView(Ui.text(this, "Decrypting locally. Please keep the app open.", 15, Ui.MUTED));
    setContentView(l);
    worker.execute(
        () -> {
          try {
            VaultStore s = new VaultStore(this);
            String folder = getIntent().getStringExtra("folder");
            String id = getIntent().getStringExtra("id");
            VaultStore.Item item = s.getItem(folder, id);
            current = s.preview(folder, item.id);
            runOnUiThread(() -> show(item));
          } catch (Exception e) {
            runOnUiThread(
                () ->
                    new AlertDialog.Builder(this)
                        .setTitle("Could not open item")
                        .setMessage(
                            "This encrypted copy could not be read. It has not been deleted.")
                        .setPositiveButton("Back", (d, w) -> finish())
                        .setOnCancelListener(d -> finish())
                        .show());
          }
        });
  }

  private void show(VaultStore.Item item) {
    if (isFinishing()) {
      clear();
      return;
    }
    LinearLayout l = Ui.column(this);
    l.addView(Ui.text(this, item.name, 18, Ui.INK));
    if (item.mime.startsWith("video/")) {
      video = new VideoView(this);
      l.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
      video.setVideoPath(current.getPath());
      MediaController controls = new MediaController(this);
      controls.setAnchorView(video);
      video.setMediaController(controls);
      video.setOnErrorListener(
          (m, w, e) -> {
            new AlertDialog.Builder(this)
                .setMessage("This phone cannot play this video's codec. The vault copy is unchanged.")
                .setPositiveButton("Back", (d, n) -> finish())
                .show();
            return true;
          });
      video.start();
    } else {
      Bitmap b;
      try {
        b = FaceEngine.load(this, android.net.Uri.fromFile(current));
      } catch (Exception e) {
        new AlertDialog.Builder(this)
            .setMessage("Could not display this photo. The vault copy is unchanged.")
            .setPositiveButton("Back", (d, w) -> finish())
            .show();
        return;
      }
      ImageView image = new ImageView(this);
      image.setScaleType(ImageView.ScaleType.FIT_CENTER);
      image.setImageBitmap(b);
      l.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    l.addView(Ui.line(this, "Close viewer", this::finish));
    setContentView(l);
  }

  private void clear() {
    if (video != null) video.stopPlayback();
    if (current != null) current.delete();
    VaultStore.clearPreviews(this);
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (!authenticating) {
      clear();
      finish();
    }
  }

  @Override
  protected void onDestroy() {
    worker.shutdown();
    clear();
    super.onDestroy();
  }
}
