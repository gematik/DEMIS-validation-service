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

package de.gematik.demis.validationservice;

import static de.gematik.demis.validationservice.util.LocaleUtil.withDefaultLocale;

import ca.uhn.fhir.context.FhirContext;
import java.util.Locale;
import org.apache.commons.lang3.LocaleUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

  public static final String CONFIGURED_LOCALE_NAME = "demis.configuredLocale";

  @Bean
  public FhirContext createFhirContext(
      @Autowired @Qualifier(CONFIGURED_LOCALE_NAME) Locale configuredLocale) {
    return withDefaultLocale(configuredLocale, FhirContext::forR4);
  }

  @Bean(CONFIGURED_LOCALE_NAME)
  public Locale configuredLocale(@Value("${demis.validation-service.locale}") String locale) {
    return LocaleUtils.toLocale(locale);
  }
}
