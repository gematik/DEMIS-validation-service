package de.gematik.demis.validationservice.services.validation;

/*-
 * #%L
 * validation-service
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import de.gematik.demis.validationservice.config.FeatureFlags;
import de.gematik.demis.validationservice.config.ValidationConfigProperties;
import de.gematik.demis.validationservice.services.ProfileParserService;
import de.gematik.demis.validationservice.services.ProfileSnapshot;
import de.gematik.demis.validationservice.services.terminology.TerminologyValidationProvider;
import de.gematik.demis.validationservice.services.validation.custom.extension.ExtensionAllowedValidatorProvider;
import de.gematik.demis.validationservice.services.validation.custom.profile.AbstractProfileValidator;
import de.gematik.demis.validationservice.services.validation.custom.questionnaire.responses.CustomQuantityComparatorQuestionnaireResponseValidator;
import de.gematik.demis.validationservice.services.validation.custom.questionnaire.responses.CustomRegexQuestionnaireResponseValidator;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.ReferenceProfileValidationModule;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.rules.ReferenceProfileRules;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.rules.ReferenceProfileRulesBuilder;
import de.gematik.demis.validationservice.services.validation.custom.resourcereferences.ResourceReferencesBundleValidationConfigProperties;
import de.gematik.demis.validationservice.services.validation.custom.resourcereferences.ResourceReferencesBundleValidationModule;
import de.gematik.demis.validationservice.services.validation.custom.strict.valuesets.StrictValueSetMembershipValidator;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r5.utils.validation.constants.BestPracticeWarningLevel;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public final class FhirValidatorFactory {

  private final ProfileParserService profileParserService;
  private final FhirContext fhirContext;
  private final ValidationConfigProperties configProperties;
  private final TerminologyValidationProvider terminologyValidationProvider;
  private final ExtensionAllowedValidatorProvider extensionAllowedValidatorProvider;
  private final FeatureFlags featureFlags;
  private final ResourceReferencesBundleValidationConfigProperties
      resourceReferencesBundleValidationConfigProperties;

  private ProfileSnapshot profileSnapshot;

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
    fhirModule.setBestPracticeWarningLevel(BestPracticeWarningLevel.Ignore);
    final var validator = fhirContext.newValidator();
    validator.registerValidatorModule(fhirModule);

    validator.registerValidatorModule(
        extensionAllowedValidatorProvider.createValidatorModule(validationSupport));

    if (featureFlags.isVsReferenceValidation()) {
      validator.registerValidatorModule(
          new ResourceReferencesBundleValidationModule(
              resourceReferencesBundleValidationConfigProperties));
    }
    if (featureFlags.isReferenceProfileValidation()) {
      validator.registerValidatorModule(
          new ReferenceProfileValidationModule(buildReferenceProfileRules()));
    }
    if (configProperties.customQuantityValidatorEnabled()) {
      validator.registerValidatorModule(
          new CustomQuantityComparatorQuestionnaireResponseValidator(
              profileSnapshot.questionnaires()));
    }
    if (configProperties.customRegexValidatorEnabled()) {
      validator.registerValidatorModule(
          new CustomRegexQuestionnaireResponseValidator(profileSnapshot.questionnaires()));
    }
    if (configProperties.additionalStrictCodingValidatorEnabled()) {
      validator.registerValidatorModule(
          new StrictValueSetMembershipValidator(
              profileSnapshot.valueSets(), profileSnapshot.structureDefinitions()));
    }
    if (featureFlags.isAbstractProfileValidation()) {
      validator.registerValidatorModule(
          new AbstractProfileValidator(profileSnapshot.structureDefinitions()));
    }
    return validator;
  }

  private IValidationSupport createValidationSupport(final Path profilesPath) {
    final ValidationSupportChain chain = new ValidationSupportChain();
    addDemisPrePopulatedValidation(chain, profilesPath);
    addDefaultProfileValidation(chain);
    terminologyValidationProvider.addTerminologyValidationSupport(chain, profilesPath);
    addSnapshotGeneratingValidation(chain);
    addCommonCodeSystemsValidation(chain);
    return chain;
  }

  private void addDemisPrePopulatedValidation(
      final ValidationSupportChain chain, final Path profilesPath) {
    profileSnapshot = profileParserService.parseProfile(profilesPath);
    chain.addValidationSupport(
        new DemisPrePopulatedValidationSupportHapi4(fhirContext, profileSnapshot));
  }

  private void addDefaultProfileValidation(ValidationSupportChain chain) {
    chain.addValidationSupport(new DefaultProfileValidationSupport(this.fhirContext));
  }

  private void addSnapshotGeneratingValidation(ValidationSupportChain chain) {
    chain.addValidationSupport(new SnapshotGeneratingValidationSupport(this.fhirContext));
  }

  private void addCommonCodeSystemsValidation(ValidationSupportChain chain) {
    if (configProperties.commonCodeSystemTerminologyEnabled()) {
      chain.addValidationSupport(new CommonCodeSystemsTerminologyService(this.fhirContext));
    }
  }

  private ReferenceProfileRules buildReferenceProfileRules() {
    log.info(
        "Building reference profile rules from {} structure definitions",
        profileSnapshot.structureDefinitions().size());
    return new ReferenceProfileRulesBuilder().build(profileSnapshot.structureDefinitions());
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
