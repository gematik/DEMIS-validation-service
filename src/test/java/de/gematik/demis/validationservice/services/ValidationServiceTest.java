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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.gematik.demis.validationservice.services.validation.ValidationService;
import de.gematik.demis.validationservice.util.FileTestUtil;
import de.gematik.demis.validationservice.util.ResourceFileConstants;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.LocaleUtils;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {
  private static final String DEFAULT_LOCALE = "en_US";
  private static ProfileParserService profileParserService;
  private static ValidationService validationService;

  private static List<OperationOutcomeIssueComponent> getErrorOrFatalIssue(
      OperationOutcome operationOutcome) {
    return operationOutcome.getIssue().stream()
        .filter(
            issue ->
                issue.getSeverity() == IssueSeverity.ERROR
                    || issue.getSeverity() == IssueSeverity.FATAL)
        .toList();
  }

  @BeforeAll
  static void setupValidationService() {
    FhirContextService fhirContextService = new FhirContextService(DEFAULT_LOCALE);
    profileParserService =
        new ProfileParserService(fhirContextService, ResourceFileConstants.PROFILE_RESOURCE_PATH);
    validationService =
        new ValidationService(fhirContextService, profileParserService, "information");
  }

  @Test
  void validateValidFileAndCheckThereIsNoErrorOrFatal() throws IOException {
    String validFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.VALID_REPORT_BED_OCCUPANCY_EXAMPLE);

    OperationOutcome operationOutcome = validationService.validate(validFileContent);

    long issueCount = operationOutcome.getIssue().stream().count();
    assertEquals(1, issueCount);
    List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        getErrorOrFatalIssue(operationOutcome);
    assertEquals(0, errorsOrFatalIssues.size(), "There are Errors or Fatal issues in outcome");
  }

  @Test
  void validateInvalidFileAndCheckThereIsOneErrorWithMissingAnswer() throws IOException {
    String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_REPORT_BED_OCCUPANCY_EXAMPLE);

    OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

    List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        getErrorOrFatalIssue(operationOutcome);
    assertEquals(1, errorsOrFatalIssues.size());
    String diagnosticsOfOnlyError = errorsOrFatalIssues.get(0).getDiagnostics();
    assertEquals(
        "No response answer found for required item 'numberOccupiedBedsGeneralWardChildren'",
        diagnosticsOfOnlyError);
  }

  @Test
  void validateInvalidDv2FileWithFollowupsAndCheckThereIsOneErrorAndFollowUpsAreFiltered()
      throws IOException {
    String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_TEST_NOTIFICATION_DV_2);

    OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

    List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        getErrorOrFatalIssue(operationOutcome);
    System.out.println(errorsOrFatalIssues.get(0).getDiagnostics());
    assertEquals(2, errorsOrFatalIssues.size());
    List<String> diagnostics =
        errorsOrFatalIssues.stream().map(OperationOutcomeIssueComponent::getDiagnostics).toList();
    assertTrue(diagnostics.stream().anyMatch(s -> s.contains("noNumbersInFamilyName")));
  }

  @Test
  void validateInvalidDv2FileWithFollowupsAndCheckThereIsSlicedMessageAndMultipleProfilesMessage()
      throws IOException {
    String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_TEST_NOTIFICATION_DV_2);

    OperationOutcome operationOutcome = validationService.validate(invalidFileContent);

    List<String> diagnostics =
        operationOutcome.getIssue().stream()
            .map(OperationOutcomeIssueComponent::getDiagnostics)
            .toList();
    assertTrue(diagnostics.stream().anyMatch(s -> !s.contains("Multiple profiles")));
    assertTrue(diagnostics.stream().anyMatch(s -> !s.contains("known slice defined")));
  }

  @Test
  void validateNotParsableFileAndCheckThereAreTenErrorAndFatalIssues() throws IOException {
    String validFileContent =
        FileTestUtil.readFileIntoString(
            ResourceFileConstants.NOT_PARSEABLE_REPORT_BED_OCCUPANCY_EXAMPLE);

    OperationOutcome operationOutcome = validationService.validate(validFileContent);

    List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        getErrorOrFatalIssue(operationOutcome);
    assertEquals(10, errorsOrFatalIssues.size());
  }

  @Test
  void validateValidFileAndFilterInformationAndWarningsAndCheckThereIsOnlyOneIssue()
      throws IOException {
    FhirContextService fhirContextService = new FhirContextService(DEFAULT_LOCALE);
    ValidationService validationServiceWithFilter =
        new ValidationService(fhirContextService, profileParserService, "error");
    String validFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.VALID_REPORT_BED_OCCUPANCY_EXAMPLE);

    OperationOutcome operationOutcome = validationServiceWithFilter.validate(validFileContent);

    long issueCount = operationOutcome.getIssue().stream().count();
    assertEquals(1, issueCount, "There still more issues in outcome");
  }

  @Test
  void setValidationServiceToGermanAndValidateInvalidFileAndCheckThereIsOneErrorWithAGermanAnswer()
      throws IOException {
    FhirContextService fhirContextService = new FhirContextService("de_DE");
    ValidationService germanValidationService =
        new ValidationService(fhirContextService, profileParserService, "information");
    String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_REPORT_BED_OCCUPANCY_EXAMPLE);

    OperationOutcome operationOutcome = germanValidationService.validate(invalidFileContent);

    Locale parsedLocale = LocaleUtils.toLocale(DEFAULT_LOCALE);
    Locale.setDefault(parsedLocale); // Set back global locale
    List<OperationOutcomeIssueComponent> errorsOrFatalIssues =
        getErrorOrFatalIssue(operationOutcome);
    assertEquals(1, errorsOrFatalIssues.size());
    String diagnosticsOfOnlyError = errorsOrFatalIssues.get(0).getDiagnostics();
    assertEquals(
        "Keine Antwort für das erforderliche Element gefunden numberOccupiedBedsGeneralWardChildren",
        diagnosticsOfOnlyError);
  }
}
