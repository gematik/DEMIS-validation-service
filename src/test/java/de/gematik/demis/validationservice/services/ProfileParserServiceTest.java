package de.gematik.demis.validationservice.services;

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

import static de.gematik.demis.validationservice.util.ResourceFileConstants.DUPLICATE_NON_TERMINOLOGY_URL_DIFFERENT_VERSION_PROFILES_PATH;
import static de.gematik.demis.validationservice.util.ResourceFileConstants.DUPLICATE_TERMINOLOGY_URL_DIFFERENT_VERSION_PROFILES_PATH;
import static de.gematik.demis.validationservice.util.ResourceFileConstants.DUPLICATE_URL_VERSION_PROFILES_PATH;
import static de.gematik.demis.validationservice.util.ResourceFileConstants.EMPTY_PROFILES_PATH;
import static de.gematik.demis.validationservice.util.ResourceFileConstants.MINIMLAL_PROFILES_PATH;
import static de.gematik.demis.validationservice.util.ResourceFileConstants.NOT_EXISTING_PROFILES_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.validationservice.config.FeatureFlags;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProfileParserServiceTest {

  private final ProfileParserService profileParserService =
      new ProfileParserService(
          FhirContext.forR4Cached(),
          FeatureFlags.builder().fhirPackagePostprocessing(false).build(),
          null);

  @Test
  void okay() {
    final var result = profileParserService.parseProfile(MINIMLAL_PROFILES_PATH);
    assertThat(result).isNotNull();
    final var structureDefinitions = result.structureDefinitions();
    assertThat(structureDefinitions)
        .isNotNull()
        .hasSize(1)
        .containsKey("https://demis.rki.de/fhir/StructureDefinition/DemisProvenance");
  }

  @Test
  void emptyDirectoryThrowsException() {
    assertThatThrownBy(() -> profileParserService.parseProfile(EMPTY_PROFILES_PATH))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void notExistingProfilesDirectoryThrowsException() {
    assertThatThrownBy(() -> profileParserService.parseProfile(NOT_EXISTING_PROFILES_PATH))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Nested
  class Hl7Issue2876Workaround {

    @TempDir Path profilesPath;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void relaxesOnlyAffectedMinimumUnlessUpstreamFixIsEnabled(boolean upstreamFixed)
        throws IOException {
      final var context = FhirContext.forR4Cached();
      final var definitionsPath =
          Files.createDirectory(profilesPath.resolve("StructureDefinition"));
      final var urls =
          List.of(
              "https://demis.rki.de/fhir/StructureDefinition/PathogenDetection",
              "https://demis.rki.de/fhir/igs/StructureDefinition/PathogenDetectionSequence");
      for (int i = 0; i < urls.size(); i++) {
        final var definition = new StructureDefinition().setUrl(urls.get(i)).setType("Observation");
        for (final var elements :
            List.of(
                definition.getSnapshot().getElement(), definition.getDifferential().getElement())) {
          elements.add(
              element("Observation.value[x]:valueString.value", "Observation.value[x].value", 1));
          elements.add(element("Observation.value[x]:valueString", "Observation.value[x]", 1));
          elements.add(
              element("Observation.value[x]:valueQuantity.value", "Observation.value[x].value", 1));
          elements.add(
              element(
                  "Observation.component.value[x]:valueString.value",
                  "Observation.component.value[x].value",
                  1));
          elements.add(element("Observation.status", "Observation.status", 1));
          elements.add(
              element("Observation.value[x]:valueString.value", "Observation.unrelated", 1));
          elements.add(
              element("Observation.value[x]:valueString.value", "Observation.value[x].value", 0));
          elements.add(
              element("Observation.value[x]:valueString.value", "Observation.value[x].value", 2));
        }
        Files.writeString(
            definitionsPath.resolve("observation-" + i + ".json"),
            context.newJsonParser().encodeResourceToString(definition));
      }
      final var service =
          new ProfileParserService(
              context, FeatureFlags.builder().hl7Issue2876Fixed(upstreamFixed).build(), null);

      final var result = service.parseProfile(profilesPath);

      for (int i = 0; i < urls.size(); i++) {
        final var definition = (StructureDefinition) result.structureDefinitions().get(urls.get(i));
        for (final var elements :
            List.of(
                definition.getSnapshot().getElement(), definition.getDifferential().getElement())) {
          assertThat(elements)
              .extracting(ElementDefinition::getMin)
              .containsExactly(upstreamFixed ? 1 : 0, 1, 1, 1, 1, 1, 0, 2);
          assertThat(elements.getFirst().getMax()).isEqualTo("1");
          assertThat(elements.getFirst().getMustSupport()).isTrue();
        }
        final var original =
            context
                .newJsonParser()
                .parseResource(
                    StructureDefinition.class,
                    Files.readString(definitionsPath.resolve("observation-" + i + ".json")));
        assertThat(original.getSnapshot().getElementFirstRep().getMin()).isEqualTo(1);
        assertThat(original.getDifferential().getElementFirstRep().getMin()).isEqualTo(1);
      }
    }

    @Test
    void leavesOtherResourceTypesAndMissingSectionsUnchanged() throws IOException {
      final var context = FhirContext.forR4Cached();
      final var definitionsPath =
          Files.createDirectory(profilesPath.resolve("StructureDefinition"));
      final var patient = new StructureDefinition().setUrl("urn:test:patient").setType("Patient");
      patient
          .getSnapshot()
          .addElement(
              element("Observation.value[x]:valueString.value", "Observation.value[x].value", 1));
      final var observation =
          new StructureDefinition().setUrl("urn:test:observation").setType("Observation");
      Files.writeString(
          definitionsPath.resolve("patient.json"),
          context.newJsonParser().encodeResourceToString(patient));
      Files.writeString(
          definitionsPath.resolve("observation.json"),
          context.newJsonParser().encodeResourceToString(observation));

      final var result = profileParserService.parseProfile(profilesPath);

      final var loadedPatient =
          (StructureDefinition) result.structureDefinitions().get(patient.getUrl());
      assertThat(loadedPatient.getSnapshot().getElementFirstRep().getMin()).isEqualTo(1);
      final var loadedObservation =
          (StructureDefinition) result.structureDefinitions().get(observation.getUrl());
      assertThat(loadedObservation.hasSnapshot()).isFalse();
      assertThat(loadedObservation.hasDifferential()).isFalse();
    }

    private ElementDefinition element(String id, String path, int min) {
      final var element =
          new ElementDefinition().setPath(path).setMin(min).setMax("1").setMustSupport(true);
      element.setId(id);
      return element;
    }
  }

  @Nested
  class FhirPackagePostProcessing {
    @Test
    void duplicateUrlVersionThrowsExceptionForAllTypesOfResources() {
      final var profileParserServiceWithPostprocessing =
          new ProfileParserService(
              FhirContext.forR4Cached(),
              FeatureFlags.builder().fhirPackagePostprocessing(true).build(),
              new CodeSystemConsolidator());

      assertThatThrownBy(
              () ->
                  profileParserServiceWithPostprocessing.parseProfile(
                      DUPLICATE_URL_VERSION_PROFILES_PATH))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Duplicate resource for lookup key")
          .hasMessageContaining("https://demis.rki.de/fhir/ValueSet/DuplicateSameVersion|1.0.0");
    }

    @Test
    void duplicateNonTerminologyUrlWithDifferentVersionThrowsException() {
      final var profileParserServiceWithPostprocessing =
          new ProfileParserService(
              FhirContext.forR4Cached(),
              FeatureFlags.builder().fhirPackagePostprocessing(true).build(),
              new CodeSystemConsolidator());

      assertThatThrownBy(
              () ->
                  profileParserServiceWithPostprocessing.parseProfile(
                      DUPLICATE_NON_TERMINOLOGY_URL_DIFFERENT_VERSION_PROFILES_PATH))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Duplicate resource for lookup key");
    }

    @Test
    void duplicateTerminologyUrlWithDifferentVersionIsAllowed() {
      final var profileParserServiceWithPostprocessing =
          new ProfileParserService(
              FhirContext.forR4Cached(),
              FeatureFlags.builder().fhirPackagePostprocessing(true).build(),
              new CodeSystemConsolidator());

      final var result =
          profileParserServiceWithPostprocessing.parseProfile(
              DUPLICATE_TERMINOLOGY_URL_DIFFERENT_VERSION_PROFILES_PATH);

      assertThat(result.codeSystems())
          .hasSize(3)
          .containsKeys(
              "https://demis.rki.de/fhir/CodeSystem/DuplicateTerminology",
              "https://demis.rki.de/fhir/CodeSystem/DuplicateTerminology|1.0.0",
              "https://demis.rki.de/fhir/CodeSystem/DuplicateTerminology|2.0.0");
    }
  }
}
