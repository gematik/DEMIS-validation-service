package de.gematik.demis.validationservice.config;

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

import lombok.Builder;
import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Value
@Builder
@ConfigurationProperties(prefix = "feature.flag")
public class FeatureFlags {
  boolean codesystemVersionVerificationEnabled;
  boolean filteredErrorsAsWarningsDisabled;

  /**
   * enable HAPI-native verification of lower limits of quantity values in questionnaire responses
   */
  boolean vsHapiQrQminVerificationEnabled;

  boolean validationExtensionCheckEnabled;
  boolean denyModifierExtensions;
  boolean formatValidationEnabled;
  boolean addressCountryErrorsAsWarningsDisabled;
  boolean addressStateErrorsAsWarningsDisabled;
  boolean addressZipErrorsAsWarningsDisabled;
  boolean vsReferenceValidation;
  boolean abstractProfileValidation;
  boolean referenceProfileValidation;
  boolean fhirPackagePostprocessing;

  /**
   * Remove when <a href="https://github.com/hapifhir/org.hl7.fhir.core/issues/2876">Issue 2876</a>
   * is fixed or no DEMIS FHIR profile uses <code>min=1</code> on the primitive Observation string
   * value. Right now this is only IGS.
   */
  boolean hl7Issue2876Fixed;
}
