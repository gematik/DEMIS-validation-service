package de.gematik.demis.validationservice.services.validation.custom.referencesolving;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.IValidationContext;
import de.gematik.demis.validationservice.services.validation.custom.resourcereferences.ResourceReferencesValidationModule;
import de.gematik.demis.validationservice.util.FileTestUtil;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceReferencesValidationModuleTest {

  private static final String INPUT_FILE =
      "src/test/resources/integrationtests/customValidators/input/strictCodingCheckExample.json";
  private static final String INPUT_FILE_WITH_PARAMETERS =
      "src/test/resources/integrationtests/customValidators/input/strictCodingCheckExample-Parameters.json";
  private static final String VALID_SPECIMEN_REFERENCE =
      "Specimen/4016fbc2-2439-3f6d-8935-0eb71c3d487c";

  private final FhirContext fhirContext = FhirContext.forR4();

  @Mock private IValidationContext<IBaseResource> validationContext;

  private ResourceReferencesValidationModule validator;
  private String bundleNotification;
  private String parametersNotification;

  @BeforeEach
  void setUp() throws Exception {
    boolean featureFlagStrictSingleBundleValidation = false;
    validator = new ResourceReferencesValidationModule(featureFlagStrictSingleBundleValidation);
    bundleNotification = FileTestUtil.readFileIntoString(INPUT_FILE);
    parametersNotification = FileTestUtil.readFileIntoString(INPUT_FILE_WITH_PARAMETERS);
  }

  @Nested
  class ExternalReferences {

    @Test
    void shouldIgnoreExternalHttpReference() {
      validate(
          parseBundle(
              bundleNotification.replace(
                  VALID_SPECIMEN_REFERENCE, "http://example.com/Patient/123")));

      verify(validationContext, never()).addValidationMessage(any());
    }

    @Test
    void shouldIgnoreExternalHttpsReference() {
      validate(
          parseBundle(
              bundleNotification.replace(
                  VALID_SPECIMEN_REFERENCE, "https://example.com/Patient/123")));

      verify(validationContext, never()).addValidationMessage(any());
    }
  }

  @Nested
  class InternalReferences {

    @Test
    void shouldReportUnresolvableInternalReference() {
      validate(
          parseBundle(
              bundleNotification.replace(VALID_SPECIMEN_REFERENCE, "Patient/wrong-reference")));

      verify(validationContext)
          .addValidationMessage(
              argThat(
                  msg ->
                      msg.getSeverity() == ca.uhn.fhir.validation.ResultSeverityEnum.ERROR
                          && "Reference 'Patient/wrong-reference' is not resolvable"
                              .equals(msg.getMessage())));
    }
  }

  @Nested
  class ParametersResources {

    @Test
    void shouldResolveReferenceInParametersResource() {
      validate(fhirContext.newJsonParser().parseResource(Parameters.class, parametersNotification));

      verify(validationContext, never()).addValidationMessage(any());
    }
  }

  private Bundle parseBundle(final String notification) {
    return fhirContext.newJsonParser().parseResource(Bundle.class, notification);
  }

  private void validate(final Resource resource) {
    when(validationContext.getResource()).thenReturn(resource);
    validator.validateResource(validationContext);
  }
}
