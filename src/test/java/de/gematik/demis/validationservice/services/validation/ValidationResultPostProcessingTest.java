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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import de.gematik.demis.validationservice.config.FeatureFlags;
import de.gematik.demis.validationservice.config.ValidationConfigProperties;
import de.gematik.demis.validationservice.services.ValidationMetrics;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidationResultPostProcessingTest {

  private static final String PROFILE_VERSION = "6.2.6";

  private final FhirContext fhirContext = FhirContext.forR4Cached();

  @Mock private ValidationResult rawValidationResult;
  @Mock private ValidationConfigProperties configProperties;
  @Mock private ValidationMetrics validationMetrics;
  @Mock private FeatureFlags featureFlags;
  private ValidationResultPostProcessing validationResultPostProcessing;

  private static SingleValidationMessage errorMessage(
      String messageId, String pattern, Object... args) {
    SingleValidationMessage message = new SingleValidationMessage();
    message.setSeverity(ResultSeverityEnum.ERROR);
    message.setMessageId(messageId);
    message.setMessage(MessageFormat.format(pattern, args));
    return message;
  }

  private static SingleValidationMessage errorMessageCantMatchChoice() {
    return errorMessage(
        ValidationResultPostProcessing.REDUCE_REF_CANT_MATCH_CHOICE,
        "Unable to find a profile match for {0} among choices: {1}",
        "http://example.org/some-ref",
        "[http://hl7.org/fhir/StructureDefinition/Patient]");
  }

  private static SingleValidationMessage addressUnknownCodeError(
      String diagnostics, String location) {
    SingleValidationMessage message = new SingleValidationMessage();
    message.setSeverity(ResultSeverityEnum.ERROR);
    message.setMessage(diagnostics);
    message.setLocationString(location);
    return message;
  }

  @BeforeEach
  void createValidationResultPostProcessing() {
    when(configProperties.locale()).thenReturn(Locale.of("en_US"));
    this.validationResultPostProcessing =
        new ValidationResultPostProcessing(
            this.fhirContext, this.configProperties, this.validationMetrics, this.featureFlags);
    validationResultPostProcessing.createDeletedMessagesMarkers();
    validationResultPostProcessing.createReducedMessagesMarkers();
  }

  @Nested
  class LogLevelReducedErrorMessages {

    @Test
    void filteredErrorMessagesAreDowngradedToWarning() {

      // Given
      SingleValidationMessage referenceCantMatchChoice = errorMessageCantMatchChoice();

      SingleValidationMessage profileNoMatch =
          errorMessage(
              ValidationResultPostProcessing.REDUCE_PROFILE_NO_MATCH,
              "Unable to find a match for the specified profile among choices: {0}",
              "[http://hl7.org/fhir/StructureDefinition/Patient]");

      SingleValidationMessage elementNoKnownSlice =
          errorMessage(
              ValidationResultPostProcessing.REDUCE_NOT_ANY_KNOWN_SLICE,
              "This element does not match any known slice {0} (this may not be a problem, but you should check that it''s not intended to match a slice)",
              "defined for the type Patient");

      SingleValidationMessage multipleProfilesMatch =
          errorMessage(
              ValidationResultPostProcessing.REDUCE_MULTIPLE_PROFILES,
              "The {1} resource matched more than one of the allowed profiles ({3})",
              null,
              "Patient",
              null,
              3);

      when(rawValidationResult.getMessages())
          .thenReturn(
              List.of(
                  referenceCantMatchChoice,
                  profileNoMatch,
                  elementNoKnownSlice,
                  multipleProfilesMatch));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);

      // When
      ValidationResult result =
          validationResultPostProcessing.processValidationResult(
              rawValidationResult, PROFILE_VERSION, null);

      // Then
      assertThat(result).as("result has been cloned").isNotSameAs(rawValidationResult);
      final List<SingleValidationMessage> messages = result.getMessages();
      final List<SingleValidationMessage> inputMessages = rawValidationResult.getMessages();
      assertThat(messages).as("messages list has been cloned").isNotSameAs(inputMessages);
      inputMessages.forEach(
          inputMessage ->
              assertThat(messages)
                  .as("message has been cloned")
                  .noneMatch(message -> message == inputMessage));
      assertThat(messages)
          .as("all four error message have been reduced to severity warn")
          .hasSize(4)
          .extracting(SingleValidationMessage::getSeverity)
          .containsOnly(ResultSeverityEnum.WARNING);
    }

    @Test
    void filteredErrorMessagesCanBeDisabled() {

      // Given
      SingleValidationMessage referenceCantMatchChoice = errorMessageCantMatchChoice();
      when(rawValidationResult.getMessages()).thenReturn(List.of(referenceCantMatchChoice));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);
      when(featureFlags.isFilteredErrorsAsWarningsDisabled()).thenReturn(true);

      // When
      ValidationResult result =
          validationResultPostProcessing.processValidationResult(
              rawValidationResult, PROFILE_VERSION, null);

      // Then
      assertThat(result.getMessages())
          .hasSize(1)
          .extracting(SingleValidationMessage::getSeverity)
          .containsOnly(ResultSeverityEnum.ERROR);
    }
  }

  @Nested
  class MinSeverityOutcome {
    @Test
    void minSeverityWarnSuppressesInfoMessage() {

      // Given
      SingleValidationMessage infoMessage = errorMessageCantMatchChoice();
      infoMessage.setSeverity(ResultSeverityEnum.INFORMATION);
      when(rawValidationResult.getMessages()).thenReturn(List.of(infoMessage));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);

      // When
      ValidationResult result =
          validationResultPostProcessing.processValidationResult(
              rawValidationResult, PROFILE_VERSION, null);

      // Then
      assertThat(result.getMessages()).isEmpty();
    }
  }

  @Nested
  class MetricsIncValidationCount {

    @Test
    void calledWithSuccessTrueWhenFilteredErrorsAreDowngradedToWarning() {
      // Given: the only error matches the filter → downgraded to WARNING → result is successful
      when(rawValidationResult.getMessages()).thenReturn(List.of(errorMessageCantMatchChoice()));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);

      // When
      validationResultPostProcessing.processValidationResult(
          rawValidationResult, PROFILE_VERSION, null);

      // Then
      verify(validationMetrics).incValidationCount(PROFILE_VERSION, true);
    }

    @Test
    void calledWithSuccessFalseWhenUnfilteredErrorRemains() {
      // Given: error does NOT match any filtered prefix → stays ERROR → result is not successful
      SingleValidationMessage unfilteredError =
          errorMessage("SOME_OTHER_KEY", "Some completely unrelated error");
      when(rawValidationResult.getMessages()).thenReturn(List.of(unfilteredError));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);

      // When
      validationResultPostProcessing.processValidationResult(
          rawValidationResult, PROFILE_VERSION, null);

      // Then
      verify(validationMetrics).incValidationCount(PROFILE_VERSION, false);
    }

    @Test
    void calledEvenWhenFeatureFlagDisablesFiltering() {
      // Given: feature flag disables filtering → error stays ERROR → result is not successful
      when(rawValidationResult.getMessages()).thenReturn(List.of(errorMessageCantMatchChoice()));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);
      when(featureFlags.isFilteredErrorsAsWarningsDisabled()).thenReturn(true);

      // When
      validationResultPostProcessing.processValidationResult(
          rawValidationResult, PROFILE_VERSION, null);

      // Then: incValidationCount is always called, independent of the feature flag
      verify(validationMetrics).incValidationCount(PROFILE_VERSION, false);
    }
  }

  @Nested
  class MetricsSaveValidationFindings {

    @Test
    void calledWithUnknownFallbackWhenPrincipalIdIsNull() {
      // Given
      when(rawValidationResult.getMessages()).thenReturn(List.of(errorMessageCantMatchChoice()));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);

      // When
      validationResultPostProcessing.processValidationResult(
          rawValidationResult, PROFILE_VERSION, null);

      // Then: null principalId is replaced by the literal "<unknown>"
      verify(validationMetrics)
          .saveValidationFindings(
              "<unknown>",
              PROFILE_VERSION,
              List.of(ValidationResultPostProcessing.REDUCE_REF_CANT_MATCH_CHOICE));
    }

    @Test
    void calledWithActualPrincipalIdWhenProvided() {
      // Given
      String principalId = "sender-abc-123";
      when(rawValidationResult.getMessages()).thenReturn(List.of(errorMessageCantMatchChoice()));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);

      // When
      validationResultPostProcessing.processValidationResult(
          rawValidationResult, PROFILE_VERSION, principalId);

      // Then
      verify(validationMetrics)
          .saveValidationFindings(
              "sender-abc-123",
              PROFILE_VERSION,
              List.of(ValidationResultPostProcessing.REDUCE_REF_CANT_MATCH_CHOICE));
    }

    @Test
    void notCalledWhenFeatureFlagDisablesFiltering() {
      // Given
      when(rawValidationResult.getMessages()).thenReturn(List.of(errorMessageCantMatchChoice()));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);
      when(featureFlags.isFilteredErrorsAsWarningsDisabled()).thenReturn(true);

      // When
      validationResultPostProcessing.processValidationResult(
          rawValidationResult, PROFILE_VERSION, null);

      // Then
      verify(validationMetrics, never()).saveValidationFindings(any(), any(), any());
    }

    @Test
    void calledWithEmptyFindingsListWhenNoErrorMatchesFilter() {
      // Given: an error whose message does not start with any filtered prefix
      SingleValidationMessage unfilteredError =
          errorMessage("SOME_OTHER_KEY", "Some completely unrelated error");
      when(rawValidationResult.getMessages()).thenReturn(List.of(unfilteredError));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);

      // When
      validationResultPostProcessing.processValidationResult(
          rawValidationResult, PROFILE_VERSION, null);

      // Then: saveValidationFindings is still called, but with an empty findings list
      verify(validationMetrics).saveValidationFindings("<unknown>", PROFILE_VERSION, List.of());
    }

    @Test
    void blankMessageIdIsExcludedFromFindings() {
      // Given: a filtered error (message text matches the prefix) but messageId is null/blank
      SingleValidationMessage filteredErrorWithoutMessageId =
          errorMessage(
              null,
              "Unable to find a profile match for {0} among choices: {1}",
              "http://example.org/some-ref",
              "[http://hl7.org/fhir/StructureDefinition/Patient]");
      when(rawValidationResult.getMessages()).thenReturn(List.of(filteredErrorWithoutMessageId));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);

      // When
      validationResultPostProcessing.processValidationResult(
          rawValidationResult, PROFILE_VERSION, null);

      // Then: the null messageId is excluded by StringUtils::isNotBlank → empty findings list
      verify(validationMetrics).saveValidationFindings("<unknown>", PROFILE_VERSION, List.of());
    }
  }

  @Nested
  class DeletedMessages {

    private static final String PATTERN_QR_QUANTITY_MIN =
        "The quantity {0} is less than the allowed minimum of {1}";

    @Test
    void questionnaireQuantityMinMessageIsDeleted() {
      // Given
      final var buggyMessage =
          errorMessage(
              ValidationResultPostProcessing.DELETE_QR_QUANTITY_MIN, PATTERN_QR_QUANTITY_MIN, 7, 3);
      when(rawValidationResult.getMessages()).thenReturn(List.of(buggyMessage));

      // When
      final ValidationResult processedValidationResult =
          validationResultPostProcessing.processValidationResult(
              rawValidationResult, PROFILE_VERSION, null);

      // Then
      assertThat(processedValidationResult).isNotNull();
      assertThat(processedValidationResult.isSuccessful()).isTrue();
      assertThat(processedValidationResult.getMessages()).isEmpty();
    }

    @Test
    void questionnaireQuantityMinMessageIsPassingOnEnabledFeatureFlag() {

      // Given
      when(featureFlags.isVsHapiQrQminVerificationEnabled()).thenReturn(true);
      validationResultPostProcessing.createDeletedMessagesMarkers();

      final var message =
          errorMessage(
              ValidationResultPostProcessing.DELETE_QR_QUANTITY_MIN, PATTERN_QR_QUANTITY_MIN, 3, 7);
      when(rawValidationResult.getMessages()).thenReturn(List.of(message));

      // When
      final ValidationResult processedValidationResult =
          validationResultPostProcessing.processValidationResult(
              rawValidationResult, PROFILE_VERSION, null);

      // Then
      assertThat(processedValidationResult).isNotNull();
      assertThat(processedValidationResult.isSuccessful()).isFalse();
      assertThat(processedValidationResult.getMessages())
          .hasSize(1)
          .extracting(SingleValidationMessage::getMessage)
          .containsExactly("The quantity 3 is less than the allowed minimum of 7");
    }
  }

  @Nested
  class AddressUnknownCodeReduction {

    @Test
    void addressStateUnknownIsoCodeIsDowngradedToWarning() {
      // Given
      final var stateUnknownCode =
          addressUnknownCodeError(
              "Unknown code 'urn:iso:std:iso:3166-2:de#Bayern'",
              "Bundle.entry[3].resource/*Organization/1db1a12d-27c3-48ab-a15e-f20ab6a580a5*/.address[0].state");
      when(rawValidationResult.getMessages()).thenReturn(List.of(stateUnknownCode));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);

      // When
      final ValidationResult processedValidationResult =
          validationResultPostProcessing.processValidationResult(
              rawValidationResult, PROFILE_VERSION, null);

      // Then
      assertThat(processedValidationResult.getMessages())
          .hasSize(1)
          .extracting(SingleValidationMessage::getSeverity)
          .containsExactly(ResultSeverityEnum.WARNING);
    }

    @Test
    void addressStateUnknownIsoCodeRemainsErrorWhenFeatureFlagIsDisabled() {
      // Given
      final var stateUnknownCode =
          addressUnknownCodeError(
              "Unknown code 'urn:iso:std:iso:3166-2:de#Bayern'",
              "Bundle.entry[3].resource/*Organization/1db1a12d-27c3-48ab-a15e-f20ab6a580a5*/.address[0].state");
      when(rawValidationResult.getMessages()).thenReturn(List.of(stateUnknownCode));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);
      when(featureFlags.isAddressStateErrorsAsWarningsDisabled()).thenReturn(true);

      // When
      final ValidationResult processedValidationResult =
          validationResultPostProcessing.processValidationResult(
              rawValidationResult, PROFILE_VERSION, null);

      // Then
      assertThat(processedValidationResult.getMessages())
          .hasSize(1)
          .extracting(SingleValidationMessage::getSeverity)
          .containsExactly(ResultSeverityEnum.ERROR);
    }

    @Test
    void addressPostalCodeUnknownCodeIsDowngradedToWarning() {
      // Given
      final var postalCodeUnknownCode =
          addressUnknownCodeError(
              "Unknown code 'https://demis.rki.de/fhir/CodeSystem/postalCode|1.1.0#A65F4E2'",
              "Bundle.entry[1].resource/*Patient/0f272475-9fed-31f7-b837-ecf1fb1dd252*/.address[1].postalCode");
      when(rawValidationResult.getMessages()).thenReturn(List.of(postalCodeUnknownCode));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);

      // When
      final ValidationResult processedValidationResult =
          validationResultPostProcessing.processValidationResult(
              rawValidationResult, PROFILE_VERSION, null);

      // Then
      assertThat(processedValidationResult.getMessages())
          .hasSize(1)
          .extracting(SingleValidationMessage::getSeverity)
          .containsExactly(ResultSeverityEnum.WARNING);
    }

    @Test
    void addressPostalCodeUnknownCodeRemainsErrorWhenFeatureFlagIsDisabled() {
      // Given
      final var postalCodeUnknownCode =
          addressUnknownCodeError(
              "Unknown code 'https://demis.rki.de/fhir/CodeSystem/postalCode|1.1.0#A65F4E2'",
              "Bundle.entry[1].resource/*Patient/0f272475-9fed-31f7-b837-ecf1fb1dd252*/.address[1].postalCode");
      when(rawValidationResult.getMessages()).thenReturn(List.of(postalCodeUnknownCode));
      when(configProperties.minSeverityOutcome()).thenReturn(ResultSeverityEnum.WARNING);
      when(featureFlags.isAddressZipErrorsAsWarningsDisabled()).thenReturn(true);

      // When
      final ValidationResult processedValidationResult =
          validationResultPostProcessing.processValidationResult(
              rawValidationResult, PROFILE_VERSION, null);

      // Then
      assertThat(processedValidationResult.getMessages())
          .hasSize(1)
          .extracting(SingleValidationMessage::getSeverity)
          .containsExactly(ResultSeverityEnum.ERROR);
    }
  }
}
