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
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for RFC3339 date/time strings.
 *
 * <p>This class is package-private and for internal use only. It handles all parsing logic and
 * preserves the original fractional second precision from the input string.
 *
 * <p>Implementation is immutable and therefore thread-safe.
 */
final class DateTimeParser {

  private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

  private static final String RFC3339_REGEX =
      "(\\d{4})-(\\d{2})-(\\d{2})"
          + "([Tt](\\d{2}):(\\d{2}):(\\d{2})(\\.\\d{1,9})?)?"
          + "([Zz]|([+-])(\\d{2}):(\\d{2}))?";

  private static final Pattern RFC3339_PATTERN = Pattern.compile(RFC3339_REGEX);

  private DateTimeParser() {}

  /**
   * Parses an RFC3339 date/time string into a DateTimeInstant.
   *
   * @param str the RFC3339 string to parse
   * @return the parsed DateTimeInstant
   * @throws NumberFormatException if the string does not match RFC3339 format
   */
  static DateTimeInstant parseRfc3339(String str) throws NumberFormatException {
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

    if (isTzShiftGiven && !isTimeGiven) {
      throw new NumberFormatException(
          "Invalid date/time format, cannot specify time zone shift"
              + " without specifying time: "
              + str);
    }

    int hourOfDay = 0;
    int minute = 0;
    int second = 0;
    int nanoseconds = 0;
    int fractionDigits = 0;
    Integer tzShiftInteger = null;

    if (isTimeGiven) {
      hourOfDay = Integer.parseInt(matcher.group(5));
      minute = Integer.parseInt(matcher.group(6));
      second = Integer.parseInt(matcher.group(7));
      if (matcher.group(8) != null) {
        String fractionStr = matcher.group(8).substring(1);
        fractionDigits = fractionStr.length();
        String paddedFraction = Strings.padEnd(fractionStr, 9, '0');
        nanoseconds = Integer.parseInt(paddedFraction);
      }
    }

    Calendar dateTime = new GregorianCalendar(GMT);
    dateTime.clear();
    dateTime.set(year, month, day, hourOfDay, minute, second);
    long millis = dateTime.getTimeInMillis();

    if (isTimeGiven && isTzShiftGiven) {
      if (Character.toUpperCase(tzShiftRegexGroup.charAt(0)) != 'Z') {
        int tzShift =
            Integer.parseInt(matcher.group(11)) * 60 + Integer.parseInt(matcher.group(12));
        if (matcher.group(10).charAt(0) == '-') {
          tzShift = -tzShift;
        }
        millis -= tzShift * 60000L;
        tzShiftInteger = tzShift;
      } else {
        tzShiftInteger = 0;
      }
    }

    long secondsSinceEpoch = millis / 1000L;
    return DateTimeInstant.ofEpochSecondsAndNanos(
        secondsSinceEpoch, nanoseconds, !isTimeGiven, tzShiftInteger, fractionDigits);
  }

  /**
   * Parses an RFC3339 date/time string and returns the seconds and nanoseconds since epoch.
   *
   * @param str the RFC3339 string to parse
   * @return SecondsAndNanos containing the parsed value
   * @throws NumberFormatException if the string does not match RFC3339 format
   */
  static DateTime.SecondsAndNanos parseRfc3339ToSecondsAndNanos(String str)
      throws NumberFormatException {
    DateTimeInstant instant = parseRfc3339(str);
    return DateTime.SecondsAndNanos.ofSecondsAndNanos(
        instant.getEpochSeconds(), instant.getNanos());
  }
}
