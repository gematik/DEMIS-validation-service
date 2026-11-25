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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class RegexTimeoutTest {

  @Test
  void performsRegexOperations() throws TimeoutException {
    assertThat(RegexTimeout.matches("abc", "a.c")).isTrue();
    assertThat(RegexTimeout.matches("abc", "a.d")).isFalse();
    assertThat(RegexTimeout.find("abc", "b")).isTrue();
    assertThat(RegexTimeout.replaceAll("abc", "b", "x")).isEqualTo("axc");
    assertThat(RegexTimeout.replaceFirst("abcabc", "b", "x")).isEqualTo("axcabc");
    assertThat(RegexTimeout.split("a,b,c", ",")).containsExactly("a", "b", "c");
    assertThat(RegexTimeout.split("a,b,c", ",", 2)).containsExactly("a", "b,c");
  }

  @Test
  void evaluatesOnTheCallingThread() throws TimeoutException {
    Thread callingThread = Thread.currentThread();
    ThreadTrackingCharSequence input = new ThreadTrackingCharSequence("abc");

    assertThat(RegexTimeout.matches(input, "abc")).isTrue();

    assertThat(input.accessingThread).isSameAs(callingThread);
  }

  @Test
  void stopsMatchingWhenTheDeadlineHasPassed() {
    assertThatThrownBy(() -> RegexTimeout.matches("a".repeat(2048), "a*", 0))
        .isInstanceOf(TimeoutException.class);
  }

  private static final class ThreadTrackingCharSequence implements CharSequence {
    private final String value;
    private Thread accessingThread;

    private ThreadTrackingCharSequence(String value) {
      this.value = value;
    }

    @Override
    public int length() {
      return value.length();
    }

    @Override
    public char charAt(int index) {
      accessingThread = Thread.currentThread();
      return value.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return new ThreadTrackingCharSequence(value.substring(start, end));
    }
  }
}
