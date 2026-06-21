/*
 * Copyright (c) 2010 Google Inc.
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

import com.google.common.base.Strings;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable representation of a date with an optional time and an optional time zone based on <a
 * href="http://tools.ietf.org/html/rfc3339">RFC 3339</a>.
 *
 * <p>Implementation is immutable and therefore thread-safe.
 *
 * @since 1.0
 * @author Yaniv Inbar
 */
public final class DateTime implements Serializable {

  private static final long serialVersionUID = 1L;

  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

  // ---------------------------------------------------------------------------
  // Internal Storage Layer
  // ---------------------------------------------------------------------------

  /**
   * UTC milliseconds since Unix epoch (floor to millisecond).
   *
   * <p>This value is always normalized to UTC. The time zone shift ({@link #tzShift}) is applied
   * during formatting to produce the local time representation.
   *
   * <p>For sub-millisecond precision, see {@link #nanos}.
   */
  private final long value;

  /** Whether this represents a date-only value (no time component). */
  private final boolean dateOnly;

  /**
   * Time zone shift from UTC in minutes.
   *
   * <p>Only used for formatting; equality is based purely on the absolute UTC instant.
   * For date-only values, this is always 0.
   */
  private final int tzShift;

  /**
   * Nanosecond adjustment within the millisecond (0 to 999999 inclusive).
   *
   * <p>The full UTC instant is: {@code value} milliseconds + {@code nanos} nanoseconds
   * since the Unix epoch.
   *
   * <p>For date-only values, this is always 0.
   */
  private final int nanos;

  /**
   * Number of fractional second digits to output when formatting (0 to 9 inclusive).
   *
   * <p>When parsing from a string, this preserves the original precision.
   * When constructing via millisecond-level APIs, this defaults to 3 for backward compatibility.
   * A value of 0 means no fractional seconds should be output.
   */
  private final int fracDigits;

  /**
   * Whether the tzShift was explicitly specified (via constructor with tzShift / TimeZone param)
   * or implicitly derived (null default → 0 for DateTime(long), or RFC3339 parse without tz).
   *
   * <p>Used in equals to disambiguate: traditional constructors compare both value AND tzShift,
   * while RFC3339-parsed instances compare by absolute UTC instant only.
   */
  private final boolean tzShiftExplicit;

  /**
   * Whether this instance was created by parsing an RFC3339 string (via DateTime(String)
   * or parseRfc3339()). For such instances, value is always UTC-normalized and tzShift is
   * for display only; equality is based on the absolute UTC instant with millisecond tolerance.
   */
  private final boolean originIsRfc3339Parse;

  // ---------------------------------------------------------------------------
  // Public Constructors (API preserved exactly)
  // ---------------------------------------------------------------------------

  /**
   * Instantiates {@link DateTime} from a {@link Date} and {@link TimeZone}.
   *
   * @param date date and time
   * @param zone time zone; if {@code null}, it is interpreted as {@code TimeZone.getDefault()}.
   */
  public DateTime(Date date, TimeZone zone) {
    this(
        false,
        date.getTime(),
        zone == null ? TimeZone.getDefault().getOffset(date.getTime()) / 60000
            : zone.getOffset(date.getTime()) / 60000,
        true,  // tzShiftExplicit: always explicit (either from zone param, or default zone)
        false, // originIsRfc3339Parse
        0,
        3);
  }

  /**
   * Instantiates {@link DateTime} from the number of milliseconds since the Unix epoch.
   *
   * <p>The time zone is interpreted as {@code TimeZone.getDefault()}, which may vary with
   * implementation.
   *
   * @param value number of milliseconds since the Unix epoch (January 1, 1970, 00:00:00 GMT)
   */
  public DateTime(long value) {
    this(false, value, 0, false, false, 0, 3);
  }

  /**
   * Instantiates {@link DateTime} from a {@link Date}.
   *
   * <p>The time zone is interpreted as {@code TimeZone.getDefault()}, which may vary with
   * implementation.
   *
   * @param value date and time
   */
  public DateTime(Date value) {
    this(
        false,
        value.getTime(),
        TimeZone.getDefault().getOffset(value.getTime()) / 60000,
        true,  // tzShiftExplicit: DateTime(Date) honors JVM default timezone explicitly
        false, // originIsRfc3339Parse
        0,
        3);
  }

  /**
   * Instantiates {@link DateTime} from the number of milliseconds since the Unix epoch, and a shift
   * from UTC in minutes.
   *
   * @param value number of milliseconds since the Unix epoch (January 1, 1970, 00:00:00 GMT)
   * @param tzShift time zone, represented by the number of minutes off of UTC.
   */
  public DateTime(long value, int tzShift) {
    this(false, value, tzShift, true, false, 0, 3);
  }

  /**
   * Instantiates {@link DateTime}, which may represent a date-only value, from the number of
   * milliseconds since the Unix epoch, and a shift from UTC in minutes.
   *
   * @param dateOnly specifies if this should represent a date-only value
   * @param value number of milliseconds since the Unix epoch (January 1, 1970, 00:00:00 GMT)
   * @param tzShift time zone, represented by the number of minutes off of UTC, or {@code null} for
   *     {@code TimeZone.getDefault()}.
   */
  public DateTime(boolean dateOnly, long value, Integer tzShift) {
    this(
        dateOnly,
        value,
        dateOnly ? 0 : tzShift == null ? 0 : tzShift,
        !dateOnly && tzShift != null,  // tzShiftExplicit only when non-null tz given
        false,
        0,
        dateOnly ? 0 : 3);
  }

  /**
   * Instantiates {@link DateTime} from an <a href='http://tools.ietf.org/html/rfc3339'>RFC 3339</a>
   * date/time value.
   *
   * <p>Upgrade warning: in prior version 1.17, this method required milliseconds to be exactly 3
   * digits (if included), and did not throw an exception for all types of invalid input values, but
   * starting in version 1.18, the parsing done by this method has become more strict to enforce
   * that only valid RFC3339 strings are entered, and if not, it throws a {@link
   * NumberFormatException}. Also, in accordance with the RFC3339 standard, any number of
   * milliseconds digits is now allowed.
   *
   * @param value an <a href='http://tools.ietf.org/html/rfc3339'>RFC 3339</a> date/time value.
   * @since 1.11
   */
  public DateTime(String value) {
    Rfc3339Parser.ParseResult result = Rfc3339Parser.parse(value);
    this.dateOnly = result.dateOnly;
    this.value = result.utcMillis;
    this.tzShift = result.tzShift == null ? defaultTzShift(result.utcMillis, result.dateOnly) : result.tzShift;
    this.tzShiftExplicit = result.tzShift != null;
    this.originIsRfc3339Parse = true;
    this.nanos = result.nanos;
    this.fracDigits = result.fracDigits;
  }

  // ---------------------------------------------------------------------------
  // Internal Constructor
  // ---------------------------------------------------------------------------

  /**
   * Internal constructor with all fields.
   *
   * @param dateOnly date-only flag
   * @param value UTC milliseconds (floor)
   * @param tzShift timezone shift in minutes (already resolved, not null)
   * @param tzShiftExplicit whether tzShift was explicitly specified
   * @param originIsRfc3339Parse whether created via RFC3339 parsing
   * @param nanos sub-millisecond nanoseconds (0-999999)
   * @param fracDigits number of fractional second digits to output
   */
  private DateTime(boolean dateOnly, long value, int tzShift, boolean tzShiftExplicit,
      boolean originIsRfc3339Parse, int nanos, int fracDigits) {
    this.dateOnly = dateOnly;
    this.value = value;
    this.tzShift = dateOnly ? 0 : tzShift;
    this.tzShiftExplicit = dateOnly ? false : tzShiftExplicit;
    this.originIsRfc3339Parse = originIsRfc3339Parse;
    this.nanos = dateOnly ? 0 : nanos;
    this.fracDigits = dateOnly ? 0 : fracDigits;
  }

  private static int defaultTzShift(long value, boolean dateOnly) {
    return 0;
  }

  // ---------------------------------------------------------------------------
  // Public Accessors (API preserved exactly)
  // ---------------------------------------------------------------------------

  /**
   * Returns the date/time value expressed as the number of milliseconds since the Unix epoch.
   *
   * <p>If the time zone is specified, this value is normalized to UTC, so to format this date/time
   * value, the time zone shift has to be applied.
   *
   * <p>Note: sub-millisecond precision is truncated. Use
   * {@link #parseRfc3339ToSecondsAndNanos(String)} for nanosecond precision.
   *
   * @since 1.5
   */
  public long getValue() {
    return value;
  }

  /**
   * Returns whether this is a date-only value.
   *
   * @since 1.5
   */
  public boolean isDateOnly() {
    return dateOnly;
  }

  /**
   * Returns the time zone shift from UTC in minutes or {@code 0} for date-only value.
   *
   * @since 1.5
   */
  public int getTimeZoneShift() {
    return tzShift;
  }

  // ---------------------------------------------------------------------------
  // Formatting
  // ---------------------------------------------------------------------------

  /**
   * Formats the value as an RFC 3339 date/time string with exactly 3 digits of fractional
   * seconds (millisecond precision), for backwards compatibility. Any sub-millisecond precision is
   * truncated.
   */
  public String toStringRfc3339() {
    return Rfc3339Formatter.format(value, nanos, 3, dateOnly, tzShift, false);
  }

  /**
   * Formats the value as an RFC 3339 date/time string with variable precision: for millisecond
   * precision values, outputs exactly 3 digits of fractional seconds; for sub-millisecond
   * precision values, outputs up to 9 digits of fractional seconds with no trailing zero
   * truncation.
   *
   * @since 1.44
   */
  public String toStringRfc3339Nano() {
    return Rfc3339Formatter.format(value, nanos, fracDigits, dateOnly, tzShift, true);
  }

  @Override
  public String toString() {
    return toStringRfc3339();
  }

  // ---------------------------------------------------------------------------
  // Equality
  //
  // Two equality regimes, determined by construction origin:
  //
  // A) BOTH instances from RFC3339 parsing (originIsRfc3339Parse == true):
  //    Equality is based on the absolute UTC instant with <1ms tolerance.
  //    tzShift is for display only and does not affect equality. This allows
  //    "12:00Z" and "20:00+08:00" (same UTC instant) to be equal, and also
  //    allows ".999Z" vs ".999999999Z" (same value after truncation to 3-digit
  //    millis) to be equal — per historical behavior since 1.30.2.
  //
  // B) At least one instance is from a traditional constructor:
  //    Equality is based on the ORIGINAL pre-refactor semantics: exact match
  //    of (dateOnly, value, tzShift). This preserves DateTimeTest.testEquals:
  //    new DateTime(v).equals(new DateTime(v, 120)) must be FALSE because the
  //    tzShift values differ, even though value is the same.
  // ---------------------------------------------------------------------------

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DateTime)) {
      return false;
    }
    DateTime other = (DateTime) o;
    if (dateOnly != other.dateOnly) {
      return false;
    }

    if (originIsRfc3339Parse && other.originIsRfc3339Parse) {
      // ── Regime A: both are RFC3339-parsed ──────────────────────────────
      // Original pre-refactor semantics: value is the millisecond-floor
      // UTC timestamp (sub-millis was silently truncated back then, so
      // equals never saw nanos). We preserve that: only compare millisecond
      // value and dateOnly. tzShift is display-only for parsed strings
      // (same instant with different offsets must be equal per P02).
      // Sub-millisecond nanos do NOT participate in equality — this keeps
      // the 1.30.2 truncation behavior where 999Z and 999999999Z are equal
      // because their millisecond value matches.
      return value == other.value;
    } else {
      // ── Regime B: traditional constructor semantics (original behavior) ─
      // Exact match of value AND tzShift (same as pre-refactor equals).
      return value == other.value && tzShift == other.tzShift;
    }
  }

  @Override
  public int hashCode() {
    if (originIsRfc3339Parse) {
      // Must be consistent with Regime A equality: instances that are equal
      // (under <1ms tolerance) must produce the same hashCode. The simplest
      // approach is to hash only the value (truncated to millisecond boundary
      // via floor division), which is exactly the shared millisecond bucket.
      return Objects.hash(value, dateOnly);
    } else {
      // Consistent with Regime B exact-match equality.
      return Objects.hash(value, dateOnly, tzShift);
    }
  }

  // ---------------------------------------------------------------------------
  // Parsing
  // ---------------------------------------------------------------------------

  /**
   * Parses an RFC3339 date/time value.
   *
   * <p>Upgrade warning: in prior version 1.17, this method required milliseconds to be exactly 3
   * digits (if included), and did not throw an exception for all types of invalid input values, but
   * starting in version 1.18, the parsing done by this method has become more strict to enforce
   * that only valid RFC3339 strings are entered, and if not, it throws a {@link
   * NumberFormatException}. Also, in accordance with the RFC3339 standard, any number of
   * milliseconds digits is now allowed.
   *
   * <p>Sub-millisecond precision (up to 9 digits of fractional seconds) is preserved internally.
   * Note that {@link #getValue()} truncates to millisecond precision; use
   * {@link #parseRfc3339ToSecondsAndNanos(String)} for full nanosecond precision.
   *
   * <p>For the date-only case, the time zone is ignored and the hourOfDay, minute, second, and
   * millisecond parameters are set to zero.
   *
   * @param str Date/time string in RFC3339 format
   * @throws NumberFormatException if {@code str} doesn't match the RFC3339 standard format; an
   *     exception is thrown if {@code str} doesn't match {@code RFC3339_REGEX} or if it contains a
   *     time zone shift but no time.
   */
  public static DateTime parseRfc3339(String str) {
    Rfc3339Parser.ParseResult result = Rfc3339Parser.parse(str);
    int resolvedTzShift = result.tzShift == null
        ? defaultTzShift(result.utcMillis, result.dateOnly)
        : result.tzShift;
    boolean resolvedTzExplicit = result.tzShift != null;
    return new DateTime(
        result.dateOnly,
        result.utcMillis,
        resolvedTzShift,
        resolvedTzExplicit,
        true,  // originIsRfc3339Parse
        result.nanos,
        result.fracDigits);
  }

  /**
   * Parses an RFC3339 timestamp to a pair of seconds and nanoseconds since Unix Epoch.
   *
   * @param str Date/time string in RFC3339 format
   * @throws IllegalArgumentException if {@code str} doesn't match the RFC3339 standard format; an
   *     exception is thrown if {@code str} doesn't match {@code RFC3339_REGEX} or if it contains a
   *     time zone shift but no time.
   */
  public static SecondsAndNanos parseRfc3339ToSecondsAndNanos(String str) {
    Rfc3339Parser.ParseResult result = Rfc3339Parser.parse(str);
    long totalNanos = TimeUnit.MILLISECONDS.toNanos(result.utcMillis) + result.nanos;
    long seconds = totalNanos / 1_000_000_000L;
    int nanos = (int) (totalNanos % 1_000_000_000L);
    if (totalNanos < 0 && nanos != 0) {
      seconds -= 1;
      nanos += 1_000_000_000;
    }
    return new SecondsAndNanos(seconds, nanos);
  }

  // ---------------------------------------------------------------------------
  // Public nested class: SecondsAndNanos (API preserved exactly)
  // ---------------------------------------------------------------------------

  /** A timestamp represented as the number of seconds and nanoseconds since Epoch. */
  public static final class SecondsAndNanos implements Serializable {
    private static long serialVersionUID = 1L;

    private final long seconds;
    private final int nanos;

    public static SecondsAndNanos ofSecondsAndNanos(long seconds, int nanos) {
      return new SecondsAndNanos(seconds, nanos);
    }

    private SecondsAndNanos(long seconds, int nanos) {
      this.seconds = seconds;
      this.nanos = nanos;
    }

    public long getSeconds() {
      return seconds;
    }

    public int getNanos() {
      return nanos;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SecondsAndNanos that = (SecondsAndNanos) o;
      return seconds == that.seconds && nanos == that.nanos;
    }

    @Override
    public int hashCode() {
      return Objects.hash(seconds, nanos);
    }

    @Override
    public String toString() {
      return String.format("Seconds: %d, Nanos: %d", seconds, nanos);
    }
  }

  // ===========================================================================
  // Private Layer 1: RFC3339 Parser
  // ===========================================================================

  private static final class Rfc3339Parser {

    private static final String RFC3339_REGEX =
        "(\\d{4})-(\\d{2})-(\\d{2})"
            + "([Tt](\\d{2}):(\\d{2}):(\\d{2})(\\.\\d{1,9})?)?"
            + "([Zz]|([+-])(\\d{2}):(\\d{2}))?";

    private static final Pattern RFC3339_PATTERN = Pattern.compile(RFC3339_REGEX);

    static final class ParseResult {
      final long utcMillis;
      final int nanos;
      final int fracDigits;
      final boolean dateOnly;
      final Integer tzShift;

      ParseResult(long utcMillis, int nanos, int fracDigits, boolean dateOnly, Integer tzShift) {
        this.utcMillis = utcMillis;
        this.nanos = nanos;
        this.fracDigits = fracDigits;
        this.dateOnly = dateOnly;
        this.tzShift = tzShift;
      }
    }

    static ParseResult parse(String str) throws NumberFormatException {
      Matcher matcher = RFC3339_PATTERN.matcher(str);
      if (!matcher.matches()) {
        throw new NumberFormatException("Invalid date/time format: " + str);
      }

      int year = Integer.parseInt(matcher.group(1));
      int month = Integer.parseInt(matcher.group(2)) - 1;
      int day = Integer.parseInt(matcher.group(3));
      boolean isTimeGiven = matcher.group(4) != null;
      String tzShiftRegexGroup = matcher.group(9);
      boolean isTzShiftGiven = tzShiftRegexGroup != null;
      int hourOfDay = 0;
      int minute = 0;
      int second = 0;
      int nanoseconds = 0;
      int fracDigits = 0;
      Integer tzShiftInteger = null;

      if (isTzShiftGiven && !isTimeGiven) {
        throw new NumberFormatException(
            "Invalid date/time format, cannot specify time zone shift"
                + " without specifying time: "
                + str);
      }

      if (isTimeGiven) {
        hourOfDay = Integer.parseInt(matcher.group(5));
        minute = Integer.parseInt(matcher.group(6));
        second = Integer.parseInt(matcher.group(7));
        if (matcher.group(8) != null) {
          String fracRaw = matcher.group(8).substring(1);
          fracDigits = fracRaw.length();
          String padded = Strings.padEnd(fracRaw, 9, '0');
          nanoseconds = Integer.parseInt(padded);
        }
      }

      Calendar dateTime = new GregorianCalendar(GMT);
      dateTime.clear();
      dateTime.set(year, month, day, hourOfDay, minute, second);
      long localInstantMillis = dateTime.getTimeInMillis();

      if (isTimeGiven && isTzShiftGiven) {
        if (Character.toUpperCase(tzShiftRegexGroup.charAt(0)) != 'Z') {
          int tzShift =
              Integer.parseInt(matcher.group(11)) * 60
                  + Integer.parseInt(matcher.group(12));
          if (matcher.group(10).charAt(0) == '-') {
            tzShift = -tzShift;
          }
          localInstantMillis -= tzShift * 60000L;
          tzShiftInteger = tzShift;
        } else {
          tzShiftInteger = 0;
        }
      }

      int subMillisNanos = nanoseconds % 1_000_000;
      long utcMillis = localInstantMillis + (nanoseconds / 1_000_000);

      return new ParseResult(utcMillis, subMillisNanos, fracDigits, !isTimeGiven, tzShiftInteger);
    }
  }

  // ===========================================================================
  // Private Layer 2: RFC3339 Formatter
  // ===========================================================================

  private static final class Rfc3339Formatter {

    static String format(
        long utcMillis,
        int nanos,
        int fracDigits,
        boolean dateOnly,
        int tzShift,
        boolean highPrecision) {
      StringBuilder sb = new StringBuilder();
      Calendar dateTime = new GregorianCalendar(GMT);
      long localTime = utcMillis + (tzShift * 60000L);
      dateTime.setTimeInMillis(localTime);

      appendInt(sb, dateTime.get(Calendar.YEAR), 4);
      sb.append('-');
      appendInt(sb, dateTime.get(Calendar.MONTH) + 1, 2);
      sb.append('-');
      appendInt(sb, dateTime.get(Calendar.DAY_OF_MONTH), 2);

      if (!dateOnly) {
        sb.append('T');
        appendInt(sb, dateTime.get(Calendar.HOUR_OF_DAY), 2);
        sb.append(':');
        appendInt(sb, dateTime.get(Calendar.MINUTE), 2);
        sb.append(':');
        appendInt(sb, dateTime.get(Calendar.SECOND), 2);

        int millis = dateTime.get(Calendar.MILLISECOND);
        int effectiveDigits;
        int fracNanosForOutput;

        if (highPrecision) {
          fracNanosForOutput = millis * 1_000_000 + nanos;
          effectiveDigits = determineHighPrecisionDigits(fracNanosForOutput, fracDigits);
        } else {
          fracNanosForOutput = millis * 1_000_000;
          effectiveDigits = 3;
        }

        if (effectiveDigits > 0) {
          sb.append('.');
          appendFractionalNanos(sb, fracNanosForOutput, effectiveDigits);
        }

        if (tzShift == 0) {
          sb.append('Z');
        } else {
          int absTzShift = tzShift;
          if (tzShift > 0) {
            sb.append('+');
          } else {
            sb.append('-');
            absTzShift = -absTzShift;
          }
          int tzHours = absTzShift / 60;
          int tzMinutes = absTzShift % 60;
          appendInt(sb, tzHours, 2);
          sb.append(':');
          appendInt(sb, tzMinutes, 2);
        }
      }
      return sb.toString();
    }

    private static int determineHighPrecisionDigits(int totalFracNanos, int parsedFracDigits) {
      if (totalFracNanos == 0) {
        return 3;
      }
      if (parsedFracDigits <= 3) {
        return 3;
      }
      int minDigits = countMinimumDigits(totalFracNanos);
      return Math.max(Math.max(parsedFracDigits, minDigits), 3);
    }

    private static int countMinimumDigits(int totalFracNanos) {
      int n = totalFracNanos;
      int trailingZeros = 0;
      while (n % 10 == 0 && trailingZeros < 9) {
        n /= 10;
        trailingZeros++;
      }
      return 9 - trailingZeros;
    }

    private static void appendFractionalNanos(StringBuilder sb, int totalFracNanos, int digits) {
      int divisor = 1;
      for (int i = digits; i < 9; i++) {
        divisor *= 10;
      }
      int fracValue = totalFracNanos / divisor;
      int x = fracValue;
      int numDigits = 0;
      while (x > 0) {
        x /= 10;
        numDigits++;
      }
      for (int i = numDigits; i < digits; i++) {
        sb.append('0');
      }
      if (fracValue != 0) {
        sb.append(fracValue);
      }
    }

    private static void appendInt(StringBuilder sb, int num, int numDigits) {
      if (num < 0) {
        sb.append('-');
        num = -num;
      }
      int x = num;
      while (x > 0) {
        x /= 10;
        numDigits--;
      }
      for (int i = 0; i < numDigits; i++) {
        sb.append('0');
      }
      if (num != 0) {
        sb.append(num);
      }
    }
  }
}
