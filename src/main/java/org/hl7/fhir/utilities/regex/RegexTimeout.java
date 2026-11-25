package org.hl7.fhir.utilities.regex;

/*-
 * #%L
 * validation-service
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
 * %%
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Executes regular expression operations with a deadline, without creating a worker thread per
 * call.
 *
 * <p>This class has the same public API as the FHIR Core 6.9.12 implementation. It is included here
 * so the application class takes precedence over that dependency's implementation at runtime.
 *
 * <p>Remove this class once the validation-service project upgrades to <code>org.hl7.fhir</code>
 * version 6.10.2 or later, which includes this implementation.
 */
@SuppressWarnings({"checkstyle:patternUsage", "checkstyle:stringImplicitPatternUsage"})
public final class RegexTimeout {

  private static final long DEFAULT_TIMEOUT = 500;
  private static final int MAX_CACHED_PATTERNS = 1000;
  private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

  private RegexTimeout() {
    throw new UnsupportedOperationException("This utility class should not be instantiated");
  }

  private static Pattern patternFor(String regex) {
    Pattern pattern = PATTERNS.get(regex);
    if (pattern != null) {
      return pattern;
    }

    pattern = Pattern.compile(regex);
    synchronized (PATTERNS) {
      if (PATTERNS.size() < MAX_CACHED_PATTERNS) {
        Pattern existing = PATTERNS.putIfAbsent(regex, pattern);
        if (existing != null) {
          return existing;
        }
      }
    }
    return pattern;
  }

  private static <T> T runWithDeadline(
      Function<CharSequence, T> regexOperation, CharSequence input, long timeoutMillis)
      throws TimeoutException {
    long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    try {
      return regexOperation.apply(new DeadlineCharSequence(input, deadlineNanos));
    } catch (RegexTimeoutException e) {
      throw new TimeoutException("Regex evaluation timed out after " + timeoutMillis + "ms");
    }
  }

  public static boolean matches(CharSequence charSequence, String regex) throws TimeoutException {
    return matches(charSequence, regex, DEFAULT_TIMEOUT);
  }

  public static boolean matches(CharSequence charSequence, String regex, long timeoutMillis)
      throws TimeoutException {
    return runWithDeadline(
        sequence -> patternFor(regex).matcher(sequence).matches(), charSequence, timeoutMillis);
  }

  public static boolean find(CharSequence charSequence, String regex) throws TimeoutException {
    return find(charSequence, regex, DEFAULT_TIMEOUT);
  }

  public static boolean find(CharSequence charSequence, String regex, long timeoutMillis)
      throws TimeoutException {
    return runWithDeadline(
        sequence -> patternFor(regex).matcher(sequence).find(), charSequence, timeoutMillis);
  }

  public static String replaceAll(CharSequence charSequence, String regex, String replacement)
      throws TimeoutException {
    return replaceAll(charSequence, regex, replacement, DEFAULT_TIMEOUT);
  }

  public static String replaceAll(
      CharSequence charSequence, String regex, String replacement, long timeoutMillis)
      throws TimeoutException {
    return runWithDeadline(
        sequence -> patternFor(regex).matcher(sequence).replaceAll(replacement),
        charSequence,
        timeoutMillis);
  }

  public static String replaceFirst(CharSequence charSequence, String regex, String replacement)
      throws TimeoutException {
    return replaceFirst(charSequence, regex, replacement, DEFAULT_TIMEOUT);
  }

  public static String replaceFirst(
      CharSequence charSequence, String regex, String replacement, long timeoutMillis)
      throws TimeoutException {
    return runWithDeadline(
        sequence -> patternFor(regex).matcher(sequence).replaceFirst(replacement),
        charSequence,
        timeoutMillis);
  }

  public static String[] split(CharSequence charSequence, String regex) throws TimeoutException {
    return split(charSequence, regex, DEFAULT_TIMEOUT);
  }

  public static String[] split(CharSequence charSequence, String regex, long timeoutMillis)
      throws TimeoutException {
    return runWithDeadline(
        sequence -> patternFor(regex).split(sequence), charSequence, timeoutMillis);
  }

  public static String[] split(CharSequence charSequence, String regex, int limit)
      throws TimeoutException {
    return split(charSequence, regex, limit, DEFAULT_TIMEOUT);
  }

  public static String[] split(
      CharSequence charSequence, String regex, int limit, long timeoutMillis)
      throws TimeoutException {
    return runWithDeadline(
        sequence -> patternFor(regex).split(sequence, limit), charSequence, timeoutMillis);
  }

  private static final class RegexTimeoutException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private RegexTimeoutException() {
      super(null, null, false, false);
    }
  }

  static final class DeadlineCharSequence implements CharSequence {
    private static final int CHECK_INTERVAL = 1024;

    private final CharSequence inner;
    private final long deadlineNanos;
    private int countdown = CHECK_INTERVAL;

    DeadlineCharSequence(CharSequence inner, long deadlineNanos) {
      this.inner = inner;
      this.deadlineNanos = deadlineNanos;
    }

    @Override
    public char charAt(int index) {
      if (--countdown == 0) {
        countdown = CHECK_INTERVAL;
        if (System.nanoTime() - deadlineNanos >= 0) {
          throw new RegexTimeoutException();
        }
      }
      return inner.charAt(index);
    }

    @Override
    public int length() {
      return inner.length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return new DeadlineCharSequence(inner.subSequence(start, end), deadlineNanos);
    }

    @Override
    public String toString() {
      return inner.toString();
    }
  }
}
