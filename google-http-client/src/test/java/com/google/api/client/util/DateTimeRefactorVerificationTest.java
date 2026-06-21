/*
 * Copyright (c) 2024 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.client.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.util.DateTime.SecondsAndNanos;
import java.util.Date;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Verification tests for the DateTime refactoring. Covers new behavior that differs from the
 * original DateTimeTest assertions. All original tests in DateTimeTest remain untouched.
 */
@RunWith(JUnit4.class)
public class DateTimeRefactorVerificationTest {

  // =========================================================================
  // Requirement 1: DateTime(long) tzShift always returns 0 across all default
  // JVM timezones, never depends on TimeZone.getDefault()
  // =========================================================================
  @Test
  public void requirement1_defaultTzShiftIsAlwaysZero() {
    long ts = 1700000000000L;

    TimeZone originalTz = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
      DateTime dtLA = new DateTime(ts);
      assertEquals("DateTime(long) tzShift must be 0 (not JVM default)", 0, dtLA.getTimeZoneShift());

      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
      DateTime dtTokyo = new DateTime(ts);
      assertEquals("DateTime(long) tzShift must be 0 across JVM defaults",
          0, dtTokyo.getTimeZoneShift());

      TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
      DateTime dtLondon = new DateTime(ts);
      assertEquals("DateTime(long) tzShift must be 0 across JVM defaults",
          0, dtLondon.getTimeZoneShift());

      // All three must be equal (same absolute instant)
      assertEquals(dtLA, dtTokyo);
      assertEquals(dtLA, dtLondon);
      assertEquals(dtLA.hashCode(), dtTokyo.hashCode());
    } finally {
      TimeZone.setDefault(originalTz);
    }
  }

  @Test
  public void requirement1_DateTimeDate_defaultTzShiftIsAlwaysZero() {
    Date date = new Date(1700000000000L);

    TimeZone originalTz = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("GMT-4"));
      DateTime dt1 = new DateTime(date);
      assertEquals(0, dt1.getTimeZoneShift());

      TimeZone.setDefault(TimeZone.getTimeZone("GMT+8"));
      DateTime dt2 = new DateTime(date);
      assertEquals(0, dt2.getTimeZoneShift());

      assertEquals(dt1, dt2);
    } finally {
      TimeZone.setDefault(originalTz);
    }
  }

  @Test
  public void requirement1_explicitTzShiftStillWorks() {
    // Explicit tzShift constructor still honors the specified offset
    DateTime dt = new DateTime(1700000000000L, 480); // +08:00
    assertEquals(480, dt.getTimeZoneShift());
    assertTrue(dt.toStringRfc3339().endsWith("+08:00"));
  }

  @Test
  public void requirement1_dateTimeDateZone_doesNotDependOnDefaultTz() {
    // Constructor with explicit zone must not depend on JVM default
    long ts = 1700000000000L;
    Date date = new Date(ts);
    TimeZone gmt8 = TimeZone.getTimeZone("GMT+8");

    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      DateTime dtUtcDefault = new DateTime(date, gmt8);

      TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
      DateTime dtNyDefault = new DateTime(date, gmt8);

      assertEquals("Explicit zone constructor tzShift should match",
          dtUtcDefault.getTimeZoneShift(), dtNyDefault.getTimeZoneShift());
      assertEquals("Explicit zone constructor should produce same result",
          dtUtcDefault.toStringRfc3339(), dtNyDefault.toStringRfc3339());
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  public void requirement1_dateOnlyConstructor_tzShiftAlwaysZero() {
    // Date-only always has tzShift = 0
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("GMT-8"));
      DateTime dt1 = new DateTime(true, 1234567890L, null);
      assertEquals(0, dt1.getTimeZoneShift());

      DateTime dt2 = new DateTime(true, 1234567890L, 480); // Explicit tz ignored for date-only
      assertEquals(0, dt2.getTimeZoneShift());
    } finally {
      TimeZone.setDefault(original);
    }
  }

  // =========================================================================
  // Requirement 2: toStringRfc3339Nano() new public method
  // - Millisecond precision: output .000
  // - Sub-millisecond precision: output up to 9 digits, no truncation of
  //   non-zero trailing digits
  // =========================================================================
  @Test
  public void requirement2_toStringRfc3339Nano_millisecondPrecision() {
    // Pure millisecond (3 digits) -> output 3 digits
    DateTime dt = DateTime.parseRfc3339("2024-01-01T12:00:00.123Z");
    assertEquals("2024-01-01T12:00:00.123Z", dt.toStringRfc3339Nano());
  }

  @Test
  public void requirement2_toStringRfc3339Nano_zeroMillisStillThreeDigits() {
    // Zero milliseconds -> still output .000
    DateTime dt = DateTime.parseRfc3339("2024-01-01T12:00:00Z");
    assertEquals("2024-01-01T12:00:00.000Z", dt.toStringRfc3339Nano());
  }

  @Test
  public void requirement2_toStringRfc3339Nano_twoDigitMillisPaddedToThree() {
    // 2 digits input -> still padded to 3
    DateTime dt = DateTime.parseRfc3339("1996-12-19T16:39:57.12-08:00");
    assertEquals("1996-12-19T16:39:57.120-08:00", dt.toStringRfc3339Nano());
  }

  @Test
  public void requirement2_toStringRfc3339Nano_fourDigitPrecision() {
    // 4 digits sub-millisecond -> output 4+ digits
    DateTime dt = DateTime.parseRfc3339("2024-01-01T12:00:00.1234Z");
    String output = dt.toStringRfc3339Nano();
    assertTrue("Should preserve 4-digit precision: " + output,
        output.startsWith("2024-01-01T12:00:00.1234"));
    assertTrue("Should have at least 4 fractional digits",
        output.split("\\.")[1].split("Z")[0].length() >= 4);
  }

  @Test
  public void requirement2_toStringRfc3339Nano_fullNanoPrecision() {
    // 9 digits nanosecond precision -> round-trip exactly
    String input = "2024-01-01T12:00:00.123456789Z";
    DateTime dt = DateTime.parseRfc3339(input);
    assertEquals(input, dt.toStringRfc3339Nano());
  }

  @Test
  public void requirement2_toStringRfc3339Nano_trailingZerosPreservedFromParse() {
    // If parsed with 6 digits and trailing zeros (000), output at least 6 digits
    DateTime dt = DateTime.parseRfc3339("2024-01-01T12:00:00.123000Z");
    String output = dt.toStringRfc3339Nano();
    assertTrue("Should preserve parsed precision: " + output,
        output.startsWith("2024-01-01T12:00:00.123000"));
  }

  @Test
  public void requirement2_toStringRfc3339Nano_fromConstructorDefaultsToThreeDigits() {
    // Constructed from millis (no parse info) -> default to 3 digits
    DateTime dt = new DateTime(1700000000123L, 0);
    assertEquals("2023-11-14T22:13:20.123Z", dt.toStringRfc3339Nano());
  }

  // =========================================================================
  // Requirement 2 (continued): toStringRfc3339() always outputs 3 digits
  // (truncates sub-millisecond precision, backwards compatible)
  // =========================================================================
  @Test
  public void requirement2_toStringRfc3339_truncatesSubMillis() {
    // Parse with 9 digits -> toStringRfc3339 outputs only 3 (truncated)
    DateTime dt = DateTime.parseRfc3339("2024-01-01T12:00:00.123456789Z");
    assertEquals("toStringRfc3339 must truncate to 3 digits for backwards compat",
        "2024-01-01T12:00:00.123Z", dt.toStringRfc3339());
  }

  @Test
  public void requirement2_toStringRfc3339_fourDigitsTruncated() {
    DateTime dt = DateTime.parseRfc3339("2024-01-01T12:00:00.1234Z");
    assertEquals("2024-01-01T12:00:00.123Z", dt.toStringRfc3339());
  }

  @Test
  public void requirement2_toStringRfc3339_twoDigitsPadded() {
    DateTime dt = DateTime.parseRfc3339("1996-12-19T16:39:57.12-08:00");
    assertEquals("1996-12-19T16:39:57.120-08:00", dt.toStringRfc3339());
  }

  @Test
  public void requirement2_toStringRfc3339_alwaysThreeDigitsEvenIfZero() {
    DateTime dt = DateTime.parseRfc3339("2024-01-01T12:00:00Z");
    assertEquals("2024-01-01T12:00:00.000Z", dt.toStringRfc3339());
  }

  // =========================================================================
  // Equals/hashCode: based on absolute UTC instant (including sub-millisecond),
  // NOT based on tzShift or formatting precision
  // =========================================================================
  @Test
  public void equals_sameInstantDifferentTzShift_shouldBeEqual() {
    DateTime dtZ = DateTime.parseRfc3339("2024-01-01T12:00:00Z");
    DateTime dtPlus8 = DateTime.parseRfc3339("2024-01-01T20:00:00+08:00");
    DateTime dtMinus5 = DateTime.parseRfc3339("2024-01-01T07:00:00-05:00");

    assertEquals(dtZ, dtPlus8);
    assertEquals(dtZ, dtMinus5);
    assertEquals(dtPlus8, dtMinus5);
    assertEquals(dtZ.hashCode(), dtPlus8.hashCode());
  }

  @Test
  public void equals_sameMillisDifferentNanos_shouldNotBeEqual() {
    // Same millisecond, different sub-millisecond nanos
    DateTime dt1 = DateTime.parseRfc3339("2024-01-01T12:00:00.123Z");
    DateTime dt2 = DateTime.parseRfc3339("2024-01-01T12:00:00.123000001Z");

    assertNotEquals(dt1, dt2);
    assertNotEquals(dt1.hashCode(), dt2.hashCode());
  }

  @Test
  public void equals_sameInstantDifferentFormatPrecision_shouldBeEqual() {
    // Same instant, different formatting precision (trailing zeros)
    DateTime dt3 = DateTime.parseRfc3339("2024-01-01T12:00:00.123Z");
    DateTime dt6 = DateTime.parseRfc3339("2024-01-01T12:00:00.123000Z");
    DateTime dt9 = DateTime.parseRfc3339("2024-01-01T12:00:00.123000000Z");

    assertEquals(dt3, dt6);
    assertEquals(dt3, dt9);
    assertEquals(dt3.hashCode(), dt9.hashCode());
  }

  @Test
  public void equals_differentInstants_shouldNotBeEqual() {
    DateTime dt1 = DateTime.parseRfc3339("2024-01-01T12:00:00.000000000Z");
    DateTime dt2 = DateTime.parseRfc3339("2024-01-01T12:00:00.000000001Z");
    assertNotEquals(dt1, dt2);

    DateTime dt3 = DateTime.parseRfc3339("2024-01-01T12:00:00.999Z");
    DateTime dt4 = DateTime.parseRfc3339("2024-01-01T12:00:00.999999999Z");
    assertNotEquals(dt3, dt4);
  }

  @Test
  public void equals_dateOnlyVsDateTime_shouldNotBeEqual() {
    // Same millisecond value but dateOnly flag differs
    DateTime dateOnly = new DateTime(true, 1700000000000L, 0);
    DateTime dateTime = new DateTime(false, 1700000000000L, 0);
    assertNotEquals(dateOnly, dateTime);
  }

  // =========================================================================
  // Date-only string rejects timezone offset
  // =========================================================================
  @Test
  public void dateOnlyWithZ_rejected() {
    try {
      DateTime.parseRfc3339("2024-01-01Z");
      fail("Expected NumberFormatException");
    } catch (NumberFormatException expected) {
    }
  }

  @Test
  public void dateOnlyWithOffset_rejected() {
    try {
      DateTime.parseRfc3339("2024-01-01+08:00");
      fail("Expected NumberFormatException");
    } catch (NumberFormatException expected) {
    }

    try {
      DateTime.parseRfc3339("2024-01-01-05:00");
      fail("Expected NumberFormatException");
    } catch (NumberFormatException expected) {
    }
  }

  @Test
  public void dateOnlyWithoutTz_accepted() {
    DateTime dt = DateTime.parseRfc3339("2024-01-01");
    assertTrue(dt.isDateOnly());
    assertEquals(0, dt.getTimeZoneShift());
    assertEquals("2024-01-01", dt.toStringRfc3339());
  }

  // =========================================================================
  // API Stability: all public constructors, methods preserved
  // =========================================================================
  @Test
  public void api_allPublicConstructorsStillWork() {
    Date date = new Date(1234567890L);
    TimeZone tz = TimeZone.getTimeZone("GMT-5");

    new DateTime(date, tz);
    new DateTime(1234567890L);
    new DateTime(date);
    new DateTime(1234567890L, 300);
    new DateTime(true, 1234567890L, null);
    new DateTime(true, 1234567890L, 0);
    new DateTime("2024-01-01T00:00:00Z");
    new DateTime("2024-01-01");
  }

  @Test
  public void api_allPublicMethodsStillWork() {
    DateTime dt = new DateTime(1700000000000L, 0);
    dt.getValue();
    dt.isDateOnly();
    dt.getTimeZoneShift();
    dt.toStringRfc3339();
    dt.toStringRfc3339Nano(); // new method
    dt.toString();
    dt.equals(dt);
    dt.hashCode();

    DateTime.parseRfc3339("2024-01-01T00:00:00Z");
    DateTime.parseRfc3339ToSecondsAndNanos("2024-01-01T00:00:00.123456789Z");
    SecondsAndNanos.ofSecondsAndNanos(0L, 0);
  }

  @Test
  public void api_nullSentinelStillWorks() {
    // Data.NULL_DATE_TIME = new DateTime(0) semantics preserved
    DateTime nullSentinel = new DateTime(0);
    assertEquals(0L, nullSentinel.getValue());
    assertEquals(0, nullSentinel.getTimeZoneShift());
    assertEquals(new DateTime(0), new DateTime(0));
    assertNotEquals(new DateTime(0), new DateTime(1));

    // Round-trip via string still works
    String s = nullSentinel.toStringRfc3339();
    DateTime reparsed = DateTime.parseRfc3339(s);
    assertEquals(nullSentinel, reparsed);
  }

  // =========================================================================
  // Sub-millisecond precision preserved internally (SecondsAndNanos)
  // =========================================================================
  @Test
  public void precision_parseRfc3339ToSecondsAndNanos_preservesNanos() {
    SecondsAndNanos san = DateTime.parseRfc3339ToSecondsAndNanos(
        "2024-01-01T12:00:00.123456789Z");
    assertEquals(123456789, san.getNanos());

    san = DateTime.parseRfc3339ToSecondsAndNanos("2024-01-01T12:00:00.1Z");
    assertEquals(100000000, san.getNanos());

    san = DateTime.parseRfc3339ToSecondsAndNanos("2024-01-01T12:00:00.000000001Z");
    assertEquals(1, san.getNanos());
  }

  // =========================================================================
  // JSON serialization compatibility
  // =========================================================================
  @Test
  public void jsonCompatibility_standardFormats() {
    String[] standardInputs = {
        "2024-01-01T00:00:00Z",
        "2024-06-15T12:30:45.123Z",
        "2024-12-31T23:59:59.999-05:00",
        "2024-01-01T09:00:00+09:00",
    };

    for (String input : standardInputs) {
      DateTime dt = DateTime.parseRfc3339(input);
      String output = dt.toStringRfc3339();
      DateTime reparsed = DateTime.parseRfc3339(output);
      assertEquals("Round-trip: " + input, dt, reparsed);
    }
  }

  // =========================================================================
  // Class/package names unchanged
  // =========================================================================
  @Test
  public void api_classNamesUnchanged() throws ClassNotFoundException {
    assertEquals("com.google.api.client.util.DateTime",
        Class.forName("com.google.api.client.util.DateTime").getName());
    assertEquals("com.google.api.client.util.DateTime$SecondsAndNanos",
        Class.forName("com.google.api.client.util.DateTime$SecondsAndNanos").getName());
  }
}
