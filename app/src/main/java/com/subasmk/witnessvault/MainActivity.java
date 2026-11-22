package com.subasmk.witnessvault;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.widget.*;
import androidx.fragment.app.FragmentActivity;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends FragmentActivity {
  private static final int AUTH = 10, PHOTO = 11, MEDIA = 12, FACE = 13;
  private LinearLayout body;
  private VaultStore store;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private boolean unlocked = false, busy = false;
  private float[] enrolled;
  private TextView progress;
  private int deferredRequest = 0;
  private Intent deferredData;

  @Override
  public void onCreate(Bundle s) {
    super.onCreate(s);
    Ui.secure(this);
    VaultStore.clearPreviews(this);
    try {
      store = new VaultStore(this);
    } catch (Exception e) {
      error("Vault storage is unavailable. Nothing was changed.");
      return;
    }
    locked();
  }

  private void page() {
    ScrollView scroll = new ScrollView(this);
    body = Ui.column(this);
    scroll.setFillViewport(true);
    scroll.addView(body);
    setContentView(scroll);
    body.addView(Ui.text(this, "W I T N E S S   /   V A U L T", 12, Ui.GREEN));
  }

  private void locked() {
    unlocked = false;
    page();
    Ui.title(this, body, "Your memories.\nA familiar face.");
    body.addView(
        Ui.text(
            this,
            "An offline photo & video vault. Each item has its own face lock for deletion.",
            17,
            Ui.MUTED));
    body.addView(Ui.button(this, "Unlock with phone lock", this::authenticate));
    body.addView(
        Ui.text(
            this,
            "EXPERIMENTAL · v1.0\n\n"
                + "Delete checks use face matching + random head-turn and blink prompts. They are"
                + " not certified liveness detection. Replays, lookalikes or deepfakes may fool the"
                + " check.\n\n"
                + "Uninstall, clear app data, reset or a rooted phone can still erase the vault. No"
                + " recovery or cloud backup. Keep your originals.\n\n"
                + "Use only with the person's consent. No face or media is uploaded.",
            14,
            Ui.MUTED));
  }

  private void authenticate() {
    KeyguardManager k = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    if (!k.isDeviceSecure()) {
      new AlertDialog.Builder(this)
          .setTitle("Set a phone screen lock first")
          .setMessage(
              "Your phone PIN, pattern or password protects vault access. It does not replace the"
                  + " face check for deletion.")
          .setPositiveButton(
              "Security settings",
              (d, w) ->
                  startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)))
          .setNegativeButton("Cancel", null)
          .show();
      return;
    }
    Intent i =
        k.createConfirmDeviceCredentialIntent(
            "Open Witness Vault", "Use your phone lock. Delete still needs the enrolled face.");
    if (i != null) startActivityForResult(i, AUTH);
  }

  private void refresh() {
    if (!unlocked) {
      locked();
      return;
    }
    page();
    Ui.title(this, body, "Your vault");
    body.addView(Ui.text(this, "Local only · no account · no cloud", 14, Ui.MUTED));
    body.addView(Ui.button(this, "+ Add a photo or video", this::begin));
    body.addView(Ui.button(this, "Lock vault", this::locked));
    progress = Ui.text(this, "Loading...", 14, Ui.MUTED);
    body.addView(progress);
    worker.execute(
        () -> {
          try {
            List<VaultStore.Item> items = store.list();
            runOnUiThread(
                () -> {
                  if (!unlocked) return;
                  progress.setText(
                      items.isEmpty()
                          ? "Nothing here yet. Start with a disposable test file."
                          : items.size() + " protected item" + (items.size() == 1 ? "" : "s"));
                  for (VaultStore.Item item : items) addRow(item);
                  body.addView(
                      Ui.text(
                          this,
                          "Original gallery files are never removed. Vault deletion affects only"
                              + " this app's copy.\n\n"
                              + "No backup. Clearing data or uninstalling destroys stored items."
                              + " Face matching can make errors.",
                          13,
                          Ui.MUTED));
                });
          } catch (Exception e) {
            runOnUiThread(
                () ->
                    error(
                        "Could not read vault. An encrypted item may be damaged. No files were"
                            + " removed."));
          }
        });
  }

  private void addRow(VaultStore.Item i) {
    LinearLayout card = Ui.column(this);
    android.graphics.drawable.GradientDrawable bg =
        new android.graphics.drawable.GradientDrawable();
    bg.setColor(Ui.CARD);
    bg.setCornerRadius(Ui.dp(this, 16));
    card.setBackground(bg);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, Ui.dp(this, 18), 0, 0);
    card.setLayoutParams(p);
    card.addView(
        Ui.text(
            this,
            i.mime.startsWith("video/") ? "VIDEO  /  FACE LOCK" : "PHOTO  /  FACE LOCK",
            11,
            Ui.GREEN));
    card.addView(Ui.text(this, i.name, 19, Ui.INK));
    card.addView(
        Ui.text(
            this,
            String.format(Locale.US, "%.1f MB · unique item lock", i.size / 1048576.0),
            12,
            Ui.MUTED));
    card.addView(
        Ui.button(
            this,
            "View",
            () -> {
              Intent v = new Intent(this, ViewerActivity.class);
              v.putExtra("id", i.id);
              startActivity(v);
            }));
    card.addView(
        Ui.button(
            this,
            "Delete with live face",
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("Delete this vault copy?")
                    .setMessage(
                        "The enrolled person must complete a fresh camera check. Original gallery"
                            + " media is not affected. This cannot be undone.")
                    .setPositiveButton(
                        "Check face",
                        (d, w) -> {
                          Intent f = new Intent(this, FaceActivity.class);
                          f.putExtra("id", i.id);
                          startActivityForResult(f, FACE);
                        })
                    .setNegativeButton("Keep item", null)
                    .show()));
    body.addView(card);
  }

  private void begin() {
    if (busy || !unlocked) return;
    new AlertDialog.Builder(this)
        .setTitle("1 / 2 · Choose the face lock")
        .setMessage(
            "Choose one clear, front-facing photo of the person who may delete this item. Only one"
                + " face, good light, both eyes open. Get their permission. The photo is converted"
                + " to an encrypted face template, not kept.\n\n"
                + "Wrong enrollment can lock you out. Test on a disposable file first. There is no"
                + " PIN delete bypass.")
        .setPositiveButton("Choose face photo", (d, w) -> pick("image/*", PHOTO))
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void pick(String type, int code) {
    Intent i =
        new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type);
    if (code == MEDIA) i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*"});
    startActivityForResult(i, code);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == AUTH) {
      if (result == RESULT_OK) {
        unlocked = true;
        refresh();
        if (deferredData != null) {
          Intent saved = deferredData;
          int code = deferredRequest;
          deferredData = null;
          onActivityResult(code, RESULT_OK, saved);
        }
      } else {
        deferredData = null;
        enrolled = null;
        locked();
      }
      return;
    }
    if (request == FACE) {
      if (unlocked) refresh();
      return;
    }
    if (result != RESULT_OK || data == null || data.getData() == null) {
      enrolled = null;
      return;
    }
    if (!unlocked) {
      deferredData = data;
      deferredRequest = request;
      authenticate();
      return;
    }
    Uri uri = data.getData();
    if (request == PHOTO) {
      busy = true;
      page();
      Ui.title(this, body, "Checking face photo");
      body.addView(Ui.text(this, "One clear face, processed only on your phone.", 16, Ui.MUTED));
      worker.execute(
          () -> {
            try (FaceEngine engine = new FaceEngine(this)) {
              Bitmap b = FaceEngine.load(this, uri);
              FaceEngine.Sample s = engine.inspect(b);
              b.recycle();
              if (s.count != 1
                  || Math.abs(s.yaw) > 12
                  || Math.abs(s.roll) > 12
                  || s.left < .65
                  || s.right < .65)
                throw new Exception("Use one clear front-facing photo, with both eyes open.");
              enrolled = s.vector;
              runOnUiThread(
                  () -> {
                    busy = false;
                    refresh();
                    new AlertDialog.Builder(this)
                        .setTitle("2 / 2 · Choose your media")
                        .setMessage(
                            "Face lock ready. Choose one photo or video to copy into the vault. The"
                                + " original stays in your gallery.")
                        .setPositiveButton("Choose media", (d, w) -> pick("*/*", MEDIA))
                        .setNegativeButton("Cancel", (d, w) -> enrolled = null)
                        .setOnCancelListener(d -> enrolled = null)
                        .show();
                  });
            } catch (Exception e) {
              runOnUiThread(
                  () -> {
                    busy = false;
                    enrolled = null;
                    refresh();
                    error(
                        e.getMessage() == null
                            ? "Could not check the face photo."
                            : e.getMessage());
                  });
            }
          });
    } else if (request == MEDIA) {
      if (enrolled == null) {
        error("Choose the face photo again.");
        return;
      }
      float[] lock = enrolled;
      enrolled = null;
      busy = true;
      page();
      Ui.title(this, body, "Encrypting your copy");
      body.addView(
          Ui.text(
              this,
              "Keep the app open until this finishes. Your original is not changed.",
              16,
              Ui.MUTED));
      worker.execute(
          () -> {
            try {
              String name = "Media";
              try (Cursor c =
                  getContentResolver()
                      .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) name = c.getString(0);
              }
              store.add(uri, name, getContentResolver().getType(uri), lock);
              runOnUiThread(
                  () -> {
                    busy = false;
                    refresh();
                    new AlertDialog.Builder(this)
                        .setTitle("Vault copy saved")
                        .setMessage(
                            "Your original is still in the gallery. Test face deletion using a"
                                + " disposable copy before trusting the vault.")
                        .setPositiveButton("OK", null)
                        .show();
                  });
            } catch (Exception e) {
              runOnUiThread(
                  () -> {
                    busy = false;
                    refresh();
                    error(
                        "Import did not finish. Original unchanged. Check storage and try a smaller"
                            + " file.");
                  });
            } finally {
              Arrays.fill(lock, 0);
            }
          });
    }
  }

  private void error(String s) {
    new AlertDialog.Builder(this)
        .setTitle("Witness Vault")
        .setMessage(s)
        .setPositiveButton("OK", null)
        .show();
  }

  @Override
  protected void onStop() {
    super.onStop();
    unlocked = false;
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (!unlocked && !busy) locked();
  }

  @Override
  protected void onDestroy() {
    enrolled = null;
    worker.shutdown();
    super.onDestroy();
  }
}
