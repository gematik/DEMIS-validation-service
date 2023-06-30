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
import de.gematik.demis.validationservice.services.ProfileParserService;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;

@RequiredArgsConstructor
@Slf4j
final class FhirValidatorFactory implements Supplier<FhirValidator> {

  private final ProfileParserService profileParserService;
  private final FhirContext fhirContext;
  private final Locale configuredLocale;

  private FhirValidator validator;

  @Override
  public FhirValidator get() {
    log.info("Start creating and initializing fhir validator");
    createWithActivatedConfiguredLocale();
    log.info("Finished creating and initializing fhir validator");
    return this.validator;
  }

  private void createWithActivatedConfiguredLocale() {
    new ConfiguredLocaleExecutor(this.fhirContext, this.configuredLocale)
        .execute(this::createAndInitialize);
  }

  private void createAndInitialize() {
    create();
    initialize();
  }

  private void create() {
    var profiles = this.profileParserService.getParseProfiles();
    final Map<String, IBaseResource> structureDefinitions = profiles.get(StructureDefinition.class);
    final Map<String, IBaseResource> valueSets = profiles.get(ValueSet.class);
    final Map<String, IBaseResource> codeSystems = profiles.get(CodeSystem.class);
    final Map<String, IBaseResource> questionnaires = profiles.get(Questionnaire.class);

    this.validator = this.fhirContext.newValidator();

    final IValidationSupport prePopulatedValidationSupport =
        new DemisPrePopulatedValidationSupportHapi4(
            this.fhirContext, structureDefinitions, valueSets, codeSystems, questionnaires);

    final DefaultProfileValidationSupport defaultProfileValidationSupport =
        new DefaultProfileValidationSupport(this.fhirContext);

    final InMemoryTerminologyServerValidationSupport inMemoryTerminologyServerValidationSupport =
        new InMemoryTerminologyServerValidationSupport(this.fhirContext);

    final SnapshotGeneratingValidationSupport snapshotGenerator =
        new SnapshotGeneratingValidationSupport(this.fhirContext);

    final CommonCodeSystemsTerminologyService commonCodeSystemsTerminologyService =
        new CommonCodeSystemsTerminologyService(this.fhirContext);

    final ValidationSupportChain validationSupportChain =
        new ValidationSupportChain(
            prePopulatedValidationSupport,
            defaultProfileValidationSupport,
            inMemoryTerminologyServerValidationSupport,
            commonCodeSystemsTerminologyService,
            snapshotGenerator);

    final FhirInstanceValidator fhirModule = new FhirInstanceValidator(validationSupportChain);
    fhirModule.setErrorForUnknownProfiles(true);
    validator.registerValidatorModule(fhirModule);
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
