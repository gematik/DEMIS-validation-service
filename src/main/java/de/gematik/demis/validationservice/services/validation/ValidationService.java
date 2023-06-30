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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import de.gematik.demis.validationservice.services.ProfileParserService;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Service holds a Fhir validator with the given profile. */
@Service
@Slf4j
public class ValidationService implements InitializingBean {
  private static final SeverityComparator SEVERITY_COMPARATOR = new SeverityComparator();

  private final ProfileParserService profileParserService;
  private final FhirContext fhirContext;
  private final String locale;
  private final String minSeverityOutcomeText;

  private FhirValidator validator;
  private ResultSeverityEnum minSeverityOutcome;
  private Set<String> filteredMessagePrefixes;

  ValidationService(
      final ProfileParserService profileParserService,
      final FhirContext fhirContext,
      @Value("${demis.validation-service.locale}") String locale,
      @Value("${demis.validation-service.minSeverityOutcome}") final String minSeverityOutcome) {
    this.profileParserService = profileParserService;
    this.fhirContext = fhirContext;
    this.locale = locale;
    this.minSeverityOutcomeText = minSeverityOutcome;
  }

  public OperationOutcome validate(final String content) {
    final ValidationResult validationResult = this.validator.validateWithResult(content);
    log.info("Validation successful: {}", validationResult.isSuccessful());
    return toOperationOutcome(validationResult);
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
    return this.filteredMessagePrefixes.stream().noneMatch(message.getMessage()::startsWith);
  }

  private boolean checkSeverity(SingleValidationMessage message) {
    return SEVERITY_COMPARATOR.compare(message.getSeverity(), this.minSeverityOutcome) >= 0;
  }

  @Override
  public void afterPropertiesSet() {
    Locale configuredLocale = ConfiguredLocaleExecutor.getLocale(this.locale);
    initializeMinSeverityOutcome();
    initializeFhirValidator(configuredLocale);
    initializeFilteredMessagePrefixes(configuredLocale);
  }

  private void initializeMinSeverityOutcome() {
    this.minSeverityOutcome = new MinSeverityFactory(this.minSeverityOutcomeText).get();
  }

  private void initializeFilteredMessagePrefixes(Locale configuredLocale) {
    this.filteredMessagePrefixes =
        new FilteredMessagePrefixesFactory(this.fhirContext, configuredLocale).get();
  }

  private void initializeFhirValidator(Locale configuredLocale) {
    this.validator =
        new FhirValidatorFactory(this.profileParserService, this.fhirContext, configuredLocale)
            .get();
  }
}
