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
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.validationservice.services.ProfileParserService;
import de.gematik.demis.validationservice.util.FileTestUtil;
import de.gematik.demis.validationservice.util.ResourceFileConstants;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@Slf4j
@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {
  private static final String SEVERITY_ERROR = "error";
  private static final String DEFAULT_LOCALE_STRING = "en_US";
  private static final String GERMAN_LOCALE_STRING = "de_DE";
  private final FhirContext fhirContext = FhirContext.forR4();
  private final Locale defaultLocale = Locale.getDefault();
  private final ProfileParserService profileParserService =
      new ProfileParserService(fhirContext, ResourceFileConstants.PROFILE_RESOURCE_PATH);
  private ValidationService validationService;

  private static List<OperationOutcomeIssueComponent> getErrorOrFatalIssue(
      final OperationOutcome operationOutcome) {
    return operationOutcome.getIssue().stream()
        .filter(
            issue ->
                issue.getSeverity() == IssueSeverity.ERROR
                    || issue.getSeverity() == IssueSeverity.FATAL)
        .toList();
  }

  @BeforeEach
  void setupValidationService() {
    initializeValidationService(DEFAULT_LOCALE_STRING, "information", true);
  }

  @AfterEach
  void tearDown() {
    // reset global locale to avoid side effects
    Locale.setDefault(defaultLocale);
  }

  @Test
  void validateValidFileAndCheckThereIsNoErrorOrFatal() throws IOException {
    final String validFileContent =
        FileTestUtil.readFileIntoString(
            ResourceFileConstants.VALID_REPORT_BED_OCCUPANCY_JSON_EXAMPLE);

    final OperationOutcome operationOutcome = validationService.validate(validFileContent);

    final var issues =
        operationOutcome.getIssue().stream()
            .filter(
                issue ->
                    !issue.getSeverity().equals(IssueSeverity.INFORMATION)
                        && !issue.getSeverity().equals(IssueSeverity.WARNING));
    assertThat(issues)
        .as("There should be nothing else than information in the issue list")
        .isEmpty();

    final List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        ValidationServiceTest.getErrorOrFatalIssue(operationOutcome);
    assertThat(errorsOrFatalIssues).as("There are Errors or Fatal issues in outcome").isEmpty();
  }

  @Test
  void validateInvalidFileAndCheckThereIsOneErrorWithMissingAnswer() throws IOException {
    final String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_REPORT_BED_OCCUPANCY_EXAMPLE);

    final OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

    final List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        ValidationServiceTest.getErrorOrFatalIssue(operationOutcome);
    assertFalse(errorsOrFatalIssues.isEmpty());
    final String diagnosticsOfOnlyError = errorsOrFatalIssues.getFirst().getDiagnostics();
    assertEquals(
        "No response answer found for required item 'numberOccupiedBedsGeneralWardChildren'",
        diagnosticsOfOnlyError);
  }

  @Test
  void expectNoValidFamilyNameErrorFoundInInvalidNotificationDV2() throws IOException {

    // GIVEN an invalid file with different invalid values
    final String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_TEST_NOTIFICATION_DV_2);

    // WHEN it's validated
    final OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

    final List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        ValidationServiceTest.getErrorOrFatalIssue(operationOutcome);

    // THEN at least "noNumbersInFamilyName is found across one or multiple errors found
    System.out.println(errorsOrFatalIssues.getFirst().getDiagnostics());
    final List<String> diagnostics =
        errorsOrFatalIssues.stream().map(OperationOutcomeIssueComponent::getDiagnostics).toList();
    assertTrue(diagnostics.stream().anyMatch(s -> s.contains("validFamilyName")));
  }

  @Test
  void validateInvalidDv2FileWithFollowupsAndCheckThereIsSlicedMessageAndMultipleProfilesMessage()
      throws IOException {
    final String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_TEST_NOTIFICATION_DV_2);

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
        FileTestUtil.readFileIntoString(
            ResourceFileConstants.NOT_PARSEABLE_REPORT_BED_OCCUPANCY_EXAMPLE);

    final OperationOutcome operationOutcome = validationService.validate(validFileContent);

    final List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        ValidationServiceTest.getErrorOrFatalIssue(operationOutcome);
    assertFalse(errorsOrFatalIssues.isEmpty());
  }

  @Test
  void validateValidFileForErrorsAndCheckThereIsOnlyOneIssue() throws IOException {
    initializeValidationService(DEFAULT_LOCALE_STRING, "error", true);

    final String validFileContent =
        FileTestUtil.readFileIntoString(
            ResourceFileConstants.VALID_REPORT_BED_OCCUPANCY_JSON_EXAMPLE);

    final OperationOutcome operationOutcome = validationService.validate(validFileContent);

    operationOutcome
        .getIssue()
        .forEach(
            operationOutcomeIssueComponent ->
                log.info(operationOutcomeIssueComponent.getDiagnostics()));

    assertThat(operationOutcome.getIssue()).as("There still more issues in outcome").hasSize(1);
  }

  @Test
  void validateValidFileForErrorsAndCheckThereIsOnlyOneIssueREGRESSION() throws IOException {
    initializeValidationService(DEFAULT_LOCALE_STRING, SEVERITY_ERROR, false);
    final String validFileContent =
        FileTestUtil.readFileIntoString(
            ResourceFileConstants.VALID_REPORT_BED_OCCUPANCY_JSON_EXAMPLE);

    final OperationOutcome operationOutcome = validationService.validate(validFileContent);

    operationOutcome
        .getIssue()
        .forEach(
            operationOutcomeIssueComponent ->
                log.info(operationOutcomeIssueComponent.getDiagnostics()));

    final long issueCount = operationOutcome.getIssue().size();
    assertEquals(1, issueCount, "Too many issues in outcome");
  }

  @Test
  void setValidationServiceToGermanAndValidateInvalidFileAndCheckThereIsOneErrorWithAGermanAnswer()
      throws IOException {
    initializeValidationService(GERMAN_LOCALE_STRING, SEVERITY_ERROR, true);

    final String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_REPORT_BED_OCCUPANCY_EXAMPLE);

    final OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

    final Locale parsedLocale = LocaleUtils.toLocale(DEFAULT_LOCALE_STRING);
    Locale.setDefault(parsedLocale); // Set back global locale
    final List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        ValidationServiceTest.getErrorOrFatalIssue(operationOutcome);
    assertFalse(errorsOrFatalIssues.isEmpty());
    final String diagnosticsOfOnlyError = errorsOrFatalIssues.getFirst().getDiagnostics();
    assertEquals(
        "Keine Antwort für das erforderliche Element gefunden numberOccupiedBedsGeneralWardChildren",
        diagnosticsOfOnlyError);
  }

  @Test
  void validateInvalidFileAndCheckThereIsOneErrorWithAGermanAnswerREGRESSION() throws IOException {
    initializeValidationService(GERMAN_LOCALE_STRING, SEVERITY_ERROR, true);

    final String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_REPORT_BED_OCCUPANCY_EXAMPLE);

    final OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

    final Locale parsedLocale = LocaleUtils.toLocale(DEFAULT_LOCALE_STRING);
    Locale.setDefault(parsedLocale); // Set back global locale
    final List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        ValidationServiceTest.getErrorOrFatalIssue(operationOutcome);
    assertFalse(errorsOrFatalIssues.isEmpty());
    final String diagnosticsOfOnlyError = errorsOrFatalIssues.getFirst().getDiagnostics();
    assertEquals(
        "Keine Antwort für das erforderliche Element gefunden numberOccupiedBedsGeneralWardChildren",
        diagnosticsOfOnlyError);
  }

  @Test
  void catchBugInHapiValidator() throws IOException {
    final String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_PARAMETERS_META_EXCEPTION);
    final OperationOutcome result = validationService.validate(invalidFileContent);
    assertThat(result).isNotNull();
    assertThat(result.getIssue()).hasSize(1);
    assertThat(result.getIssueFirstRep())
        .returns(IssueSeverity.FATAL, OperationOutcomeIssueComponent::getSeverity)
        .returns(OperationOutcome.IssueType.EXCEPTION, OperationOutcomeIssueComponent::getCode);
  }

  private Locale createLocale(final String locale) {
    final var localization = LocaleUtils.toLocale(locale);
    Locale.setDefault(localization);
    return localization;
  }

  private void initializeValidationService(
      final String locale, final String severity, boolean terminologyServiceActive) {
    final Locale currentLocale = createLocale(locale);
    final var filteredMessagePrefixesFactory = new FilteredMessagePrefixesFactory(currentLocale);
    final var fhirValidatorFactory =
        new FhirValidatorFactory(profileParserService, fhirContext, 5, terminologyServiceActive);
    final var minSeverityFactory = new MinSeverityFactory(severity);
    validationService =
        new ValidationService(
            fhirContext, fhirValidatorFactory, minSeverityFactory, filteredMessagePrefixesFactory);
  }
}
