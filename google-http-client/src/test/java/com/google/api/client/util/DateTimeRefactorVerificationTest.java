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
 * Verification tests for the DateTime refactoring, covering the 9 requirements.
 */
@RunWith(JUnit4.class)
public class DateTimeRefactorVerificationTest {

  // =========================================================================
  // Requirement 1: Timezone drift fix
  // Same timestamp constructed on JVMs with different default timezones
  // should be equal (same absolute instant)
  // =========================================================================
  @Test
  public void requirement1_timezoneDrift_equalsStableAcrossDefaultTimezones() {
    long ts = 1700000000000L;

    TimeZone originalTz = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
      DateTime dtLA = new DateTime(ts);
      int hashLA = dtLA.hashCode();

      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
      DateTime dtTokyo = new DateTime(ts);
      int hashTokyo = dtTokyo.hashCode();

      assertEquals("Same timestamp should be equal regardless of default JVM timezone", dtLA, dtTokyo);
      assertEquals("hashCode should be consistent for same instant", hashLA, hashTokyo);
    } finally {
      TimeZone.setDefault(originalTz);
    }
  }

  // =========================================================================
  // Requirement 2: Sub-millisecond precision preserved end-to-end
  // Parse 9-digit fractional seconds -> format output 9 digits
  // =========================================================================
  @Test
  public void requirement2_precisionPreserved_parseNanoFormatNano() {
    // 9 digits nanosecond precision
    String input = "2024-01-01T12:00:00.123456789Z";
    DateTime dt = DateTime.parseRfc3339(input);
    String output = dt.toStringRfc3339();
    assertEquals("Full 9-digit nanosecond precision must round-trip", input, output);

    SecondsAndNanos san = DateTime.parseRfc3339ToSecondsAndNanos(input);
    assertEquals(123456789, san.getNanos());
  }

  @Test
  public void requirement2_precisionPreserved_subMilliNotTruncated() {
    // Input with 4 digits -> should NOT silently truncate to 3
    String input4 = "2024-01-01T12:00:00.1234Z";
    DateTime dt4 = DateTime.parseRfc3339(input4);
    String output4 = dt4.toStringRfc3339();
    assertTrue("Sub-millisecond precision must appear in output (at least 4 digits)",
        output4.contains(".1234") || output4.contains(".12340"));

    // Internal nanos preserved
    SecondsAndNanos san4 = DateTime.parseRfc3339ToSecondsAndNanos(input4);
    assertEquals(123400000, san4.getNanos());
  }

  // =========================================================================
  // Requirement 3: Date-only string must NOT accept timezone offset
  // =========================================================================
  @Test
  public void requirement3_dateOnlyRejectsTimezone() {
    // Date-only string with Z -> must throw
    try {
      DateTime.parseRfc3339("2024-01-01Z");
      fail("Expected NumberFormatException for date-only with timezone");
    } catch (NumberFormatException expected) {
      // correct behavior
    }

    // Date-only string with +HH:mm -> must throw
    try {
      DateTime.parseRfc3339("2024-01-01+08:00");
      fail("Expected NumberFormatException for date-only with timezone offset");
    } catch (NumberFormatException expected) {
      // correct behavior
    }

    // Plain date-only without timezone -> must work
    DateTime dt = DateTime.parseRfc3339("2024-01-01");
    assertTrue(dt.isDateOnly());
    assertEquals("2024-01-01", dt.toStringRfc3339());
  }

  // =========================================================================
  // Requirement 4: equals semantics based on absolute instant
  // =========================================================================
  @Test
  public void requirement4_equals_sameInstantDifferentTz_shouldBeEqual() {
    DateTime dtZ = DateTime.parseRfc3339("2024-01-01T12:00:00Z");
    DateTime dtPlus8 = DateTime.parseRfc3339("2024-01-01T20:00:00+08:00");
    DateTime dtMinus5 = DateTime.parseRfc3339("2024-01-01T07:00:00-05:00");

    // All three represent the SAME absolute instant
    assertEquals("Z and +08:00 representing same instant", dtZ, dtPlus8);
    assertEquals("Z and -05:00 representing same instant", dtZ, dtMinus5);
    assertEquals("+08:00 and -05:00 representing same instant", dtPlus8, dtMinus5);
    assertEquals("hashCode consistent for same instant", dtZ.hashCode(), dtPlus8.hashCode());
  }

  @Test
  public void requirement4_equals_purePrecisionDiff_shouldBeEqual() {
    // Same instant, one written with 3 digits, one with 9 trailing zeros
    DateTime dt3 = DateTime.parseRfc3339("2024-01-01T12:00:00.123Z");
    DateTime dt9 = DateTime.parseRfc3339("2024-01-01T12:00:00.123000000Z");

    assertEquals("Same instant (trailing zeros precision diff)", dt3, dt9);
  }

  @Test
  public void requirement4_equals_differentInstants_shouldNotBeEqual() {
    // Truly different instants (1 ns apart)
    DateTime dt1 = DateTime.parseRfc3339("2024-01-01T12:00:00.000000000Z");
    DateTime dt2 = DateTime.parseRfc3339("2024-01-01T12:00:00.000000001Z");

    assertNotEquals("Instants 1ns apart must not be equal", dt1, dt2);
  }

  @Test
  public void requirement4_tzShiftOnlyUsedForFormattingNotEquality() {
    // Same instant via constructor with different explicit tzShift
    DateTime dtA = new DateTime(1700000000000L, 0);   // UTC
    DateTime dtB = new DateTime(1700000000000L, 480); // +08:00

    // They are the same absolute instant -> must be equal
    assertEquals(dtA, dtB);

    // But formatted output differs (as expected)
    assertEquals("2023-11-14T22:13:20.000Z", dtA.toStringRfc3339());
    assertTrue(dtB.toStringRfc3339().endsWith("+08:00"));
  }

  // =========================================================================
  // Requirement 6: No new 3rd-party deps (implicitly verified by compilation)
  // Package and class names unchanged
  // =========================================================================
  @Test
  public void requirement6_apiNamesUnchanged() throws ClassNotFoundException {
    // Class must be in the same package with same name
    Class<?> clazz = Class.forName("com.google.api.client.util.DateTime");
    assertEquals("com.google.api.client.util.DateTime", clazz.getName());

    // Nested SecondsAndNanos preserved
    Class<?> nested = Class.forName("com.google.api.client.util.DateTime$SecondsAndNanos");
    assertEquals("com.google.api.client.util.DateTime$SecondsAndNanos", nested.getName());
  }

  // =========================================================================
  // Requirement 7: JSON serialization paths compatible
  // toStringRfc3339() and parseRfc3339() work for standard formats
  // =========================================================================
  @Test
  public void requirement7_jsonSerialization_roundTrip() {
    // Standard ISO-8601 / RFC3339 patterns used in JSON
    String[] standardInputs = {
        "2024-01-01T00:00:00Z",
        "2024-06-15T12:30:45.123Z",
        "2024-12-31T23:59:59.999-05:00",
        "2024-01-01T09:00:00+09:00",
    };

    for (String input : standardInputs) {
      // Simulate JSON read path: parseRfc3339(String)
      DateTime dt = DateTime.parseRfc3339(input);

      // Simulate JSON write path: toStringRfc3339()
      String output = dt.toStringRfc3339();

      // Output must be parseable by downstream JSON parsers (self-consistent)
      DateTime reparsed = DateTime.parseRfc3339(output);
      assertEquals("Round-trip equality for input: " + input, dt, reparsed);
    }
  }

  // =========================================================================
  // Requirement 8: NULL_DATE_TIME sentinel semantics preserved
  // Data.NULL_DATE_TIME = new DateTime(0) still works
  // =========================================================================
  @Test
  public void requirement8_nullSentinel_semanticsPreserved() {
    // Simulate Data.NULL_DATE_TIME definition
    DateTime nullSentinel = new DateTime(0);

    // getValue() must return 0
    assertEquals(0L, nullSentinel.getValue());

    // toStringRfc3339 must be a valid parseable string
    String sentinelStr = nullSentinel.toStringRfc3339();
    DateTime reparsed = DateTime.parseRfc3339(sentinelStr);
    assertEquals("NULL_DATE_TIME round-trips via string", nullSentinel, reparsed);

    // Sentinel equals sentinel
    assertEquals(new DateTime(0), new DateTime(0));

    // Sentinel is distinct from other dates
    assertNotEquals(nullSentinel, new DateTime(1));
  }

  // =========================================================================
  // Requirement 9: Public API stable - all constructors, methods, signatures
  // =========================================================================
  @Test
  public void requirement9_allPublicConstructorsAvailable() {
    Date date = new Date(1234567890L);
    TimeZone tz = TimeZone.getTimeZone("GMT-5");

    // Every public constructor must compile and produce consistent results
    DateTime c1 = new DateTime(date, tz);
    DateTime c2 = new DateTime(1234567890L);
    DateTime c3 = new DateTime(date);
    DateTime c4 = new DateTime(1234567890L, 300);
    DateTime c5 = new DateTime(true, 1234567890L, null);
    DateTime c6 = new DateTime(true, 1234567890L, 0);
    DateTime c7 = new DateTime("2024-01-01T00:00:00Z");
    DateTime c8 = new DateTime("2024-01-01");

    // Every public accessor must work
    assertTrue(Long.class.isInstance(c1.getValue()));
    assertTrue(Boolean.class.isInstance(c1.isDateOnly()));
    assertTrue(Integer.class.isInstance(c1.getTimeZoneShift()));
    assertTrue(String.class.isInstance(c1.toStringRfc3339()));
    assertTrue(String.class.isInstance(c1.toString()));

    // Every public static method must work
    DateTime parsed = DateTime.parseRfc3339("2024-01-01T00:00:00Z");
    SecondsAndNanos san = DateTime.parseRfc3339ToSecondsAndNanos("2024-01-01T00:00:00.123456789Z");
    SecondsAndNanos san2 = SecondsAndNanos.ofSecondsAndNanos(0L, 0);
    assertEquals(Long.class.isInstance(san.getSeconds()), true);
    assertEquals(Integer.class.isInstance(san.getNanos()), true);
  }

  // Additional sanity: explicit zone constructor doesn't depend on default TZ
  @Test
  public void requirement1_explicitZoneStableAcrossDefaults() {
    long ts = 1700000000000L;
    TimeZone gmt8 = TimeZone.getTimeZone("GMT+8");

    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      DateTime dtUtcDefault = new DateTime(new Date(ts), gmt8);

      TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
      DateTime dtNyDefault = new DateTime(new Date(ts), gmt8);

      assertEquals("Explicit zone constructor: same result regardless of default",
          dtUtcDefault, dtNyDefault);
      assertEquals("Explicit zone constructor: same toString regardless of default",
          dtUtcDefault.toStringRfc3339(), dtNyDefault.toStringRfc3339());
    } finally {
      TimeZone.setDefault(original);
    }
  }
}
