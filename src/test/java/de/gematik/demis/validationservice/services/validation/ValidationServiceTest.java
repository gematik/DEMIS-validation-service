package de.gematik.demis.validationservice.services.validation;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

  private static final String CONTENT = "Some Fhir bundle";
  private static final String VALID_INFO_MESSAGE = "No issues detected during validation";
  private static final String EXCEPTION_MESSAGE = "exception in validation";

  private final FhirContext fhirContext = FhirContext.forR4Cached();

  @Mock private FhirValidatorManager fhirValidatorManagerMock;
  @Mock private ValidationResultPostProcessing validationResultPostProcessingMock;

  private ValidationService underTest;

  @BeforeEach
  void setUp() {
    underTest = new ValidationService(fhirValidatorManagerMock, validationResultPostProcessingMock);
  }

  /** Creates a processed ValidationResult with no issues (isSuccessful() == true). */
  private ValidationResult successfulResult() {
    return new ValidationResult(fhirContext, List.of());
  }

  /**
   * Creates a processed ValidationResult containing a single ERROR message (isSuccessful() ==
   * false).
   */
  private ValidationResult failedResult(final String errorText) {
    final SingleValidationMessage msg = new SingleValidationMessage();
    msg.setSeverity(ResultSeverityEnum.ERROR);
    msg.setMessage(errorText);
    return new ValidationResult(fhirContext, List.of(msg));
  }

  private void assertOperationOutcome(
      final OperationOutcome result,
      final IssueSeverity expectedSeverity,
      final String expectedDiagnostics) {
    assertThat(result).isNotNull();
    assertThat(result.getIssue()).hasSize(1);
    final var issue = result.getIssue().getFirst();
    assertThat(issue.getSeverity()).isEqualTo(expectedSeverity);
    assertThat(issue.getDiagnostics()).isEqualTo(expectedDiagnostics);
  }

  @Nested
  class OneProfileVersion {

    private static final String PROFILES_VERSION = "1.0.0";

    private FhirValidator fhirValidatorMock;

    @BeforeEach
    void setUp() {
      when(fhirValidatorManagerMock.getVersions()).thenReturn(List.of(PROFILES_VERSION));
      fhirValidatorMock = mock(FhirValidator.class);
      when(fhirValidatorManagerMock.getFhirValidator(PROFILES_VERSION))
          .thenReturn(Optional.of(fhirValidatorMock));
    }

    @Test
    void valid() {
      // Given
      final ValidationResult rawResult = mock(ValidationResult.class);
      when(fhirValidatorMock.validateWithResult(CONTENT)).thenReturn(rawResult);
      when(validationResultPostProcessingMock.processValidationResult(
              rawResult, PROFILES_VERSION, null))
          .thenReturn(successfulResult());

      // When
      final OperationOutcome result = underTest.validate(CONTENT);

      // Then: outcome reflects the processed successful result
      assertOperationOutcome(result, IssueSeverity.INFORMATION, VALID_INFO_MESSAGE);
      verify(validationResultPostProcessingMock)
          .processValidationResult(rawResult, PROFILES_VERSION, null);
    }

    @Test
    void invalid() {
      // Given
      final String errorText = "some validation error";
      final ValidationResult rawResult = mock(ValidationResult.class);
      when(fhirValidatorMock.validateWithResult(CONTENT)).thenReturn(rawResult);
      when(validationResultPostProcessingMock.processValidationResult(
              rawResult, PROFILES_VERSION, null))
          .thenReturn(failedResult(errorText));

      // When
      final OperationOutcome result = underTest.validate(CONTENT);

      // Then
      assertOperationOutcome(result, IssueSeverity.ERROR, errorText);
    }

    @Test
    void principalIdIsForwardedToPostProcessing() {
      // Given
      final String principalId = "sender-abc-123";
      final ValidationResult rawResult = mock(ValidationResult.class);
      when(fhirValidatorMock.validateWithResult(CONTENT)).thenReturn(rawResult);
      when(validationResultPostProcessingMock.processValidationResult(
              rawResult, PROFILES_VERSION, principalId))
          .thenReturn(successfulResult());

      // When
      underTest.validate(CONTENT, principalId);

      // Then: the principalId is passed through unmodified to post-processing
      verify(validationResultPostProcessingMock)
          .processValidationResult(rawResult, PROFILES_VERSION, principalId);
    }

    @Test
    void exceptionInHapiFhirValidator() {
      // Given
      when(fhirValidatorMock.validateWithResult(CONTENT))
          .thenThrow(new NullPointerException("just for testing"));

      // When
      final OperationOutcome result = underTest.validate(CONTENT);

      // Then: exception is caught and a FATAL outcome is returned; post-processing is never called
      assertOperationOutcome(result, IssueSeverity.FATAL, EXCEPTION_MESSAGE);
      verify(validationResultPostProcessingMock, never())
          .processValidationResult(any(), any(), any());
    }
  }

  @Nested
  class TwoProfileVersions {

    private static final String NEW_VERSION = "2.0.0";
    private static final String OLD_VERSION = "1.0.0";

    private FhirValidator newValidatorMock;
    private FhirValidator oldValidatorMock;

    @BeforeEach
    void setUp() {
      when(fhirValidatorManagerMock.getVersions()).thenReturn(List.of(NEW_VERSION, OLD_VERSION));

      newValidatorMock = mock(FhirValidator.class);
      when(fhirValidatorManagerMock.getFhirValidator(NEW_VERSION))
          .thenReturn(Optional.of(newValidatorMock));

      // oldValidatorMock is created here but getFhirValidator(OLD_VERSION) is only stubbed in
      // tests that actually fall through to the older version (strict stubbing enforcement).
      oldValidatorMock = mock(FhirValidator.class);
    }

    @Test
    void validWithNewestVersion() {
      // Given
      final ValidationResult newRawResult = mock(ValidationResult.class);
      when(newValidatorMock.validateWithResult(CONTENT)).thenReturn(newRawResult);
      when(validationResultPostProcessingMock.processValidationResult(
              newRawResult, NEW_VERSION, null))
          .thenReturn(successfulResult());

      // When
      final OperationOutcome result = underTest.validate(CONTENT);

      // Then: validation stops after the first (newest) version – the older validator is never used
      assertOperationOutcome(result, IssueSeverity.INFORMATION, VALID_INFO_MESSAGE);
      verifyNoInteractions(oldValidatorMock);
      verify(validationResultPostProcessingMock, never())
          .processValidationResult(any(), eq(OLD_VERSION), any());
    }

    @Test
    void invalidWithNewestVersionButValidWithOlderVersion() {
      // Given
      final String newestVersionError = "some validation error from newest version";

      final ValidationResult newRawResult = mock(ValidationResult.class);
      when(newValidatorMock.validateWithResult(CONTENT)).thenReturn(newRawResult);
      when(validationResultPostProcessingMock.processValidationResult(
              newRawResult, NEW_VERSION, null))
          .thenReturn(failedResult(newestVersionError));

      when(fhirValidatorManagerMock.getFhirValidator(OLD_VERSION))
          .thenReturn(Optional.of(oldValidatorMock));
      final ValidationResult oldRawResult = mock(ValidationResult.class);
      when(oldValidatorMock.validateWithResult(CONTENT)).thenReturn(oldRawResult);
      when(validationResultPostProcessingMock.processValidationResult(
              oldRawResult, OLD_VERSION, null))
          .thenReturn(successfulResult());

      // When
      final OperationOutcome result = underTest.validate(CONTENT);

      // Then: outcome is based on the NEWEST version's result (errors from that version are
      // reported), but ERROR severity is downgraded to WARNING because the older version validated
      // successfully
      assertOperationOutcome(result, IssueSeverity.WARNING, newestVersionError);
    }

    @Test
    void invalidWithAllVersions() {
      // Given
      final String newestVersionError = "some validation error from newest version";

      final ValidationResult newRawResult = mock(ValidationResult.class);
      when(newValidatorMock.validateWithResult(CONTENT)).thenReturn(newRawResult);
      when(validationResultPostProcessingMock.processValidationResult(
              newRawResult, NEW_VERSION, null))
          .thenReturn(failedResult(newestVersionError));

      when(fhirValidatorManagerMock.getFhirValidator(OLD_VERSION))
          .thenReturn(Optional.of(oldValidatorMock));
      final ValidationResult oldRawResult = mock(ValidationResult.class);
      when(oldValidatorMock.validateWithResult(CONTENT)).thenReturn(oldRawResult);
      when(validationResultPostProcessingMock.processValidationResult(
              oldRawResult, OLD_VERSION, null))
          .thenReturn(failedResult("some error in older version too"));

      // When
      final OperationOutcome result = underTest.validate(CONTENT);

      // Then: outcome is based on the NEWEST version's result; ERROR severity is NOT downgraded
      // because no version validated successfully
      assertOperationOutcome(result, IssueSeverity.ERROR, newestVersionError);
    }
  }
}
