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

package de.gematik.demis.validationservice.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import de.gematik.demis.validationservice.services.FhirJsonService;
import de.gematik.demis.validationservice.services.JsonValidator;
import de.gematik.demis.validationservice.services.validation.ValidationService;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Controller for the main rest endpoint dor this service. */
@RestController
@RequestMapping(path = "/")
public class ValidationController {
  private final JsonValidator jsonValidator;
  private final FhirJsonService fhirJsonService;
  private final ValidationService validationService;

  public ValidationController(
      JsonValidator jsonValidator,
      FhirJsonService fhirJsonService,
      ValidationService validationService) {
    this.jsonValidator = jsonValidator;
    this.fhirJsonService = fhirJsonService;
    this.validationService = validationService;
  }

  @PostMapping(
      path = "$validate",
      consumes = APPLICATION_JSON_VALUE,
      produces = APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<String> processReport(@RequestBody String content) {
    if (!jsonValidator.isValidJson(content)) {
      return operationOutcomeErrorWithNoValidJson();
    }
    OperationOutcome operationOutcome = validationService.validate(content);

    return toResponseEntity(operationOutcome);
  }

  private ResponseEntity<String> operationOutcomeErrorWithNoValidJson() {
    OperationOutcome operationOutcome =
        new OperationOutcome()
            .addIssue(
                new OperationOutcomeIssueComponent()
                    .setSeverity(IssueSeverity.FATAL)
                    .setDiagnostics("Given data is not a valid JSON"));

    return toResponseEntity(operationOutcome);
  }

  private ResponseEntity<String> toResponseEntity(OperationOutcome operationOutcome) {
    String operationOutcomeAsJson = fhirJsonService.toJson(operationOutcome);
    boolean hasErrorOrFatalIssue =
        operationOutcome.getIssue().stream()
            .anyMatch(
                issue ->
                    issue.getSeverity() == IssueSeverity.ERROR
                        || issue.getSeverity() == IssueSeverity.FATAL);
    if (hasErrorOrFatalIssue) {
      return ResponseEntity.unprocessableEntity().body(operationOutcomeAsJson);
    }

    return ResponseEntity.ok().body(operationOutcomeAsJson);
  }
}
