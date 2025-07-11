package de.gematik.demis.validationservice.services.validation;

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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static de.gematik.demis.validationservice.util.FileTestUtil.readFileIntoString;
import static de.gematik.demis.validationservice.util.ResourceFileConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@Slf4j
class ValidationServiceIntegrationProfileFilteringResultsRegressionTest {

  public static final String THERE_ARE_ERRORS_OR_FATAL_ISSUES_IN_OUTCOME =
      "There are Errors or Fatal issues in outcome";

  private static List<OperationOutcomeIssueComponent> getErrorOrFatalIssues(
      final OperationOutcome operationOutcome) {
    return operationOutcome.getIssue().stream()
        .filter(
            issue ->
                issue.getSeverity() == IssueSeverity.ERROR
                    || issue.getSeverity() == IssueSeverity.FATAL)
        .toList();
  }

  private static void logOperationOutcome(final OperationOutcome operationOutcome) {
    operationOutcome
        .getIssue()
        .forEach(
            operationOutcomeIssueComponent ->
                log.info(
                    "Issue: {} - {}",
                    operationOutcomeIssueComponent.getSeverity(),
                    operationOutcomeIssueComponent.getDiagnostics()));
  }

  @Nested
  @SpringBootTest(
      webEnvironment = SpringBootTest.WebEnvironment.NONE,
      properties = {
        "feature.flag.filtered.validation.errors.disabled=false",
        "feature.flag.filtered.errors.as.warnings.disabled=true"
      })
  @ActiveProfiles("test")
  class ValidationWithDefaults {

    @Autowired private ValidationService validationService;

    @Test
    void validateValidFileAndCheckThereIsNoErrorOrFatal() throws IOException {
      final String validFileContent = readFileIntoString(VALID_REPORT_BED_OCCUPANCY_JSON_EXAMPLE);

      final OperationOutcome operationOutcome = validationService.validate(validFileContent);

      final var issues = getErrorOrFatalIssues(operationOutcome);

      assertThat(issues).as(THERE_ARE_ERRORS_OR_FATAL_ISSUES_IN_OUTCOME).isEmpty();
    }

    @Test
    void validateInvalidFileAndCheckThereIsOneErrorWithMissingAnswer() throws IOException {
      final String invalidFileContent = readFileIntoString(INVALID_REPORT_BED_OCCUPANCY_EXAMPLE);

      final OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

      final List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
          getErrorOrFatalIssues(operationOutcome);
      assertThat(errorsOrFatalIssues).isNotEmpty();
      final String diagnosticsOfOnlyError = errorsOrFatalIssues.getFirst().getDiagnostics();
      assertEquals(
          "No response answer found for required item 'numberOccupiedBedsGeneralWardChildren'",
          diagnosticsOfOnlyError);
    }

    @Test
    void expectNoValidFamilyNameErrorFoundInInvalidNotificationDV2() throws IOException {
      // GIVEN an invalid file with different invalid values
      final String invalidFileContent = readFileIntoString(INVALID_TEST_NOTIFICATION_DV_2);

      // WHEN it's validated
      final OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

      final List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
          getErrorOrFatalIssues(operationOutcome);

      // THEN at least "noNumbersInFamilyName" is found across one or multiple errors found
      System.out.println(errorsOrFatalIssues.getFirst().getDiagnostics());
      final List<String> diagnostics =
          errorsOrFatalIssues.stream().map(OperationOutcomeIssueComponent::getDiagnostics).toList();
      assertTrue(diagnostics.stream().anyMatch(s -> s.contains("validFamilyName")));
    }

    @Test
    void validateInvalidDv2FileWithFollowupsAndCheckThereIsSlicedMessageAndMultipleProfilesMessage()
        throws IOException {
      final String invalidFileContent = readFileIntoString(INVALID_TEST_NOTIFICATION_DV_2);

      final OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

      final List<String> diagnostics =
          operationOutcome.getIssue().stream()
              .map(OperationOutcomeIssueComponent::getDiagnostics)
              .toList();
      assertTrue(diagnostics.stream().anyMatch(s -> !s.contains("Multiple profiles")));
      assertTrue(diagnostics.stream().anyMatch(s -> !s.contains("known slice defined")));
    }

    @Test
    void validateNotParsableFileAndCheckThereAreTenErrorAndFatalIssues() throws IOException {
      final String validFileContent =
          readFileIntoString(NOT_PARSEABLE_REPORT_BED_OCCUPANCY_EXAMPLE);

      final OperationOutcome operationOutcome = validationService.validate(validFileContent);

      final var errorsOrFatalIssues = getErrorOrFatalIssues(operationOutcome);
      assertThat(errorsOrFatalIssues).isNotEmpty();
    }

    @Test
    void validateValidFileForErrorsAndCheckThereIsOnlyOneIssue() throws IOException {
      final String validFileContent = readFileIntoString(VALID_REPORT_BED_OCCUPANCY_JSON_EXAMPLE);

      final OperationOutcome operationOutcome = validationService.validate(validFileContent);

      logOperationOutcome(operationOutcome);

      assertThat(getErrorOrFatalIssues(operationOutcome)).isEmpty();
    }

    @Test
    void validateValidFileForErrorsAndCheckThereIsOnlyOneIssueREGRESSION() throws IOException {
      // TODO ff off bzw. on
      final String validFileContent = readFileIntoString(VALID_REPORT_BED_OCCUPANCY_JSON_EXAMPLE);

      final OperationOutcome operationOutcome = validationService.validate(validFileContent);

      assertThat(getErrorOrFatalIssues(operationOutcome)).isEmpty();
    }

    @Test
    void catchBugInHapiValidator() throws IOException {
      final String invalidFileContent = readFileIntoString(INVALID_PARAMETERS_META_EXCEPTION);

      final OperationOutcome result = validationService.validate(invalidFileContent);

      assertThat(result).isNotNull();
      assertThat(result.getIssue()).hasSize(1);
      assertThat(result.getIssueFirstRep())
          .returns(IssueSeverity.FATAL, OperationOutcomeIssueComponent::getSeverity)
          .returns(OperationOutcome.IssueType.EXCEPTION, OperationOutcomeIssueComponent::getCode);
    }

    @Test
    void validateNotificationsWithUnderscoresAllowed() throws IOException {
      final String validFileContent = readFileIntoString(VALID_BUNDLE_WITH_UNDERSCORES_EXAMPLE);

      final OperationOutcome operationOutcome = validationService.validate(validFileContent);
      logOperationOutcome(operationOutcome);

      assertThat(getErrorOrFatalIssues(operationOutcome)).isEmpty();
    }
  }

  @Nested
  @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
  @ActiveProfiles("test")
  @TestPropertySource(
      properties = {
        "demis.validation-service.locale=de_DE",
        "feature.flag.filtered.validation.errors.disabled=false"
      })
  class ValidationGerman {

    @Autowired private ValidationService validationService;

    @Test
    void
        setValidationServiceToGermanAndValidateInvalidFileAndCheckThereIsOneErrorWithAGermanAnswer()
            throws IOException {
      final String invalidFileContent = readFileIntoString(INVALID_REPORT_BED_OCCUPANCY_EXAMPLE);

      final OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

      final var errorsOrFatalIssues = getErrorOrFatalIssues(operationOutcome);
      assertThat(errorsOrFatalIssues).isNotEmpty();

      final String diagnosticsOfOnlyError = errorsOrFatalIssues.getFirst().getDiagnostics();
      assertEquals(
          "Keine Antwort für das erforderliche Element gefunden numberOccupiedBedsGeneralWardChildren",
          diagnosticsOfOnlyError);
    }
  }

  @Nested
  @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
  @ActiveProfiles("test")
  @TestPropertySource(
      properties = {
        "demis.validation-service.profiles.versions=6.0.6,5.3.1",
        "feature.flag.filtered.validation.errors.disabled=false",
        "feature.flag.filtered.errors.as.warnings.disabled=true"
      })
  class ValidationWithMultipleProfilesVersions {

    @Autowired private ValidationService validationService;

    @Test
    void invalidWithNewVersionButValidWithFallbackVersion() throws IOException {
      final String fhirContent = readFileIntoString(VALID_REPORT_BED_OCCUPANCY_JSON_EXAMPLE);

      final OperationOutcome operationOutcome = validationService.validate(fhirContent);
      logOperationOutcome(operationOutcome);

      final var errorsOrFatalIssues = getErrorOrFatalIssues(operationOutcome);
      assertThat(errorsOrFatalIssues).isEmpty();
      final var warnings =
          operationOutcome.getIssue().stream()
              .filter(issue -> issue.getSeverity() == IssueSeverity.WARNING)
              .toList();
      assertThat(warnings).hasSize(1);
    }

    @Test
    void validWithNew() throws IOException {
      final String fhirContent = readFileIntoString(VALID_REPORT_BED_OCCUPANCY_6_0_0);

      final OperationOutcome operationOutcome = validationService.validate(fhirContent);
      logOperationOutcome(operationOutcome);

      final var errorsOrFatalIssues = getErrorOrFatalIssues(operationOutcome);
      assertThat(errorsOrFatalIssues).isEmpty();
      final var warnings =
          operationOutcome.getIssue().stream()
              .filter(issue -> issue.getSeverity() == IssueSeverity.WARNING)
              .toList();
      assertThat(warnings).isEmpty();
    }

    @Test
    void invalidWithAllVersions() throws IOException {
      final String fhirContent = readFileIntoString(INVALID_REPORT_BED_OCCUPANCY_EXAMPLE);

      final OperationOutcome operationOutcome = validationService.validate(fhirContent);
      logOperationOutcome(operationOutcome);

      final var errorsOrFatalIssues = getErrorOrFatalIssues(operationOutcome);
      assertThat(errorsOrFatalIssues).hasSize(2);
    }
  }
}
