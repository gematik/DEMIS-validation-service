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

import static de.gematik.demis.validationservice.services.validation.extension.StructureDefinitionExtensionExtractor.unionSets;
import static java.nio.file.Files.readString;
import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.demis.validationservice.services.validation.extension.StructureDefinitionExtensionExtractor.AllowedExtensionUrls;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StructureDefinitionExtensionExtractorTest {

  private static final Path STRUCTURE_DEFINITION_PATH =
      Path.of("src/test/resources/extension/StructureDefinition");

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final StructureDefinitionExtensionExtractor underTest =
      new StructureDefinitionExtensionExtractor();

  private static Map<String, Set<String>> loadExpected(final Path file) throws IOException {
    return OBJECT_MAPPER.readValue(file.toFile(), new TypeReference<>() {});
  }

  @ParameterizedTest
  @CsvSource({
    // extension urls via type profiles
    "StructureDefinition-NotifiedPerson.json, StructureDefinition-NotifiedPerson-Expected-Extension-Urls.json",
    // extension urls via fixed urls
    "StructureDefinition-Observation.json, StructureDefinition-Observation-Expected-Extension-Urls.json"
  })
  void extract(final String input, final String expected) throws Exception {
    final var expectedUrls = loadExpected(STRUCTURE_DEFINITION_PATH.resolve(expected));
    final String structureDefinitionJsonString =
        readString(STRUCTURE_DEFINITION_PATH.resolve(input));
    final StructureDefinition structureDefinition =
        FhirContext.forR4Cached()
            .newJsonParser()
            .parseResource(StructureDefinition.class, structureDefinitionJsonString);

    final AllowedExtensionUrls result = underTest.buildAllowedExtensionUrlsMap(structureDefinition);

    assertThat(result.pathUrlsMap()).containsExactlyInAnyOrderEntriesOf(expectedUrls);
  }

  @Test
  void emptyStructureDefinition() {
    final StructureDefinition structureDefinition = new StructureDefinition();
    final AllowedExtensionUrls result = underTest.buildAllowedExtensionUrlsMap(structureDefinition);
    assertThat(result).isNotNull();
    assertThat(result.pathUrlsMap()).isEmpty();
  }

  @Nested
  class UnionSets {

    @Test
    void unionSets_oneSetIsEmpty() {
      final Set<String> first = Set.of();
      final var second = Set.of("some_value");
      assertThat(unionSets(first, second)).isSameAs(second);
      assertThat(unionSets(second, first)).isSameAs(second);
    }

    @Test
    void unionSets_setIsFullyContained() {
      final Set<String> first = Set.of("some_value", "same_value");
      final var second = Set.of("same_value");
      assertThat(unionSets(first, second)).isSameAs(first);
      assertThat(unionSets(second, first)).isSameAs(first);
    }

    @Test
    void unionSets_differentSetUnitedToOneNew() {
      final Set<String> first = Set.of("some_value", "same_value");
      final var second = Set.of("same_value", "other_value");
      final var expected = Set.of("some_value", "same_value", "other_value");
      assertThat(unionSets(first, second))
          .containsExactlyInAnyOrderElementsOf(expected)
          .isUnmodifiable();
      assertThat(unionSets(second, first))
          .containsExactlyInAnyOrderElementsOf(expected)
          .isUnmodifiable();
    }
  }
}
