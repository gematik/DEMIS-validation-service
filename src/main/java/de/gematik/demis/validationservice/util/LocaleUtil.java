/*
 * Copyright [2023], gematik GmbH
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either expressed or implied.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 */

package de.gematik.demis.validationservice.util;

import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class LocaleUtil {

  private LocaleUtil() {}

  private static final Lock LOCALE_LOCK = new ReentrantLock();

  public static <T> T withDefaultLocale(Locale tempDefault, Supplier<T> actionWithDefaultLocale) {
    LOCALE_LOCK.lock();
    Locale oldDefault = Locale.getDefault();
    try {
      Locale.setDefault(tempDefault);
      return actionWithDefaultLocale.get();
    } finally {
      Locale.setDefault(oldDefault);
      LOCALE_LOCK.unlock();
    }
  }
}
