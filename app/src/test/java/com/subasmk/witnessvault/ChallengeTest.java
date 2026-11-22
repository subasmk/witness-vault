package com.subasmk.witnessvault;

import static org.junit.Assert.*;

import org.junit.Test;

public class ChallengeTest {
  long t;

  boolean feed(Challenge c, float similarity, float yaw, float eye) {
    t += 250;
    return c.accept(t, 1, similarity, yaw, 0, eye, eye);
  }

  void repeat(Challenge c, float yaw, float eye) {
    for (int i = 0; i < 3; i++) feed(c, .95f, yaw, eye);
  }

  Challenge complete() {
    t = 0;
    Challenge c = new Challenge("item-A", 0, true);
    repeat(c, 0, .95f);
    repeat(c, -23, .95f);
    repeat(c, 0, .95f);
    repeat(c, 23, .95f);
    repeat(c, 0, .95f);
    feed(c, .95f, 0, .1f);
    repeat(c, 0, .95f);
    repeat(c, 0, .95f);
    assertEquals(7, c.step());
    return c;
  }

  @Test
  public void acceptsCorrectFreshSequence() {
    Challenge c = complete();
    assertTrue(c.consume("item-A", t));
  }

  @Test
  public void rejectsWrongFace() {
    Challenge c = new Challenge("a", 0, true);
    for (int i = 0; i < 100; i++) feed(c, .65f, 0, .95f);
    assertFalse(c.consume("a", t));
    assertEquals(0, c.step());
  }

  @Test
  public void staticPhotoCannotProgress() {
    Challenge c = new Challenge("a", 0, true);
    for (int i = 0; i < 70; i++) feed(c, .99f, 0, .95f);
    assertEquals(1, c.step());
    assertFalse(c.consume("a", t));
  }

  @Test
  public void noBlinkCannotComplete() {
    t = 0;
    Challenge c = new Challenge("a", 0, true);
    repeat(c, 0, .9f);
    repeat(c, -24, .9f);
    repeat(c, 0, .9f);
    repeat(c, 24, .9f);
    repeat(c, 0, .9f);
    for (int i = 0; i < 20; i++) feed(c, .99f, 0, .9f);
    assertEquals(5, c.step());
    assertFalse(c.consume("a", t));
  }

  @Test
  public void proofCannotCrossItems() {
    Challenge c = complete();
    assertFalse(c.consume("item-B", t));
    assertTrue(c.consume("item-A", t));
  }

  @Test
  public void proofCannotBeReused() {
    Challenge c = complete();
    assertTrue(c.consume("item-A", t));
    assertFalse(c.consume("item-A", t));
  }

  @Test
  public void timeoutFailsClosed() {
    Challenge c = complete();
    assertFalse(c.consume("item-A", 46000));
  }

  @Test
  public void differentFaceResetsSequence() {
    t = 0;
    Challenge c = new Challenge("a", 0, true);
    repeat(c, 0, .9f);
    repeat(c, -24, .9f);
    feed(c, .1f, 0, .9f);
    assertEquals(0, c.step());
  }

  @Test
  public void missingFaceResets() {
    t = 0;
    Challenge c = new Challenge("a", 0, true);
    repeat(c, 0, .9f);
    c.accept(t + 250, 0, .99f, 0, 0, 1, 1);
    assertEquals(0, c.step());
  }

  @Test
  public void twoFacesReset() {
    t = 0;
    Challenge c = new Challenge("a", 0, true);
    repeat(c, 0, .9f);
    c.accept(t + 250, 2, .99f, 0, 0, 1, 1);
    assertEquals(0, c.step());
  }

  @Test
  public void nanFailsClosed() {
    t = 0;
    Challenge c = new Challenge("a", 0, true);
    for (int i = 0; i < 10; i++) feed(c, Float.NaN, 0, 1);
    assertEquals(0, c.step());
  }

  @Test
  public void duplicateFrameTimestampDoesNotProgress() {
    Challenge c = new Challenge("a", 0, true);
    for (int i = 0; i < 20; i++) c.accept(100, 1, .99f, 0, 0, 1, 1);
    assertEquals(0, c.step());
  }

  @Test
  public void rollDoesNotSubstituteYaw() {
    t = 0;
    Challenge c = new Challenge("a", 0, true);
    for (int i = 0; i < 6; i++) c.accept(t += 250, 1, .99f, 0, 30, 1, 1);
    assertEquals(0, c.step());
  }

  @Test
  public void reverseDirectionSupported() {
    t = 0;
    Challenge c = new Challenge("a", 0, false);
    repeat(c, 0, .9f);
    repeat(c, 23, .9f);
    assertEquals(2, c.step());
  }

  @Test
  public void cancellationHasNoProof() {
    Challenge c = new Challenge("a", 0, true);
    assertFalse(c.consume("a", 5000));
  }
}
