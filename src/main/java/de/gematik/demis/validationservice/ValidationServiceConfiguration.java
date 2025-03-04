package de.gematik.demis.validationservice;

/*-
 * #%L
 * validation-service
 * %%
 * Copyright (C) 2025 gematik GmbH
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
 * #L%
 */

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.validationservice.services.ProfileParserService;
import de.gematik.demis.validationservice.services.validation.FhirValidatorFactory;
import de.gematik.demis.validationservice.services.validation.FilteredMessagePrefixesFactory;
import de.gematik.demis.validationservice.services.validation.MinSeverityFactory;
import java.util.Locale;
import org.apache.commons.lang3.LocaleUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class ValidationServiceConfiguration {

  @Bean
  @Lazy
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }

  @Bean
  public Locale locale(@Value("${demis.validation-service.locale}") final String locale) {
    final var localization = LocaleUtils.toLocale(locale);
    Locale.setDefault(localization);
    return localization;
  }

  @Bean
  public FilteredMessagePrefixesFactory filteredMessagePrefixesFactory(final Locale locale) {
    return new FilteredMessagePrefixesFactory(locale);
  }

  @Bean
  public ProfileParserService profileParserService(
      final FhirContext fhirContext,
      @Value("${demis.validation-service.profileResourcePath}") final String profileResource) {
    return new ProfileParserService(fhirContext, profileResource);
  }

  @Bean
  public FhirValidatorFactory fhirValidatorFactory(
      final FhirContext fhirContext,
      final ProfileParserService profileParserService,
      @Value("${demis.validation-service.cache.expireAfterAccessMins}")
          final long cacheExpireAfterAccessTimeoutMins,
      @Value("${feature.flag.common.code.system.terminology.enabled}")
          final boolean isCommonCodeSystemTerminologyActive) {
    return new FhirValidatorFactory(
        profileParserService,
        fhirContext,
        cacheExpireAfterAccessTimeoutMins,
        isCommonCodeSystemTerminologyActive);
  }

  @Bean
  public MinSeverityFactory minSeverityFactory(
      @Value("${demis.validation-service.minSeverityOutcome}") final String minSeverityOutcome) {
    return new MinSeverityFactory(minSeverityOutcome);
  }
}
