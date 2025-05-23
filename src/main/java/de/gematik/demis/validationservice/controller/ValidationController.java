package de.gematik.demis.validationservice.controller;

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

import static de.gematik.demis.validationservice.services.FormatValidator.isValidJson;
import static de.gematik.demis.validationservice.services.FormatValidator.isValidXml;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.validationservice.services.validation.ValidationService;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for the main rest endpoint dor this service. */
@Slf4j
@RestController
@RequestMapping(path = "/")
public class ValidationController {
  private final FhirContext fhirContext;
  private final ValidationService validationService;

  private final boolean isFormatValidationActive;

  public ValidationController(
      final FhirContext fhirContext,
      final ValidationService validationService,
      @Value("${feature.flag.format.validation.enabled}") boolean isFormatValidationActive) {
    this.fhirContext = fhirContext;
    this.validationService = validationService;
    this.isFormatValidationActive = isFormatValidationActive;
  }

  @PostMapping(
      path = "$validate",
      consumes = {
        APPLICATION_JSON_VALUE,
        APPLICATION_XML_VALUE,
        "application/xml+fhir",
        "application/fhir+xml",
        "application/json+fhir",
        "application/fhir+json"
      },
      produces = {
        APPLICATION_JSON_VALUE,
        APPLICATION_XML_VALUE,
        "application/xml+fhir",
        "application/fhir+xml",
        "application/json+fhir",
        "application/fhir+json"
      })
  public ResponseEntity<String> processReport(
      @RequestBody final String content,
      @RequestHeader(CONTENT_TYPE) MediaType mediaType,
      @RequestHeader(name = ACCEPT, required = false) MediaType accept) {
    if (isFormatValidationActive && !isFormatValid(content, mediaType)) {
      return operationOutcomeErrorWithNoValidJson(mediaType, accept);
    }
    final OperationOutcome operationOutcome = validationService.validate(content);
    return toResponseEntity(operationOutcome, mediaType, accept);
  }

  private boolean isFormatValid(String content, MediaType mediaType) {
    if (mediaType.getSubtype().contains(MediaType.APPLICATION_JSON.getSubtype())) {
      return isValidJson(content);
    } else if (mediaType.getSubtype().contains(MediaType.APPLICATION_XML.getSubtype())) {
      return isValidXml(content);
    }
    return false;
  }

  private ResponseEntity<String> operationOutcomeErrorWithNoValidJson(
      MediaType mediaType, MediaType accept) {
    final OperationOutcome operationOutcome =
        new OperationOutcome()
            .addIssue(
                new OperationOutcome.OperationOutcomeIssueComponent()
                    .setSeverity(IssueSeverity.FATAL)
                    .setDiagnostics("Given data is not a valid " + mediaType.getSubtype()));

    return toResponseEntity(operationOutcome, mediaType, accept);
  }

  private ResponseEntity<String> toResponseEntity(
      final OperationOutcome operationOutcome, MediaType mediaType, MediaType accept) {
    MediaType mT = accept != null ? accept : mediaType;
    IParser iParser;
    if (mT.getSubtype().contains(MediaType.APPLICATION_JSON.getSubtype())) {
      iParser = fhirContext.newJsonParser();
    } else if (mT.getSubtype().contains(MediaType.APPLICATION_XML.getSubtype())) {
      iParser = fhirContext.newXmlParser();
    } else {
      return ResponseEntity.badRequest().body("Unsupported media type");
    }

    final String operationOutcomeAsString = iParser.encodeResourceToString(operationOutcome);
    final boolean hasErrorOrFatalIssue =
        operationOutcome.getIssue().stream()
            .anyMatch(
                issue ->
                    issue.getSeverity() == IssueSeverity.ERROR
                        || issue.getSeverity() == IssueSeverity.FATAL);
    if (hasErrorOrFatalIssue) {
      log.warn("Operation failed with {}", operationOutcomeAsString);
      return ResponseEntity.unprocessableEntity().body(operationOutcomeAsString);
    }

    return ResponseEntity.ok().body(operationOutcomeAsString);
  }
}
