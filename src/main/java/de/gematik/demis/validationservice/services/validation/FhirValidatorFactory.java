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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import de.gematik.demis.validationservice.config.ValidationConfigProperties;
import de.gematik.demis.validationservice.services.ProfileParserService;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
final class FhirValidatorFactory {

  private final ProfileParserService profileParserService;
  private final FhirContext fhirContext;
  private final ValidationConfigProperties configProperties;

  @Value("${feature.flag.common.code.system.terminology.enabled}")
  private boolean featureFlagCommonCodeSystemsTerminologyEnabled;

  public FhirValidator createFhirValidator(final Path profilesPath) {
    log.info("Start creating and initializing fhir validator for profiles path {}", profilesPath);
    final var validator = create(profilesPath);
    initialize(validator);
    log.info("Finished creating and initializing fhir validator");
    return validator;
  }

  private FhirValidator create(final Path profilesPath) {
    final IValidationSupport validationSupport = createValidationSupport(profilesPath);
    final FhirInstanceValidator fhirModule = new FhirInstanceValidator(validationSupport);
    fhirModule.setErrorForUnknownProfiles(true);
    final var validator = fhirContext.newValidator();
    validator.registerValidatorModule(fhirModule);
    return validator;
  }

  private IValidationSupport createValidationSupport(final Path profilesPath) {
    final ValidationSupportChain chain = new ValidationSupportChain();
    addDemisPrePopulatedValidation(chain, profilesPath);
    addDefaultProfileValidation(chain);
    addExpandedValueSetsValidation(chain);
    addInMemoryTerminologyServerValidation(chain);
    addSnapshotGeneratingValidation(chain);
    addCommonCodeSystemsValidation(chain);
    return chain;
  }

  private void addDemisPrePopulatedValidation(
      final ValidationSupportChain chain, final Path profilesPath) {
    final var profiles = profileParserService.parseProfile(profilesPath);
    final var structureDefinitions = profiles.get(StructureDefinition.class);
    final var valueSets = profiles.get(ValueSet.class);
    final var codeSystems = profiles.get(CodeSystem.class);
    final var questionnaires = profiles.get(Questionnaire.class);
    chain.addValidationSupport(
        new DemisPrePopulatedValidationSupportHapi4(
            fhirContext, structureDefinitions, valueSets, codeSystems, questionnaires));
  }

  private void addDefaultProfileValidation(ValidationSupportChain chain) {
    chain.addValidationSupport(new DefaultProfileValidationSupport(this.fhirContext));
  }

  private void addExpandedValueSetsValidation(ValidationSupportChain chain) {
    chain.addValidationSupport(new ExpandedValueSetsCodeValidationSupport(this.fhirContext));
  }

  private void addInMemoryTerminologyServerValidation(ValidationSupportChain chain) {
    chain.addValidationSupport(
        new CachingValidationSupport(
            new InMemoryTerminologyServerValidationSupport(this.fhirContext),
            createTerminologyServerCacheTimeouts()));
  }

  private CachingValidationSupport.CacheTimeouts createTerminologyServerCacheTimeouts() {
    final long millis =
        TimeUnit.MINUTES.toMillis(configProperties.cacheExpireAfterAccessTimeoutMins());
    if (log.isInfoEnabled()) {
      log.info(
          "Creating in-memory terminology server cache timeouts. ExpireAfterAccessTimeout: {}",
          DurationFormatUtils.formatDurationHMS(millis));
    }
    return (new CachingValidationSupport.CacheTimeouts())
        .setLookupCodeMillis(millis)
        .setExpandValueSetMillis(millis)
        .setTranslateCodeMillis(millis)
        .setValidateCodeMillis(millis)
        .setMiscMillis(millis);
  }

  private void addSnapshotGeneratingValidation(ValidationSupportChain chain) {
    chain.addValidationSupport(new SnapshotGeneratingValidationSupport(this.fhirContext));
  }

  private void addCommonCodeSystemsValidation(ValidationSupportChain chain) {
    if (featureFlagCommonCodeSystemsTerminologyEnabled) {
      chain.addValidationSupport(new CommonCodeSystemsTerminologyService(this.fhirContext));
    }
  }

  /**
   * Does one validation with a code system, so DefaultProfileValidationSupport loads all resources.
   */
  private void initialize(final FhirValidator validator) {
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
    validator.validateWithResult(bundle);
  }
}
