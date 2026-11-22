package com.subasmk.witnessvault;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Ghost theme: matte charcoal, glass cards, cyan HUD accents. */
final class Ui {
  static final int BG = Color.rgb(10, 14, 18),
      CARD = Color.rgb(17, 25, 31),
      INK = Color.rgb(234, 246, 251),
      MUTED = Color.rgb(107, 125, 136),
      CYAN = Color.rgb(34, 211, 238),
      TEAL = Color.rgb(94, 234, 212),
      RED = Color.rgb(248, 113, 113);

  static int dp(Activity a, float v) {
    return (int) (v * a.getResources().getDisplayMetrics().density + .5f);
  }

  static GradientDrawable pageBackground(Activity a) {
    GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
        new int[] {Color.rgb(13, 22, 28), BG});
    return d;
  }

  static LinearLayout column(Activity a) {
    LinearLayout l = new LinearLayout(a);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setPadding(dp(a, 24), dp(a, 24), dp(a, 24), dp(a, 24));
    l.setBackground(pageBackground(a));
    return l;
  }

  static TextView text(Activity a, String s, int size, int color) {
    TextView t = new TextView(a);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(a, 6), 0, dp(a, 10));
    t.setLineSpacing(dp(a, 3), 1);
    return t;
  }

  static TextView label(Activity a, String s) {
    TextView t = text(a, s, 11, CYAN);
    t.setTypeface(Typeface.MONOSPACE);
    t.setLetterSpacing(0.18f);
    return t;
  }

  static void title(Activity a, LinearLayout l, String s) {
    TextView t = text(a, s, 30, INK);
    t.setTypeface(null, Typeface.BOLD);
    t.setPadding(0, dp(a, 2), 0, dp(a, 8));
    l.addView(t);
  }

  static GradientDrawable cardBackground(Activity a) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(CARD);
    d.setCornerRadius(dp(a, 18));
    d.setStroke(dp(a, 1), Color.argb(70, 103, 232, 249));
    return d;
  }

  static LinearLayout card(Activity a) {
    LinearLayout c = new LinearLayout(a);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 8));
    c.setBackground(cardBackground(a));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(a, 14), 0, 0);
    c.setLayoutParams(p);
    return c;
  }

  private static Button base(Activity a, String s, Runnable r) {
    Button b = new Button(a);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextSize(15);
    b.setMinHeight(0);
    b.setMinimumHeight(0);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(a, 50));
    p.setMargins(0, dp(a, 10), 0, dp(a, 4));
    b.setLayoutParams(p);
    b.setOnClickListener(v -> r.run());
    return b;
  }

  static Button primary(Activity a, String s, Runnable r) {
    Button b = base(a, s, r);
    b.setTextColor(Color.rgb(4, 32, 42));
    b.setTypeface(null, Typeface.BOLD);
    GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
        new int[] {CYAN, TEAL});
    d.setCornerRadius(dp(a, 14));
    b.setBackground(d);
    return b;
  }

  static Button line(Activity a, String s, Runnable r) {
    Button b = base(a, s, r);
    b.setTextColor(CYAN);
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.TRANSPARENT);
    d.setCornerRadius(dp(a, 14));
    d.setStroke(dp(a, 1), Color.argb(110, 34, 211, 238));
    b.setBackground(d);
    return b;
  }

  static Button danger(Activity a, String s, Runnable r) {
    Button b = base(a, s, r);
    b.setTextColor(RED);
    b.setTypeface(Typeface.MONOSPACE);
    b.setTextSize(12);
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.argb(26, 248, 113, 113));
    d.setCornerRadius(dp(a, 10));
    d.setStroke(dp(a, 1), Color.argb(120, 248, 113, 113));
    b.setBackground(d);
    return b;
  }

  static TextView chip(Activity a, String s) {
    TextView t = new TextView(a);
    t.setText(s);
    t.setTextSize(10);
    t.setTypeface(Typeface.MONOSPACE);
    t.setTextColor(CYAN);
    t.setLetterSpacing(0.08f);
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.argb(18, 34, 211, 238));
    d.setCornerRadius(dp(a, 99));
    d.setStroke(dp(a, 1), Color.argb(100, 34, 211, 238));
    t.setBackground(d);
    int h = dp(a, 12), v = dp(a, 6);
    t.setPadding(h, v, h, v);
    return t;
  }

  static TextView warnStrip(Activity a, String s) {
    TextView t = text(a, s, 11, MUTED);
    t.setTypeface(Typeface.MONOSPACE);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.argb(200, 10, 14, 18));
    d.setCornerRadius(dp(a, 10));
    d.setStroke(dp(a, 1), Color.argb(40, 255, 255, 255));
    t.setBackground(d);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(a, 16), 0, 0);
    t.setLayoutParams(p);
    return t;
  }

  static void secure(Activity a) {
    a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    a.getWindow().setStatusBarColor(BG);
    a.getWindow().setNavigationBarColor(BG);
    a.getWindow()
        .getDecorView()
        .setOnApplyWindowInsetsListener(
            (v, i) -> {
              v.setPadding(
                  i.getSystemWindowInsetLeft(),
                  i.getSystemWindowInsetTop(),
                  i.getSystemWindowInsetRight(),
                  i.getSystemWindowInsetBottom());
              return i.consumeSystemWindowInsets();
            });
  }
}
