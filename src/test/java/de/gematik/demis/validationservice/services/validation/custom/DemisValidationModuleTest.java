package de.gematik.demis.validationservice.services.validation.custom;

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

import static org.mockito.Mockito.*;

import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemisValidationModuleTest {
  private TestDemisValidationModuleImplementation module;
  @Mock private IValidationContext<IBaseResource> ctx;

  @BeforeEach
  void setUp() {
    module = new TestDemisValidationModuleImplementation(true);
  }

  @Nested
  class ValidateResourceTests {
    @Test
    void shouldDelegateBundleToValidateBundle() {
      Bundle bundle = createTestBundle();

      when(ctx.getResource()).thenReturn(bundle);

      module.validateResource(ctx);

      verify(ctx, never()).addValidationMessage(any());
    }

    @Test
    void shouldAllowParametersWithExactlyOneBundle() {
      Bundle bundle = createTestBundle();

      Parameters parameters = new Parameters();
      parameters.addParameter().setResource(bundle);

      when(ctx.getResource()).thenReturn(parameters);

      module.validateResource(ctx);

      verify(ctx, never()).addValidationMessage(any());
    }

    @Test
    void shouldThrowExceptionWhenParametersIsEmpty() {
      Parameters parameters = new Parameters();

      when(ctx.getResource()).thenReturn(parameters);

      module.validateResource(ctx);

      verify(ctx)
          .addValidationMessage(
              argThat(
                  msg ->
                      msg.getSeverity() == ResultSeverityEnum.ERROR
                          && "Parameters resource is empty, it must contain exactly one Bundle"
                              .equals(msg.getMessage())));
    }

    @Test
    void shouldThrowExceptionWhenParametersHasMultipleBundles() {
      Parameters parameters = new Parameters();
      parameters.addParameter().setResource(createTestBundle());
      parameters.addParameter().setResource(createTestBundle());

      when(ctx.getResource()).thenReturn(parameters);

      module.validateResource(ctx);

      verify(ctx)
          .addValidationMessage(
              argThat(
                  msg ->
                      msg.getSeverity() == ResultSeverityEnum.ERROR
                          && "Parameter contains multiple bundles, it must contain exactly one Bundle"
                              .equals(msg.getMessage())));
    }

    @Test
    void shouldThrowExceptionWhenGivenOtherResourceThanBundleOrParameters() {
      Patient patient = new Patient();
      patient.setId("test-patient-id");
      patient.addName().setFamily("Mustermann").addGiven("Max");

      when(ctx.getResource()).thenReturn(patient);

      module.validateResource(ctx);

      verify(ctx)
          .addValidationMessage(
              argThat(
                  msg ->
                      msg.getSeverity() == ResultSeverityEnum.ERROR
                          && "Invalid resource type, has to be Bundle or Parameters containing exactly one Bundle"
                              .equals(msg.getMessage())));
    }
  }

  @Nested
  class ValidationMessageHelperTests {

    @Test
    void shouldAddValidationErrorWithGivenText() {
      module.emitError(ctx, "Critical error!", "/");

      verify(ctx)
          .addValidationMessage(
              argThat(
                  msg ->
                      msg.getSeverity() == ResultSeverityEnum.ERROR
                          && "Critical error!".equals(msg.getMessage())));
    }

    @Test
    void shouldAddValidationWarningWithGivenText() {
      module.emitWarning(ctx, "Warning message!", "/");

      verify(ctx)
          .addValidationMessage(
              argThat(
                  msg ->
                      msg.getSeverity() == ResultSeverityEnum.WARNING
                          && "Warning message!".equals(msg.getMessage())));
    }

    @Test
    void shouldAddValidationInfoWithGivenText() {
      module.emitInfo(ctx, "Info message!", "/");

      verify(ctx)
          .addValidationMessage(
              argThat(
                  msg ->
                      msg.getSeverity() == ResultSeverityEnum.INFORMATION
                          && "Info message!".equals(msg.getMessage())));
    }
  }

  private Bundle createTestBundle() {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);

    return bundle;
  }

  private static class TestDemisValidationModuleImplementation extends DemisValidationModule {
    TestDemisValidationModuleImplementation(final boolean strictSingleBundleValidation) {
      super(strictSingleBundleValidation);
    }

    @Override
    protected void validateBundle(IValidationContext<IBaseResource> theCtx, Bundle bundle) {
      // no-op
    }

    void emitError(IValidationContext<IBaseResource> ctx, String text, String location) {
      addValidationError(ctx, text, location);
    }

    void emitWarning(IValidationContext<IBaseResource> ctx, String text, String location) {
      addValidationWarning(ctx, text, location);
    }

    void emitInfo(IValidationContext<IBaseResource> ctx, String text, String location) {
      addValidationInfo(ctx, text, location);
    }
  }
}
