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

package de.gematik.demis.validationservice.services;

import ca.uhn.fhir.context.FhirContext;
import java.util.Locale;
import org.apache.commons.lang3.LocaleUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service that holds the R4 FHIR context as singleton, so that there is only one in the
 * application.
 */
@Service
public class FhirContextService {
  private final FhirContext fhirContext;
  private final Locale configuredLocale;

  public FhirContextService(@Value("${demis.validation-service.locale}") String locale) {
    configuredLocale = LocaleUtils.toLocale(locale);
    Locale oldLocale = Locale.getDefault();
    Locale.setDefault(configuredLocale);
    fhirContext = FhirContext.forR4();
    Locale.setDefault(oldLocale); // Put back global default locale to avoid side effects
  }

  public FhirContext getFhirContext() {
    return fhirContext;
  }

  public Locale getConfiguredLocale() {
    return configuredLocale;
  }
}
