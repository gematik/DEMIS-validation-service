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

package de.gematik.demis.validationservice.services.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@Slf4j
@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {
  private final String DEFAULT_LOCALE = "en_US";
  private ProfileParserService profileParserService;
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
  void setupValidationService() throws Exception {
    final FhirContext fhirContext = FhirContext.forR4();
    profileParserService =
        new ProfileParserService(fhirContext, ResourceFileConstants.PROFILE_RESOURCE_PATH);
    profileParserService.afterPropertiesSet();
    validationService =
        new ValidationService(profileParserService, fhirContext, DEFAULT_LOCALE, "information");
    validationService.afterPropertiesSet();
  }

  @Test
  void validateValidFileAndCheckThereIsNoErrorOrFatal() throws IOException {
    final String validFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.VALID_REPORT_BED_OCCUPANCY_EXAMPLE);

    final OperationOutcome operationOutcome = validationService.validate(validFileContent);

    final long issueCount = operationOutcome.getIssue().stream().count();
    assertEquals(1, issueCount);
    final List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        getErrorOrFatalIssue(operationOutcome);
    assertEquals(0, errorsOrFatalIssues.size(), "There are Errors or Fatal issues in outcome");
  }

  @Test
  void validateInvalidFileAndCheckThereIsOneErrorWithMissingAnswer() throws IOException {
    final String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_REPORT_BED_OCCUPANCY_EXAMPLE);
    //    final FhirContext fhirContext = FhirContext.forR4();
    //    validationService =
    //            new ValidationService(profileParserService, fhirContext, DEFAULT_LOCALE,
    // "information");

    final OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

    final List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        getErrorOrFatalIssue(operationOutcome);
    assertEquals(1, errorsOrFatalIssues.size());
    final String diagnosticsOfOnlyError = errorsOrFatalIssues.get(0).getDiagnostics();
    assertEquals(
        "No response answer found for required item 'numberOccupiedBedsGeneralWardChildren'",
        diagnosticsOfOnlyError);
  }

  @Test
  void expectNoNumbersInFamilyNameErrorFoundInInvalidNotificationDV2() throws IOException {

    // GIVEN an invalid file with different invalid values
    final String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_TEST_NOTIFICATION_DV_2);

    // WHEN it's validated
    final OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

    final List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        getErrorOrFatalIssue(operationOutcome);

    // THEN at least "noNumbersInFamilyName is found across one or multiple errors found
    System.out.println(errorsOrFatalIssues.get(0).getDiagnostics());
    final List<String> diagnostics =
        errorsOrFatalIssues.stream().map(OperationOutcomeIssueComponent::getDiagnostics).toList();
    assertTrue(diagnostics.stream().anyMatch(s -> s.contains("noNumbersInFamilyName")));
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
        getErrorOrFatalIssue(operationOutcome);
    assertEquals(10, errorsOrFatalIssues.size());
  }

  @Test
  void validateValidFileAndFilterInformationAndWarningsAndCheckThereIsOnlyOneIssue()
      throws IOException {
    final FhirContext fhirContext = FhirContext.forR4();
    final ValidationService validationServiceWithFilter =
        new ValidationService(profileParserService, fhirContext, DEFAULT_LOCALE, "error");
    validationServiceWithFilter.afterPropertiesSet();
    final String validFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.VALID_REPORT_BED_OCCUPANCY_EXAMPLE);

    final OperationOutcome operationOutcome =
        validationServiceWithFilter.validate(validFileContent);

    operationOutcome
        .getIssue()
        .forEach(
            operationOutcomeIssueComponent ->
                log.info(operationOutcomeIssueComponent.getDiagnostics()));

    final long issueCount = operationOutcome.getIssue().stream().count();
    assertEquals(1, issueCount, "There still more issues in outcome");
  }

  @Test
  void setValidationServiceToGermanAndValidateInvalidFileAndCheckThereIsOneErrorWithAGermanAnswer()
      throws IOException {
    final FhirContext fhirContext = FhirContext.forR4();
    final ValidationService germanValidationService =
        new ValidationService(profileParserService, fhirContext, "de_DE", "information");
    germanValidationService.afterPropertiesSet();
    final String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_REPORT_BED_OCCUPANCY_EXAMPLE);

    final OperationOutcome operationOutcome = germanValidationService.validate(invalidFileContent);

    final Locale parsedLocale = LocaleUtils.toLocale(DEFAULT_LOCALE);
    Locale.setDefault(parsedLocale); // Set back global locale
    final List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        getErrorOrFatalIssue(operationOutcome);
    assertEquals(1, errorsOrFatalIssues.size());
    final String diagnosticsOfOnlyError = errorsOrFatalIssues.get(0).getDiagnostics();
    assertEquals(
        "Keine Antwort für das erforderliche Element gefunden numberOccupiedBedsGeneralWardChildren",
        diagnosticsOfOnlyError);
  }
}
