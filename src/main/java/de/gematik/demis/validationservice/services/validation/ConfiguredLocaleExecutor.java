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

package de.gematik.demis.validationservice.services.validation;

import ca.uhn.fhir.context.FhirContext;
import java.util.Locale;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;

/**
 * Executes immediately with configured locale set as VM default locale. Restores machine default
 * locale after execution.
 */
@RequiredArgsConstructor
@Slf4j
final class ConfiguredLocaleExecutor implements Executor {

  /**
   * Get locale from configured input text value
   *
   * @param locale configured locale as text
   * @return locale
   */
  static Locale getLocale(String locale) {
    return LocaleUtils.toLocale(locale);
  }

  private final FhirContext fhirContext;
  private final Locale configuredLocale;

  /**
   * Execute runnable with configured locale set as VM default locale.
   *
   * @param runnable task to run under configured locale
   */
  @Override
  public void execute(Runnable runnable) {
    final Locale defaultLocale = activateConfiguredLocale();
    runnable.run();
    activateDefaultLocale(defaultLocale);
  }

  private Locale activateConfiguredLocale() {
    final Locale defaultLocale = Locale.getDefault();
    Locale.setDefault(this.configuredLocale);
    log.info(
        "Locale for validation messages: %s Configured: %s Default: %s"
            .formatted(
                this.fhirContext.getLocalizer().getLocale(), this.configuredLocale, defaultLocale));
    return defaultLocale;
  }

  private void activateDefaultLocale(Locale defaultLocale) {
    Locale.setDefault(defaultLocale); // Put back global default locale to avoid side effects
  }
}
