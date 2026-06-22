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
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Internal storage class for date/time instant with nanosecond precision.
 *
 * <p>This class is package-private and for internal use only. It stores the absolute moment in
 * time as seconds since Unix epoch plus nanoseconds adjustment, along with metadata for date-only
 * flag, timezone shift, and original fractional digit precision.
 *
 * <p>Implementation is immutable and therefore thread-safe.
 */
final class DateTimeInstant implements Serializable {

  private static final long serialVersionUID = 1L;

  static final DateTimeInstant ZERO = new DateTimeInstant(0, 0, false, 0, 0);

  private final long seconds;

  private final int nanos;

  private final boolean dateOnly;

  private final int tzShift;

  private final int fractionDigits;

  private DateTimeInstant(
      long seconds, int nanos, boolean dateOnly, int tzShift, int fractionDigits) {
    if (nanos < 0 || nanos >= 1_000_000_000) {
      throw new IllegalArgumentException("nanos must be in range [0, 999999999]");
    }
    if (fractionDigits < 0 || fractionDigits > 9) {
      throw new IllegalArgumentException("fractionDigits must be in range [0, 9]");
    }
    long normalizedSeconds = seconds + nanos / 1_000_000_000;
    int normalizedNanos = nanos % 1_000_000_000;
    this.seconds = normalizedSeconds;
    this.nanos = normalizedNanos;
    this.dateOnly = dateOnly;
    this.tzShift = dateOnly ? 0 : tzShift;
    this.fractionDigits = fractionDigits;
  }

  static DateTimeInstant ofEpochMillis(long epochMillis, boolean dateOnly, Integer tzShift) {
    long seconds = TimeUnit.MILLISECONDS.toSeconds(epochMillis);
    int nanos = (int) (TimeUnit.MILLISECONDS.toNanos(epochMillis % 1000));
    if (nanos < 0) {
      nanos += 1_000_000_000;
      seconds -= 1;
    }
    int resolvedTzShift =
        dateOnly ? 0 : tzShift == null ? TimeZone.getDefault().getOffset(epochMillis) / 60000 : tzShift;
    return new DateTimeInstant(seconds, nanos, dateOnly, resolvedTzShift, 3);
  }

  static DateTimeInstant ofEpochSecondsAndNanos(
      long seconds, int nanos, boolean dateOnly, Integer tzShift, int fractionDigits) {
    int resolvedTzShift =
        dateOnly
            ? 0
            : tzShift == null
                ? TimeZone.getDefault()
                        .getOffset(TimeUnit.SECONDS.toMillis(seconds) + nanos / 1_000_000)
                    / 60000
                : tzShift;
    return new DateTimeInstant(seconds, nanos, dateOnly, resolvedTzShift, fractionDigits);
  }

  static DateTimeInstant ofUtcMillis(long epochMillis, boolean dateOnly, int tzShift) {
    long seconds = TimeUnit.MILLISECONDS.toSeconds(epochMillis);
    int nanos = (int) (TimeUnit.MILLISECONDS.toNanos(epochMillis % 1000));
    if (nanos < 0) {
      nanos += 1_000_000_000;
      seconds -= 1;
    }
    return new DateTimeInstant(seconds, nanos, dateOnly, tzShift, nanos == 0 ? 0 : 3);
  }

  long getEpochSeconds() {
    return seconds;
  }

  int getNanos() {
    return nanos;
  }

  long getEpochMillis() {
    return TimeUnit.SECONDS.toMillis(seconds) + TimeUnit.NANOSECONDS.toMillis(nanos);
  }

  boolean isDateOnly() {
    return dateOnly;
  }

  int getTimeZoneShift() {
    return tzShift;
  }

  int getFractionDigits() {
    return fractionDigits;
  }

  boolean hasSubMillisecondPrecision() {
    return nanos % 1_000_000 != 0;
  }

  long getTotalNanos() {
    return seconds * 1_000_000_000L + nanos;
  }

  DateTimeInstant withDateOnly(boolean newDateOnly) {
    return new DateTimeInstant(
        seconds, nanos, newDateOnly, newDateOnly ? 0 : tzShift, fractionDigits);
  }

  DateTimeInstant withTimeZoneShift(int newTzShift) {
    return new DateTimeInstant(seconds, nanos, dateOnly, dateOnly ? 0 : newTzShift, fractionDigits);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DateTimeInstant)) {
      return false;
    }
    DateTimeInstant other = (DateTimeInstant) o;
    return seconds == other.seconds && nanos == other.nanos && dateOnly == other.dateOnly;
  }

  @Override
  public int hashCode() {
    long totalNanos = getTotalNanos();
    int result = (int) (totalNanos ^ (totalNanos >>> 32));
    result = 31 * result + (dateOnly ? 1 : 0);
    return result;
  }
}
