package de.gematik.demis.validationservice.services;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.Test;

class ProfileParserServiceTest {

  private static final Path MINIMLAL_PROFILES_PATH =
      Paths.get("src/test/resources/profiles/minimal");
  private static final Path EMPTY_PROFILES_PATH = Paths.get("src/test/resources/profiles/empty");
  private static final Path NOT_EXISTING_PROFILES_PATH =
      Paths.get("src/test/resources/profiles/not_exists");

  private final ProfileParserService profileParserService =
      new ProfileParserService(FhirContext.forR4Cached());

  @Test
  void okay() {
    final Map<Class<? extends MetadataResource>, Map<String, IBaseResource>> result =
        profileParserService.parseProfile(MINIMLAL_PROFILES_PATH);
    assertThat(result).isNotNull();
    final var structureDefinitions = result.get(StructureDefinition.class);
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
}
