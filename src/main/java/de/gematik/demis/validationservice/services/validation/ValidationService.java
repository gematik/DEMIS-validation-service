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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.stereotype.Service;

/** Service holds a Fhir validator with the given profile. */
@Service
@Slf4j
public class ValidationService {
  private static final SeverityComparator SEVERITY_COMPARATOR = new SeverityComparator();

  private final FhirContext fhirContext;
  private final FhirValidator validator;
  private final ResultSeverityEnum minSeverityOutcome;
  private final Set<String> filteredMessagePrefixes;

  ValidationService(
      final FhirContext fhirContext,
      final FhirValidatorFactory fhirValidatorFactory,
      final MinSeverityFactory minSeverityFactory,
      final FilteredMessagePrefixesFactory filteredMessagePrefixesFactory) {
    this.fhirContext = fhirContext;
    this.validator = fhirValidatorFactory.get();
    this.minSeverityOutcome = minSeverityFactory.get();
    this.filteredMessagePrefixes = filteredMessagePrefixesFactory.get();
  }

  @Observed(
      name = "validate",
      contextualName = "validate",
      lowCardinalityKeyValues = {"notification", "fhir"})
  public OperationOutcome validate(final String content) {
    try {
      final ValidationResult validationResult = validator.validateWithResult(content);
      log.info("Validation successful: {}", validationResult.isSuccessful());
      return toOperationOutcome(validationResult);
    } catch (final Exception e) {
      return handleException(e);
    }
  }

  private OperationOutcome handleException(final Exception e) {
    final String errorId = UUID.randomUUID().toString();
    log.error("{} - exception in validation", errorId, e);

    final OperationOutcome operationOutcome = new OperationOutcome();
    operationOutcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.FATAL)
        .setCode(OperationOutcome.IssueType.EXCEPTION)
        .setDiagnostics("exception in validation")
        .addLocation(errorId);
    return operationOutcome;
  }

  private OperationOutcome toOperationOutcome(final ValidationResult validationResult) {
    final List<SingleValidationMessage> collect =
        validationResult.getMessages().stream()
            .filter(this::checkFilters)
            .filter(this::checkSeverity)
            .toList();
    final ValidationResult filteredValidationResult =
        new ValidationResult(this.fhirContext, collect);
    final OperationOutcome outcome = new OperationOutcome();
    filteredValidationResult.populateOperationOutcome(outcome);
    return outcome;
  }

  private boolean checkFilters(SingleValidationMessage message) {
    return filteredMessagePrefixes.stream().noneMatch(message.getMessage()::startsWith);
  }

  private boolean checkSeverity(SingleValidationMessage message) {
    return SEVERITY_COMPARATOR.compare(message.getSeverity(), minSeverityOutcome) >= 0;
  }
}
