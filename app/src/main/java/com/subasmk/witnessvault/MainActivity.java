package com.subasmk.witnessvault;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.View;
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
  private String currentFolder;
  private String pendingName;
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
    scroll.setFillViewport(true);
    body = Ui.column(this);
    scroll.addView(body);
    setContentView(scroll);
  }

  private void ring(LinearLayout into) {
    FrameLayout wrap = new FrameLayout(this);
    LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(Ui.dp(this, 150), Ui.dp(this, 150));
    wp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
    wp.setMargins(0, Ui.dp(this, 26), 0, Ui.dp(this, 10));
    wrap.setLayoutParams(wp);
    GradientDrawable circle = new GradientDrawable();
    circle.setShape(GradientDrawable.OVAL);
    circle.setColor(Ui.BG);
    circle.setStroke(Ui.dp(this, 2), Color.argb(170, 34, 211, 238));
    wrap.setBackground(circle);
    ImageView icon = new ImageView(this);
    icon.setImageResource(R.drawable.ic_vault);
    FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 62));
    ip.gravity = android.view.Gravity.CENTER;
    icon.setLayoutParams(ip);
    wrap.addView(icon);
    into.addView(wrap);
  }

  private void locked() {
    unlocked = false;
    currentFolder = null;
    page();
    ring(body);
    TextView name = Ui.text(this, "Witness Vault", 28, Ui.INK);
    name.setTypeface(null, Typeface.BOLD);
    name.setGravity(android.view.Gravity.CENTER);
    body.addView(name);
    TextView sub = Ui.label(this, "SIGNAL SEALED · AES-256-GCM");
    sub.setGravity(android.view.Gravity.CENTER);
    body.addView(sub);
    LinearLayout stats = Ui.card(this);
    stats.addView(Ui.text(this, "FACE LOCK     ARMED\nNETWORK       CUT\nSTORAGE       THIS PHONE ONLY", 13, Ui.MUTED));
    body.addView(stats);
    body.addView(Ui.primary(this, "Unlock", this::authenticate));
    body.addView(
        Ui.warnStrip(
            this,
            "EXPERIMENTAL v1.1\n"
                + "Delete checks use face matching plus a silent blink signal. They are not"
                + " certified liveness detection: replays, lookalikes or deepfakes may pass.\n"
                + "Uninstall or clearing data erases the vault. No backup. Keep your originals."));
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
              (d, w) -> startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)))
          .setNegativeButton("Cancel", null)
          .show();
      return;
    }
    Intent i =
        k.createConfirmDeviceCredentialIntent(
            "Open Witness Vault", "Use your phone lock. Delete still needs the enrolled face.");
    if (i != null) startActivityForResult(i, AUTH);
  }

  private void folders() {
    currentFolder = null;
    page();
    body.addView(Ui.label(this, "VAULT"));
    Ui.title(this, body, "Your vault");
    LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.addView(Ui.chip(this, "LOCAL ONLY"));
    TextView gap = new TextView(this);
    bar.addView(gap, new LinearLayout.LayoutParams(0, 1, 1));
    bar.addView(Ui.chip(this, "NO CLOUD"));
    body.addView(bar);
    body.addView(Ui.primary(this, "+ New folder", this::newFolder));
    progress = Ui.text(this, "Loading...", 13, Ui.MUTED);
    body.addView(progress);
    worker.execute(
        () -> {
          try {
            List<VaultStore.Folder> list = store.listFolders();
            runOnUiThread(
                () -> {
                  if (!unlocked || currentFolder != null) return;
                  if (list.isEmpty()) {
                    progress.setText("");
                    emptyBox();
                  } else {
                    progress.setText(list.size() + (list.size() == 1 ? " folder" : " folders"));
                    for (VaultStore.Folder f : list) addFolderRow(f);
                  }
                  body.addView(
                      Ui.warnStrip(
                          this,
                          "face check runs before any delete · gallery originals untouched ·"
                              + " uninstall erases vault"));
                  body.addView(Ui.line(MainActivity.this, "Lock vault", this::locked));
                });
          } catch (Exception e) {
            runOnUiThread(
                () ->
                    error("Could not read vault. An encrypted entry may be damaged. Nothing was"
                        + " removed."));
          }
        });
  }

  private void emptyBox() {
    TextView t = Ui.text(this, "Nothing secured yet\ncreate a folder and enroll one face", 15, Ui.MUTED);
    t.setGravity(android.view.Gravity.CENTER);
    t.setPadding(Ui.dp(this, 20), Ui.dp(this, 44), Ui.dp(this, 20), Ui.dp(this, 44));
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.argb(10, 34, 211, 238));
    d.setCornerRadius(Ui.dp(this, 20));
    d.setStroke(Ui.dp(this, 1.5f), Color.argb(120, 103, 232, 249), Ui.dp(this, 6), Ui.dp(this, 6));
    t.setBackground(d);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, Ui.dp(this, 16), 0, 0);
    t.setLayoutParams(p);
    body.addView(t);
  }

  void addFolderRow(VaultStore.Folder f) {
    LinearLayout card = Ui.card(this);
    TextView name = Ui.text(this, f.name, 20, Ui.INK);
    name.setTypeface(null, Typeface.BOLD);
    name.setPadding(0, 0, 0, 0);
    card.addView(name);
    card.addView(
        Ui.text(this, f.count + (f.count == 1 ? " item" : " items") + " · face-keyed", 12, Ui.MUTED));
    card.addView(Ui.primary(this, "Open", () -> openFolder(f.id)));
    body.addView(card);
  }

  private void newFolder() {
    if (busy || !unlocked) return;
    page();
    body.addView(Ui.label(this, "NEW FOLDER"));
    Ui.title(this, body, "Who may delete?");
    body.addView(
        Ui.text(
            this,
            "One face per folder. It is enrolled once, from one clear front photo, and converted"
                + " to an encrypted template. Adding media later needs no check. Deleting any item"
                + " needs that person's live face.",
            15,
            Ui.MUTED));
    final EditText input = new EditText(this);
    input.setHint("Folder name (e.g. Mine, Amma)");
    input.setHintTextColor(Ui.MUTED);
    input.setTextColor(Ui.INK);
    input.setTextSize(17);
    input.setSingleLine();
    input.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
    GradientDrawable field = new GradientDrawable();
    field.setColor(Ui.CARD);
    field.setCornerRadius(Ui.dp(this, 14));
    field.setStroke(Ui.dp(this, 1), Color.argb(70, 103, 232, 249));
    input.setBackground(field);
    LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, Ui.dp(this, 52));
    ip.setMargins(0, Ui.dp(this, 8), 0, 0);
    input.setLayoutParams(ip);
    body.addView(input);
    body.addView(
        Ui.primary(
            this,
            "Choose face photo",
            () -> {
              String name = input.getText().toString();
              pendingName = name.isEmpty() ? "Folder" : name;
              pick("image/*", PHOTO);
            }));
    body.addView(
        Ui.warnStrip(
            this,
            "one clear front photo · both eyes open · get their permission · the photo becomes an"
                + " encrypted template and is not kept"));
    body.addView(Ui.line(this, "Back", this::folders));
  }

  private void openFolder(String folderId) {
    currentFolder = folderId;
    page();
    body.addView(Ui.label(this, "FOLDER"));
    progress = Ui.text(this, "Loading...", 13, Ui.MUTED);
    worker.execute(
        () -> {
          try {
            VaultStore.Folder f = store.getFolder(folderId);
            List<VaultStore.Item> items = store.listItems(folderId);
            runOnUiThread(
                () -> {
                  if (!unlocked || !folderId.equals(currentFolder)) return;
                  Ui.title(MainActivity.this, body, f.name);
                  body.addView(Ui.chip(MainActivity.this, items.size() + " ITEMS · AES-256"));
                  body.addView(
                      Ui.primary(MainActivity.this, "+ Add media", () -> pick("*/*", MEDIA)));
                  LinearLayout row = new LinearLayout(MainActivity.this);
                  row.setOrientation(LinearLayout.HORIZONTAL);
                  LinearLayout rename = new LinearLayout(MainActivity.this);
                  rename.addView(Ui.line(MainActivity.this, "Rename", () -> renameDialog(f)));
                  LinearLayout delete = new LinearLayout(MainActivity.this);
                  delete.addView(
                      Ui.danger(MainActivity.this, "DELETE FOLDER", () -> confirmFolderDelete(f)));
                  row.addView(rename, new LinearLayout.LayoutParams(0, -2, 1));
                  TextView spacer = new TextView(MainActivity.this);
                  row.addView(spacer, new LinearLayout.LayoutParams(Ui.dp(MainActivity.this, 10), 1));
                  row.addView(delete, new LinearLayout.LayoutParams(0, -2, 1));
                  body.addView(row);
                  progress.setText(
                      items.isEmpty() ? "Empty. Add a photo or video, no check needed." : "");
                  for (VaultStore.Item i : items) addItemRow(f, i);
                  body.addView(
                      Ui.warnStrip(
                          MainActivity.this,
                          "deleting an item asks for " + f.name
                              + "'s live face · originals in gallery stay untouched"));
                  body.addView(Ui.line(MainActivity.this, "All folders", () -> folders()));
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  error("Could not open this folder. Nothing was removed.");
                  folders();
                });
          }
        });
  }

  void addItemRow(VaultStore.Folder f, VaultStore.Item i) {
    LinearLayout card = Ui.card(this);
    LinearLayout top = new LinearLayout(this);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(android.view.Gravity.CENTER_VERTICAL);
    View thumb = new View(this);
    GradientDrawable g =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            i.mime.startsWith("video/")
                ? new int[] {Color.rgb(14, 116, 144), Color.rgb(4, 33, 31)}
                : new int[] {Color.rgb(51, 65, 85), Color.rgb(2, 6, 23)});
    g.setCornerRadius(Ui.dp(this, 12));
    thumb.setBackground(g);
    top.addView(thumb, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
    LinearLayout texts = new LinearLayout(this);
    texts.setOrientation(LinearLayout.VERTICAL);
    texts.setPadding(Ui.dp(this, 12), 0, 0, 0);
    TextView name = Ui.text(this, i.name, 16, Ui.INK);
    name.setTypeface(null, Typeface.BOLD);
    name.setPadding(0, 0, 0, 0);
    texts.addView(name);
    texts.addView(
        Ui.text(
            this,
            String.format(
                Locale.US,
                "%.1f MB · %s",
                i.size / 1048576.0,
                i.mime.startsWith("video/") ? "VIDEO" : "PHOTO"),
            11,
            Ui.MUTED));
    top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
    card.addView(top);
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout view = new LinearLayout(this);
    view.addView(
        Ui.line(
            this,
            "View",
            () -> {
              Intent v = new Intent(this, ViewerActivity.class);
              v.putExtra("folder", f.id);
              v.putExtra("id", i.id);
              startActivity(v);
            }));
    LinearLayout del = new LinearLayout(this);
    del.addView(Ui.danger(this, "DELETE", () -> confirmItemDelete(f, i)));
    row.addView(view, new LinearLayout.LayoutParams(0, -2, 1));
    TextView spacer = new TextView(this);
    row.addView(spacer, new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
    row.addView(del, new LinearLayout.LayoutParams(0, -2, 1));
    card.addView(row);
    body.addView(card);
  }

  private void confirmItemDelete(VaultStore.Folder f, VaultStore.Item i) {
    new AlertDialog.Builder(this)
        .setTitle("Delete this vault copy?")
        .setMessage(
            "\"" + i.name + "\" will be destroyed after a fresh live face check for \""
                + f.name + "\". The gallery original is not affected. This cannot be undone.")
        .setPositiveButton(
            "Start face check",
            (d, w) -> {
              Intent intent = new Intent(this, FaceActivity.class);
              intent.putExtra("mode", "item");
              intent.putExtra("folder", f.id);
              intent.putExtra("id", i.id);
              startActivityForResult(intent, FACE);
            })
        .setNegativeButton("Keep item", null)
        .show();
  }

  private void confirmFolderDelete(VaultStore.Folder f) {
    new AlertDialog.Builder(this)
        .setTitle("Destroy the whole folder?")
        .setMessage(
            "Every item inside \"" + f.name + "\" (" + f.count
                + ") will be destroyed after a fresh live face check. Gallery originals are not"
                + " affected. This cannot be undone.")
        .setPositiveButton(
            "Start face check",
            (d, w) -> {
              Intent intent = new Intent(this, FaceActivity.class);
              intent.putExtra("mode", "folder");
              intent.putExtra("folder", f.id);
              intent.putExtra("id", f.id);
              startActivityForResult(intent, FACE);
            })
        .setNegativeButton("Keep folder", null)
        .show();
  }

  private void renameDialog(VaultStore.Folder f) {
    final EditText input = new EditText(this);
    input.setText(f.name);
    input.setSingleLine();
    new AlertDialog.Builder(this)
        .setTitle("Rename folder")
        .setView(input)
        .setPositiveButton(
            "Rename",
            (d, w) ->
                worker.execute(
                    () -> {
                      try {
                        store.renameFolder(f.id, input.getText().toString());
                        runOnUiThread(() -> openFolder(f.id));
                      } catch (Exception e) {
                        runOnUiThread(() -> error("Could not rename. Nothing was changed."));
                      }
                    }))
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
        folders();
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
      if (!unlocked) return;
      if (currentFolder != null) {
        String id = currentFolder;
        if (result == RESULT_OK && data != null && data.getBooleanExtra("folderGone", false)) {
          folders();
        } else openFolder(id);
      } else folders();
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
      body.addView(Ui.label(this, "REGISTER FACE KEY"));
      Ui.title(this, body, "Checking face photo");
      body.addView(Ui.text(this, "One clear face, processed only on this phone.", 15, Ui.MUTED));
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
                    createFolder();
                  });
            } catch (Exception e) {
              runOnUiThread(
                  () -> {
                    busy = false;
                    enrolled = null;
                    newFolder();
                    error(e.getMessage() == null ? "Could not check the face photo." : e.getMessage());
                  });
            }
          });
    } else if (request == MEDIA) {
      if (currentFolder == null) {
        error("Open the folder again, then add.");
        return;
      }
      busy = true;
      page();
      body.addView(Ui.label(this, "IMPORT"));
      Ui.title(this, body, "Encrypting your copy");
      body.addView(
          Ui.text(this, "Keep the app open until this finishes. Your original is not changed.", 15,
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
              store.addItem(currentFolder, uri, name, getContentResolver().getType(uri));
              runOnUiThread(
                  () -> {
                    busy = false;
                    openFolder(currentFolder);
                    new AlertDialog.Builder(this)
                        .setTitle("Vault copy saved")
                        .setMessage(
                            "Your original is still in the gallery. Test face deletion with a"
                                + " disposable copy before trusting the vault.")
                        .setPositiveButton("OK", null)
                        .show();
                  });
            } catch (Exception e) {
              runOnUiThread(
                  () -> {
                    busy = false;
                    openFolder(currentFolder);
                    error(
                        "Import did not finish. Original unchanged. Check storage and try a smaller"
                            + " file.");
                  });
            }
          });
    }
  }

  private void createFolder() {
    float[] face = enrolled;
    enrolled = null;
    if (face == null || pendingName == null) {
      folders();
      return;
    }
    busy = true;
    page();
    body.addView(Ui.label(this, "REGISTER FACE KEY"));
    Ui.title(this, body, "Sealing folder");
    body.addView(Ui.text(this, "Encrypting the face template on this phone.", 15, Ui.MUTED));
    worker.execute(
        () -> {
          try {
            VaultStore.Folder f = store.createFolder(pendingName, face);
            Arrays.fill(face, 0);
            runOnUiThread(
                () -> {
                  busy = false;
                  openFolder(f.id);
                  new AlertDialog.Builder(this)
                      .setTitle("Folder ready")
                      .setMessage(
                          "Face key saved for \"" + f.name
                              + "\". Add photos and videos freely; deleting any item asks for a"
                              + " live face.")
                      .setPositiveButton("OK", null)
                      .show();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  busy = false;
                  folders();
                  error("Could not create the folder. Nothing was saved.");
                });
          }
        });
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
