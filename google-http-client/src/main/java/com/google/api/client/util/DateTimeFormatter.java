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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Formatter for RFC3339 date/time strings.
 *
 * <p>This class is package-private and for internal use only. It handles all formatting logic,
 * including proper handling of sub-millisecond precision when available.
 *
 * <p>Implementation is immutable and therefore thread-safe.
 */
final class DateTimeFormatter {

  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

  private DateTimeFormatter() {}

  /**
   * Formats a DateTimeInstant as an RFC3339 date/time string.
   *
   * <p>If the instant has sub-millisecond precision (nanos % 1_000_000 != 0), the output will
   * include nanosecond digits. Otherwise, it will output 3 digits for milliseconds if there are
   * any, or omit fractional seconds entirely if nanos is zero.
   *
   * @param instant the instant to format
   * @return the RFC3339 formatted string
   */
  static String toStringRfc3339(DateTimeInstant instant) {
    StringBuilder sb = new StringBuilder();
    Calendar dateTime = new GregorianCalendar(GMT);
    long localTime = instant.getEpochMillis() + (instant.getTimeZoneShift() * 60000L);
    dateTime.setTimeInMillis(localTime);

    appendInt(sb, dateTime.get(Calendar.YEAR), 4);
    sb.append('-');
    appendInt(sb, dateTime.get(Calendar.MONTH) + 1, 2);
    sb.append('-');
    appendInt(sb, dateTime.get(Calendar.DAY_OF_MONTH), 2);

    if (!instant.isDateOnly()) {
      sb.append('T');
      appendInt(sb, dateTime.get(Calendar.HOUR_OF_DAY), 2);
      sb.append(':');
      appendInt(sb, dateTime.get(Calendar.MINUTE), 2);
      sb.append(':');
      appendInt(sb, dateTime.get(Calendar.SECOND), 2);

      appendFractionalSeconds(sb, instant);
      appendTimeZone(sb, instant.getTimeZoneShift());
    }
    return sb.toString();
  }

  private static void appendFractionalSeconds(StringBuilder sb, DateTimeInstant instant) {
    int nanos = instant.getNanos();
    int fractionDigits = instant.getFractionDigits();

    if (nanos == 0 && fractionDigits == 0) {
      return;
    }

    sb.append('.');

    if (instant.hasSubMillisecondPrecision() || fractionDigits > 3) {
      int effectiveDigits = fractionDigits;
      if (effectiveDigits <= 3) {
        effectiveDigits = 9;
      }
      String nanosStr = String.valueOf(nanos);
      nanosStr = padStart(nanosStr, 9, '0');
      sb.append(nanosStr, 0, effectiveDigits);
    } else {
      int millis = nanos / 1_000_000;
      appendInt(sb, millis, 3);
    }
  }

  private static String padStart(String s, int minLength, char padChar) {
    if (s.length() >= minLength) {
      return s;
    }
    StringBuilder sb = new StringBuilder(minLength);
    for (int i = s.length(); i < minLength; i++) {
      sb.append(padChar);
    }
    sb.append(s);
    return sb.toString();
  }

  private static void appendTimeZone(StringBuilder sb, int tzShift) {
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
