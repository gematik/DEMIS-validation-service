package de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.SliceMatcherFixtures;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SliceMatcherTest {

  private static final String SYSTEM =
      "https://demis.rki.de/fhir/CodeSystem/notificationSectionType";

  private final SliceMatcher matcher = new SliceMatcher();
  private final SliceMatcherFixtures sliceMatcherFixtures = new SliceMatcherFixtures();

  static Stream<Arguments> valueDiscriminatorCases() {
    return Stream.of(
        arguments("single coding matches", List.of("pathogenDetection"), true),
        arguments(
            "one of multiple codings matches",
            List.of("somethingElse", "pathogenDetection", "another"),
            true),
        arguments("value does not match", List.of("somethingElse"), false));
  }

  static Stream<Arguments> patternDiscriminatorCases() {
    return Stream.of(
        arguments(
            "pattern matches single coding",
            Map.of("coding", List.of(Map.of("system", SYSTEM, "code", "pathogenDetection"))),
            List.of("pathogenDetection", "extra"),
            true),
        arguments(
            "pattern matches multiple codings when all are present",
            Map.of(
                "coding",
                List.of(
                    Map.of("system", SYSTEM, "code", "A"), Map.of("system", SYSTEM, "code", "B"))),
            List.of("A", "B"),
            true),
        arguments(
            "pattern does not match when one coding is missing",
            Map.of(
                "coding",
                List.of(
                    Map.of("system", SYSTEM, "code", "A"), Map.of("system", SYSTEM, "code", "B"))),
            List.of("A"),
            false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("valueDiscriminatorCases")
  void shouldMatchValueDiscriminator(String description, List<String> codes, boolean shouldMatch) {
    SlicingRoot root =
        sliceMatcherFixtures.slicingRoot(
            sliceMatcherFixtures.valueSlice(
                "pathogenDetection", "code.coding.code", "pathogenDetection"));
    SectionComponent section =
        sliceMatcherFixtures.sectionWithCodes(SYSTEM, codes.toArray(String[]::new));

    var match = matcher.matchSlice(section, root);

    if (shouldMatch) {
      assertThat(match)
          .isPresent()
          .get()
          .extracting(SliceDef::sliceName)
          .isEqualTo("pathogenDetection");
    } else {
      assertThat(match).isEmpty();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("patternDiscriminatorCases")
  void shouldMatchPatternDiscriminator(
      String description,
      Map<String, Object> expectedPattern,
      List<String> codes,
      boolean shouldMatch) {
    SlicingRoot root =
        sliceMatcherFixtures.slicingRoot(
            sliceMatcherFixtures.patternSlice("sliceName", "code", expectedPattern));
    SectionComponent section =
        sliceMatcherFixtures.sectionWithCodes(SYSTEM, codes.toArray(String[]::new));

    var match = matcher.matchSlice(section, root);

    if (shouldMatch) {
      assertThat(match).isPresent().get().extracting(SliceDef::sliceName).isEqualTo("sliceName");
    } else {
      assertThat(match).isEmpty();
    }
  }

  @Test
  void shouldReturnEmptyWhenNoSlicesDefined() {
    SlicingRoot root = sliceMatcherFixtures.slicingRoot();
    SectionComponent section = sliceMatcherFixtures.sectionWithCode(SYSTEM, "pathogenDetection");

    assertThat(matcher.matchSlice(section, root)).isEmpty();
  }

  @Test
  void shouldMatchWhenAllDiscriminatorsMatch() {
    SlicingRoot root =
        sliceMatcherFixtures.slicingRoot(
            sliceMatcherFixtures.slice(
                "pathogenDetection",
                sliceMatcherFixtures.valueDiscriminator("code.coding.system", SYSTEM),
                sliceMatcherFixtures.valueDiscriminator("code.coding.code", "pathogenDetection")));
    SectionComponent section = sliceMatcherFixtures.sectionWithCode(SYSTEM, "pathogenDetection");

    assertThat(matcher.matchSlice(section, root))
        .isPresent()
        .get()
        .extracting(SliceDef::sliceName)
        .isEqualTo("pathogenDetection");
  }

  @Test
  void shouldNotMatchWhenNotAllDiscriminatorsMatch() {
    SlicingRoot root =
        sliceMatcherFixtures.slicingRoot(
            sliceMatcherFixtures.slice(
                "pathogenDetection",
                sliceMatcherFixtures.valueDiscriminator("code.coding.system", SYSTEM),
                sliceMatcherFixtures.valueDiscriminator("code.coding.code", "pathogenDetection")));
    SectionComponent section = sliceMatcherFixtures.sectionWithCode(SYSTEM, "somethingElse");

    assertThat(matcher.matchSlice(section, root)).isEmpty();
  }

  @Test
  void shouldReturnFirstMatchingSlice() {
    SlicingRoot root =
        sliceMatcherFixtures.slicingRoot(
            sliceMatcherFixtures.valueSlice("first", "code.coding.system", SYSTEM),
            sliceMatcherFixtures.valueSlice("second", "code.coding.system", SYSTEM));
    SectionComponent section = sliceMatcherFixtures.sectionWithCode(SYSTEM, "anyCode");

    assertThat(matcher.matchSlice(section, root))
        .isPresent()
        .get()
        .extracting(SliceDef::sliceName)
        .isEqualTo("first");
  }
}
