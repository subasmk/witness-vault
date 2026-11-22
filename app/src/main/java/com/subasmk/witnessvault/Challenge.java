package com.subasmk.witnessvault;

/**
 * Fail-closed, short-lived per-item proof: match the enrolled face, then blink. NOT certified
 * presentation attack detection. Weaker than a multi-prompt sequence: it stops still photos and
 * wrong faces, but a video of the person blinking could pass.
 */
public final class Challenge {
  public static final float MIN_SIMILARITY = 0.78f;
  public static final long TTL_MS = 45_000;
  public static final long MIN_MS = 2_000;
  private final String item;
  private final long started;
  private int step = 0, stable = 0;
  private boolean closed = false, consumed = false;
  private long previous = -1;

  public Challenge(String item, long now) {
    this.item = item;
    started = now;
  }

  public String prompt() {
    return switch (step) {
      case 0 -> "Show your face";
      case 1 -> "Keep looking at the camera";
      default -> "Check complete";
    };
  }

  public int step() {
    return step;
  }

  public float progress() {
    return Math.min(1f, (step * 3 + Math.min(stable, 3)) / 6f);
  }

  public void reset() {
    step = 0;
    stable = 0;
    closed = false;
  }

  public boolean accept(
      long now, int count, float similarity, float yaw, float roll, float leftEye, float rightEye) {
    if (consumed || now - started > TTL_MS || now < started || now <= previous) return false;
    previous = now;
    if (count != 1
        || !Float.isFinite(similarity)
        || similarity < MIN_SIMILARITY
        || !Float.isFinite(yaw)
        || !Float.isFinite(roll)
        || Math.abs(roll) > 18) {
      reset();
      return false;
    }
    boolean front = Math.abs(yaw) < 11;
    boolean open = leftEye > 0.7f && rightEye > 0.7f;
    boolean match;
    if (step == 0) {
      match = front && open;
    } else if (step == 1) {
      if (!front) {
        closed = false;
        match = false;
      } else {
        if (leftEye >= 0 && rightEye >= 0 && leftEye < 0.2f && rightEye < 0.2f) closed = true;
        match = closed && open;
      }
    } else {
      match = false;
    }
    if (match) {
      if (++stable >= 3) {
        step++;
        stable = 0;
        closed = false;
      }
    } else stable = 0;
    return step == 2 && now - started >= MIN_MS;
  }

  public boolean consume(String id, long now) {
    if (consumed
        || step != 2
        || !item.equals(id)
        || now - started > TTL_MS
        || now - started < MIN_MS) return false;
    consumed = true;
    return true;
  }
}
