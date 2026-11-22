package com.subasmk.witnessvault;

import java.security.SecureRandom;

/**
 * A fail-closed, short-lived per-item gesture sequence. NOT certified presentation attack
 * detection.
 */
public final class Challenge {
  public static final float MIN_SIMILARITY = 0.78f;
  public static final long TTL_MS = 45_000;
  private final String item;
  private final long started;
  private final boolean leftFirst;
  private int step = 0, stable = 0;
  private boolean closed = false, consumed = false;
  private long previous = -1;

  public Challenge(String item, long now) {
    this(item, now, new SecureRandom().nextBoolean());
  }

  Challenge(String item, long now, boolean leftFirst) {
    this.item = item;
    started = now;
    this.leftFirst = leftFirst;
  }

  public String prompt() {
    return switch (step) {
      case 0 -> "Look straight at the camera";
      case 1 -> leftFirst ? "Turn slowly to one side (←)" : "Turn slowly to the other side (→)";
      case 2 -> "Return to the centre";
      case 3 -> leftFirst ? "Turn to the other side (→)" : "Turn to one side (←)";
      case 4 -> "Return to the centre";
      case 5 -> "Blink once, then open both eyes";
      case 6 -> "Look straight. Final face check";
      default -> "Check complete";
    };
  }

  public int step() {
    return step;
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
    boolean match =
        switch (step) {
          case 0, 2, 4, 6 -> front && open;
          case 1 -> leftFirst ? yaw < -18 && yaw > -35 : yaw > 18 && yaw < 35;
          case 3 -> leftFirst ? yaw > 18 && yaw < 35 : yaw < -18 && yaw > -35;
          case 5 -> false;
          default -> false;
        };
    if (step == 5) {
      if (!front) {
        closed = false;
        return false;
      }
      if (leftEye >= 0 && rightEye >= 0 && leftEye < 0.2f && rightEye < 0.2f) closed = true;
      match = closed && open;
    }
    if (match) {
      if (++stable >= 3) {
        step++;
        stable = 0;
        closed = false;
      }
    } else stable = 0;
    return step == 7 && now - started >= 4_000;
  }

  public boolean consume(String id, long now) {
    if (consumed
        || step != 7
        || !item.equals(id)
        || now - started > TTL_MS
        || now - started < 4_000) return false;
    consumed = true;
    return true;
  }
}
