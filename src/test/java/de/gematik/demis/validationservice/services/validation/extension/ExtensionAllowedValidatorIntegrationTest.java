package de.gematik.demis.validationservice.services.validation.extension;

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

import static java.nio.file.Files.readString;
import static java.nio.file.Path.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import de.gematik.demis.validationservice.config.ValidationConfigProperties;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class ExtensionAllowedValidatorIntegrationTest {

  private static final String STRUCTURE_DEFINITION_FILE =
      "src/test/resources/extension/StructureDefinition/StructureDefinition-NotifiedPerson.json";
  private static final String NOTIFIED_PERSON_PROFILE =
      "https://demis.rki.de/fhir/StructureDefinition/NotifiedPerson";

  private final FhirContext fhirContext = FhirContext.forR4Cached();
  private FhirValidator fhirValidator;

  @BeforeEach
  @SneakyThrows
  void setup() {
    final ValidationConfigProperties config =
        ValidationConfigProperties.builder()
            .unexpectedExtensionSeverity(ResultSeverityEnum.WARNING)
            .build();
    final var extensionAllowedValidatorProvider =
        new ExtensionAllowedValidatorProvider(
            new ResourceWalker(), new StructureDefinitionExtensionExtractor(), config);
    fhirValidator = fhirContext.newValidator();
    fhirValidator.registerValidatorModule(
        extensionAllowedValidatorProvider.createValidatorModule(
            provideStructureDefinition(), true, true));
  }

  @Test
  void unexpectedExtensionDueToNotDefinedInStructureDefinition() {
    final Bundle bundle = new Bundle();
    final Patient patient = new Patient();
    bundle.addEntry().setResource(patient);

    patient.getMeta().addProfile(NOTIFIED_PERSON_PROFILE);

    patient
        .getBirthDateElement()
        .setValue(new Date())
        .addExtension()
        .setUrl("http://demis.de/test/my-extension")
        .setValue(new StringType().setValue("test"));

    final ValidationResult validationResult = fhirValidator.validateWithResult(bundle);

    assertThat(validationResult.getMessages())
        .singleElement()
        .returns(ResultSeverityEnum.WARNING, SingleValidationMessage::getSeverity)
        .returns("Unexpected_Extension", SingleValidationMessage::getMessageId)
        .returns(
            "Es wurde eine unerwartete Extension http://demis.de/test/my-extension gefunden. Das Profil https://demis.rki.de/fhir/StructureDefinition/NotifiedPerson erlaubt für Patient.birthDate.extension keine Extensions",
            SingleValidationMessage::getMessage)
        .returns(
            "Bundle.entry[0].resource/*Patient/null*/.birthDate.extension[0]",
            SingleValidationMessage::getLocationString);
  }

  @Test
  void modifierExtensionsAreNotAllowed() {
    final Bundle bundle = new Bundle();
    final Composition composition = new Composition();
    bundle.addEntry().setResource(composition);

    composition
        .addModifierExtension()
        .setUrl("http://demis.de/test/my-extension")
        .setValue(new StringType().setValue("test"));

    final ValidationResult validationResult = fhirValidator.validateWithResult(bundle);

    final String expectedExtensionPath =
        "Bundle.entry[0].resource/*Composition/null*/.modifierExtension[0]";

    // Note: Both feature flags are active
    // thus we expect one error due to modifierExtension and one warning due to unexpected extension
    // url
    assertThat(validationResult.getMessages())
        .extracting(
            SingleValidationMessage::getSeverity,
            SingleValidationMessage::getMessageId,
            SingleValidationMessage::getMessage,
            SingleValidationMessage::getLocationString)
        .containsExactlyInAnyOrder(
            tuple(
                ResultSeverityEnum.ERROR,
                "Modifier_Extension_not_allowed",
                "Modifier Extensions sind nicht erlaubt.",
                expectedExtensionPath),
            tuple(
                ResultSeverityEnum.WARNING,
                "Unexpected_Extension",
                "Es wurde eine unerwartete Extension http://demis.de/test/my-extension gefunden. Das Profil http://hl7.org/fhir/StructureDefinition/Composition erlaubt für Composition.modifierExtension keine Extensions",
                expectedExtensionPath));
  }

  private IValidationSupport provideStructureDefinition() throws IOException {
    final ValidationSupportChain validationSupportChain = new ValidationSupportChain();
    final StructureDefinition structureDefinition =
        fhirContext
            .newJsonParser()
            .parseResource(StructureDefinition.class, readString(of(STRUCTURE_DEFINITION_FILE)));
    validationSupportChain.addValidationSupport(
        new PrePopulatedValidationSupport(
            fhirContext, Map.of(NOTIFIED_PERSON_PROFILE, structureDefinition), Map.of(), Map.of()));
    validationSupportChain.addValidationSupport(new DefaultProfileValidationSupport(fhirContext));
    return validationSupportChain;
  }
}
