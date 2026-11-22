package com.subasmk.witnessvault;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class Ui {
  static final int BG = Color.rgb(17, 24, 23),
      CARD = Color.rgb(29, 39, 35),
      INK = Color.rgb(242, 246, 238),
      MUTED = Color.rgb(167, 181, 170),
      GREEN = Color.rgb(183, 242, 121);

  static int dp(Activity a, float v) {
    return (int) (v * a.getResources().getDisplayMetrics().density + .5f);
  }

  static LinearLayout column(Activity a) {
    LinearLayout l = new LinearLayout(a);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setPadding(dp(a, 24), dp(a, 24), dp(a, 24), dp(a, 24));
    l.setBackgroundColor(BG);
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

  static void title(Activity a, LinearLayout l, String s) {
    TextView t = text(a, s, 32, INK);
    t.setTypeface(null, Typeface.BOLD);
    l.addView(t);
  }

  static Button button(Activity a, String s, Runnable r) {
    Button b = new Button(a);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextColor(BG);
    b.setTextSize(16);
    GradientDrawable d = new GradientDrawable();
    d.setColor(GREEN);
    d.setCornerRadius(dp(a, 14));
    b.setBackground(d);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(a, 54));
    p.setMargins(0, dp(a, 12), 0, dp(a, 6));
    b.setLayoutParams(p);
    b.setOnClickListener(v -> r.run());
    return b;
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
