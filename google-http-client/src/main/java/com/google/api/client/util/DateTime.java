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

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

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

  private final DateTimeInstant instant;

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
        zone == null ? null : zone.getOffset(date.getTime()) / 60000);
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
    this(false, value, null);
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
    this(value.getTime());
  }

  /**
   * Instantiates {@link DateTime} from the number of milliseconds since the Unix epoch, and a shift
   * from UTC in minutes.
   *
   * @param value number of milliseconds since the Unix epoch (January 1, 1970, 00:00:00 GMT)
   * @param tzShift time zone, represented by the number of minutes off of UTC.
   */
  public DateTime(long value, int tzShift) {
    this(false, value, tzShift);
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
    this.instant = DateTimeInstant.ofEpochMillis(value, dateOnly, tzShift);
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
    this.instant = DateTimeParser.parseRfc3339(value);
  }

  private DateTime(DateTimeInstant instant) {
    this.instant = instant;
  }

  /**
   * Returns the date/time value expressed as the number of milliseconds since the Unix epoch.
   *
   * <p>If the time zone is specified, this value is normalized to UTC, so to format this date/time
   * value, the time zone shift has to be applied.
   *
   * @since 1.5
   */
  public long getValue() {
    return instant.getEpochMillis();
  }

  /**
   * Returns whether this is a date-only value.
   *
   * @since 1.5
   */
  public boolean isDateOnly() {
    return instant.isDateOnly();
  }

  /**
   * Returns the time zone shift from UTC in minutes or {@code 0} for date-only value.
   *
   * @since 1.5
   */
  public int getTimeZoneShift() {
    return instant.getTimeZoneShift();
  }

  /** Formats the value as an RFC 3339 date/time string. */
  public String toStringRfc3339() {
    return DateTimeFormatter.toStringRfc3339(instant);
  }

  @Override
  public String toString() {
    return toStringRfc3339();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Equality is based on the absolute moment in time (UTC normalized), regardless of time zone
   * shift or sub-millisecond precision differences. Two DateTime instances represent the same
   * moment if their UTC epoch time (including nanoseconds) is identical, and they have the same
   * dateOnly flag.
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
    return instant.equals(other.instant);
  }

  @Override
  public int hashCode() {
    return instant.hashCode();
  }

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
   * <p>Sub-millisecond precision (fractional second digits beyond 3) is preserved internally and
   * will be reflected in formatted output.
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
    DateTimeInstant parsed = DateTimeParser.parseRfc3339(str);
    return new DateTime(parsed);
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
    return DateTimeParser.parseRfc3339ToSecondsAndNanos(str);
  }

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
}
