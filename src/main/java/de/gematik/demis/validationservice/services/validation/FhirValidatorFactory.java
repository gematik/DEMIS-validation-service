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
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import de.gematik.demis.validationservice.services.ProfileParserService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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

@Slf4j
public final class FhirValidatorFactory implements Supplier<FhirValidator> {

  private final ProfileParserService profileParserService;
  private final FhirContext fhirContext;
  private final long cacheExpireAfterAccessTimeoutMins;
  private final boolean isCommonCodeSystemTerminologyActive;
  private FhirValidator validator;

  public FhirValidatorFactory(
      ProfileParserService profileParserService,
      FhirContext fhirContext,
      long cacheExpireAfterAccessTimeoutMins,
      boolean isCommonCodeSystemTerminologyActive) {
    this.profileParserService = profileParserService;
    this.fhirContext = fhirContext;
    this.cacheExpireAfterAccessTimeoutMins = cacheExpireAfterAccessTimeoutMins;
    this.isCommonCodeSystemTerminologyActive = isCommonCodeSystemTerminologyActive;
  }

  @Override
  public FhirValidator get() {
    log.debug("Start creating and initializing fhir validator");
    createAndInitialize();
    log.info("Finished creating and initializing fhir validator");
    return this.validator;
  }

  private void createAndInitialize() {
    create();
    initialize();
  }

  private void create() {
    final IValidationSupport validationSupport = createValidationSupport();
    final FhirInstanceValidator fhirModule = new FhirInstanceValidator(validationSupport);
    fhirModule.setErrorForUnknownProfiles(true);
    this.validator = this.fhirContext.newValidator();
    this.validator.registerValidatorModule(fhirModule);
  }

  private IValidationSupport createValidationSupport() {
    final ValidationSupportChain chain = new ValidationSupportChain();
    addDemisPrePopulatedValidation(chain);
    addDefaultProfileValidation(chain);
    addExpandedValueSetsValidation(chain);
    addInMemoryTerminologyServerValidation(chain);
    addSnapshotGeneratingValidation(chain);
    addCommonCodeSystemsValidation(chain);
    return chain;
  }

  private void addDemisPrePopulatedValidation(ValidationSupportChain chain) {
    final var profiles = this.profileParserService.getParseProfiles();
    final var structureDefinitions = profiles.get(StructureDefinition.class);
    final var valueSets = profiles.get(ValueSet.class);
    final var codeSystems = profiles.get(CodeSystem.class);
    final var questionnaires = profiles.get(Questionnaire.class);
    chain.addValidationSupport(
        new DemisPrePopulatedValidationSupportHapi4(
            this.fhirContext, structureDefinitions, valueSets, codeSystems, questionnaires));
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
    final long millis = TimeUnit.MINUTES.toMillis(this.cacheExpireAfterAccessTimeoutMins);
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
    if (isCommonCodeSystemTerminologyActive) {
      chain.addValidationSupport(new CommonCodeSystemsTerminologyService(this.fhirContext));
    }
  }

  /**
   * Does one validation with a code system, so DefaultProfileValidationSupport loads all resources.
   */
  private void initialize() {
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
    this.validator.validateWithResult(bundle);
  }
}
