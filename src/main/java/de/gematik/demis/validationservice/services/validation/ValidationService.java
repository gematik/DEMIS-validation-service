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
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import de.gematik.demis.validationservice.services.ProfileParserService;
import de.gematik.demis.validationservice.services.validation.severity.SeverityComparator;
import de.gematik.demis.validationservice.services.validation.severity.SeverityParser;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Service holds a Fhir validator with the given profile. */
@Service
@Slf4j
public class ValidationService {
  private static final SeverityComparator SEVERITY_COMPARATOR = new SeverityComparator();
  private static final Set<String> FILTERED_MESSAGES_KEYS =
      Set.of(
          "Reference_REF_CantMatchChoice",
          "BUNDLE_BUNDLE_ENTRY_MULTIPLE_PROFILES_other",
          "Validation_VAL_Profile_NoMatch",
          "This_element_does_not_match_any_known_slice_");
  private final FhirContext fhirContext;
  private final FhirValidator validator;
  private final ResultSeverityEnum minSeverityOutcome;
  private Set<String> filteredMessagePrefixes;

  public ValidationService(
      final ProfileParserService profileParserService,
      final FhirContext fhirContext,
      @Value("${demis.validation-service.locale}") String locale,
      @Value("${demis.validation-service.minSeverityOutcome}") final String minSeverityOutcome) {
    this.minSeverityOutcome = SeverityParser.parse(minSeverityOutcome);
    this.fhirContext = fhirContext;
    if (this.minSeverityOutcome == null) {
      final String errorMessage =
          "Configured minSeverityOutcome has an illegal value: %s".formatted(minSeverityOutcome);
      log.error(errorMessage);
      throw new NoSuchElementException(
          errorMessage); // Shutdown service -> No silent config error treatment
    }
    final Locale parsedLocale = LocaleUtils.toLocale(locale);
    final Locale oldLocale = Locale.getDefault();
    Locale.setDefault(parsedLocale);
    log.info(
        "Locale for validation messages: %s"
            .formatted(fhirContext.getLocalizer().getLocale().toString()));
    log.info("Minimum severity of the outcome: %s".formatted(this.minSeverityOutcome));
    // Eager creation of validator for validation performance
    final var profiles = profileParserService.getParseProfiles();
    validator = createAndInitValidator(profiles);
    filteredMessagePrefixes = loadMessagesToFilter(parsedLocale);
    Locale.setDefault(oldLocale); // Put back global default locale to avoid side effects
  }

  /**
   * Does one validation with a code system, so DefaultProfileValidationSupport loads all resources.
   *
   * @param fhirValidator validator to initialize
   */
  private static void initValidator(final FhirValidator fhirValidator) {
    final Observation observation = new Observation();
    observation.setStatus(Observation.ObservationStatus.FINAL);
    observation
        .getCode()
        .addCoding()
        .setSystem("http://loinc.org")
        .setCode("789-8")
        .setDisplay("Erythrocytes [#/volume] in Blood by Automated count");
    final Bundle bundle = new Bundle();
    bundle
        .addEntry()
        .setResource(observation)
        .getRequest()
        .setUrl("Observation")
        .setMethod(Bundle.HTTPVerb.POST);

    fhirValidator.validateWithResult(bundle);
  }

  private Set<String> loadMessagesToFilter(final Locale parsedLocale) {
    final ResourceBundle resourceBundle = ResourceBundle.getBundle("Messages", parsedLocale);
    filteredMessagePrefixes = new HashSet<>(FILTERED_MESSAGES_KEYS.size());
    return FILTERED_MESSAGES_KEYS.stream()
        .map(resourceBundle::getString)
        .map(
            message -> {
              final int indexParameter = message.indexOf("{");
              return message.substring(0, indexParameter > 0 ? indexParameter : message.length());
            })
        .collect(Collectors.toSet());
  }

  private FhirValidator createAndInitValidator(
      final Map<Class<? extends MetadataResource>, Map<String, IBaseResource>> profiles) {
    final Map<String, IBaseResource> structureDefinitions = profiles.get(StructureDefinition.class);
    final Map<String, IBaseResource> valueSets = profiles.get(ValueSet.class);
    final Map<String, IBaseResource> codeSystems = profiles.get(CodeSystem.class);
    final Map<String, IBaseResource> questionnaires = profiles.get(Questionnaire.class);

    log.info("Start creating and initializing fhir validator");
    final FhirValidator fhirValidator = fhirContext.newValidator();

    final IValidationSupport prePopulatedValidationSupport =
        new DemisPrePopulatedValidationSupportHapi4(
            fhirContext, structureDefinitions, valueSets, codeSystems, questionnaires);

    final DefaultProfileValidationSupport defaultProfileValidationSupport =
        new DefaultProfileValidationSupport(fhirContext);

    final InMemoryTerminologyServerValidationSupport inMemoryTerminologyServerValidationSupport =
        new InMemoryTerminologyServerValidationSupport(fhirContext);

    final SnapshotGeneratingValidationSupport snapshotGenerator =
        new SnapshotGeneratingValidationSupport(fhirContext);

    final CommonCodeSystemsTerminologyService commonCodeSystemsTerminologyService =
        new CommonCodeSystemsTerminologyService(fhirContext);

    final ValidationSupportChain validationSupportChain =
        new ValidationSupportChain(
            prePopulatedValidationSupport,
            defaultProfileValidationSupport,
            inMemoryTerminologyServerValidationSupport,
            commonCodeSystemsTerminologyService,
            snapshotGenerator);

    final FhirInstanceValidator fhirModule = new FhirInstanceValidator(validationSupportChain);
    fhirModule.setErrorForUnknownProfiles(true);
    fhirValidator.registerValidatorModule(fhirModule);
    initValidator(fhirValidator);
    log.info("Finished creating and initializing fhir validator");

    return fhirValidator;
  }

  private OperationOutcome toOperationOutcome(final ValidationResult validationResult) {
    final List<SingleValidationMessage> collect =
        validationResult.getMessages().stream()
            .filter(
                message ->
                    filteredMessagePrefixes.stream().noneMatch(message.getMessage()::startsWith))
            .filter(
                message ->
                    SEVERITY_COMPARATOR.compare(message.getSeverity(), minSeverityOutcome) >= 0)
            .toList();
    final ValidationResult filteredValidationResult = new ValidationResult(fhirContext, collect);

    final OperationOutcome outcome = new OperationOutcome();
    filteredValidationResult.populateOperationOutcome(outcome);

    return outcome;
  }

  public OperationOutcome validate(final String content) {
    final ValidationResult validationResult = validator.validateWithResult(content);
    log.info("Validation successful: {}", validationResult.isSuccessful());
    return toOperationOutcome(validationResult);
  }
}
