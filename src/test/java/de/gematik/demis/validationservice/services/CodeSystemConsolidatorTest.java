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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.CodeSystemContentMode;
import org.junit.jupiter.api.Test;

class CodeSystemConsolidatorTest {

  private final CodeSystemConsolidator underTest = new CodeSystemConsolidator();

  @Test
  void keepsDistinctVersionsForSameUrl() {
    CodeSystem csA1 = codeSystemFragment("cs-a1", "http://example.org/system-a", "1", "A1");
    CodeSystem csA2 = codeSystemFragment("cs-a2", "http://example.org/system-a", "2", "A2");
    CodeSystem csB1 = codeSystemFragment("cs-b1", "http://example.org/system-b", "1", "B1");

    var consolidated = underTest.consolidateByUrlAndVersion(List.of(csA1, csA2, csB1));

    assertThat(consolidated).hasSize(3);
    assertThat(consolidated)
        .extracting(CodeSystem::getUrl, CodeSystem::getVersion)
        .contains(
            org.assertj.core.groups.Tuple.tuple("http://example.org/system-a", "1"),
            org.assertj.core.groups.Tuple.tuple("http://example.org/system-a", "2"),
            org.assertj.core.groups.Tuple.tuple("http://example.org/system-b", "1"));
  }

  @Test
  void usesCompleteCodeSystemWithoutMergingFragments() {
    CodeSystem complete =
        completeCodeSystem("cs-complete", "http://example.org/system-complete", "1", "COMP");
    CodeSystem fragment =
        codeSystemFragment("cs-fragment", "http://example.org/system-complete", "1", "FRAG");
    CodeSystem example =
        exampleCodeSystem("cs-example", "http://example.org/system-complete", "1", "EXAMPLE");
    CodeSystem notPresent =
        notPresentCodeSystem("cs-not-present", "http://example.org/system-complete", "1");

    var result =
        underTest.consolidateByUrlAndVersion(List.of(fragment, complete, example, notPresent));

    assertThat(result).hasSize(1);
    assertThat(codes(result.getFirst())).containsExactly("COMP");
    assertThat(result.getFirst().getContent()).isEqualTo(CodeSystemContentMode.COMPLETE);
  }

  @Test
  void usesFirstCompleteCodeSystemWhenMultipleCompleteExist() {
    CodeSystem firstComplete =
        completeCodeSystem("cs-complete-1", "http://example.org/system-complete", "2", "FIRST");
    CodeSystem secondComplete =
        completeCodeSystem("cs-complete-2", "http://example.org/system-complete", "2", "SECOND");
    CodeSystem fragment =
        codeSystemFragment("cs-fragment", "http://example.org/system-complete", "2", "FRAG");

    var result =
        underTest.consolidateByUrlAndVersion(List.of(firstComplete, secondComplete, fragment));

    assertThat(result).hasSize(1);
    assertThat(codes(result.getFirst())).containsExactly("FIRST");
    assertThat(result.getFirst().getContent()).isEqualTo(CodeSystemContentMode.COMPLETE);
  }

  @Test
  void mergesFragmentsWithUniqueConcepts() {
    CodeSystem fragment1 =
        codeSystemFragment("cs-snomed-fragment-1", "http://example.org/snomed", "2024", "A");
    CodeSystem fragment2 =
        codeSystemFragment("cs-snomed-fragment-2", "http://example.org/snomed", "2024", "B");
    CodeSystem example =
        exampleCodeSystem("cs-snomed-example", "http://example.org/snomed", "2024", "C");
    CodeSystem notPresent =
        notPresentCodeSystem("cs-snomed-not-present", "http://example.org/snomed", "2024");

    var result =
        underTest.consolidateByUrlAndVersion(List.of(fragment1, fragment2, example, notPresent));
    assertThat(result).hasSize(1);
    assertThat(codes(result.getFirst())).containsExactly("A", "B");
    assertThat(result.getFirst().getContent()).isEqualTo(CodeSystemContentMode.FRAGMENT);
  }

  @Test
  void prefersConceptWithGermanDesignation() {
    CodeSystem withGerman = codeSystemFragment("a", "http://example.org/de", "1", "DUP");
    withGerman.getConceptFirstRep().addDesignation().setLanguage("de").setValue("Deutscher Text");

    CodeSystem withoutGerman = codeSystemFragment("b", "http://example.org/de", "1", "DUP");
    withoutGerman.getConceptFirstRep().addDesignation().setLanguage("en").setValue("English Text");

    var result = underTest.consolidateByUrlAndVersion(List.of(withoutGerman, withGerman));
    assertThat(result).hasSize(1);

    var concept = result.getFirst().getConceptFirstRep();
    assertThat(
            concept.getDesignation().stream()
                .map(CodeSystem.ConceptDefinitionDesignationComponent::getLanguage))
        .contains("de");
  }

  @Test
  void prefersMoreCompleteConceptWhenBothHaveGermanDesignation() {
    CodeSystem lessComplete = codeSystemFragment("a", "http://example.org/de-complete", "1", "DUP");
    lessComplete.getConceptFirstRep().addDesignation().setLanguage("de").setValue("Kurz");

    CodeSystem moreComplete = codeSystemFragment("b", "http://example.org/de-complete", "1", "DUP");
    moreComplete.getConceptFirstRep().setDefinition("Ausführliche Definition");
    moreComplete.getConceptFirstRep().addDesignation().setLanguage("de-CH").setValue("Ausführlich");
    moreComplete
        .getConceptFirstRep()
        .addProperty()
        .setCode("kind")
        .setValue(new org.hl7.fhir.r4.model.StringType("detailed"));

    var result = underTest.consolidateByUrlAndVersion(List.of(lessComplete, moreComplete));
    var concept = result.getFirst().getConceptFirstRep();

    assertThat(concept.getDefinition()).isEqualTo("Ausführliche Definition");
    assertThat(concept.getProperty()).hasSize(1);
  }

  @Test
  void ignoresUnsupportedContentModesWhenNoCompleteOrFragmentExists() {
    CodeSystem notPresent =
        notPresentCodeSystem("cs-not-present", "http://example.org/unsupported", "1");
    CodeSystem example =
        exampleCodeSystem("cs-example", "http://example.org/unsupported", "1", "EXAMPLE");

    var result = underTest.consolidateByUrlAndVersion(List.of(notPresent, example));

    assertThat(result).isEmpty();
  }

  @Test
  void keepsSingleSupplementCodeSystem() {
    CodeSystem supplement =
        supplementCodeSystem("cs-supplement", "http://example.org/supplement", "1", "SUP");

    var result = underTest.consolidateByUrlAndVersion(List.of(supplement));

    assertThat(result).hasSize(1);
    assertThat(codes(result.getFirst())).containsExactly("SUP");
    assertThat(result.getFirst().getContent()).isEqualTo(CodeSystemContentMode.SUPPLEMENT);
  }

  @Test
  void throwsExceptionForDuplicateGroupContainingSupplement() {
    CodeSystem supplement =
        supplementCodeSystem("cs-supplement", "http://example.org/collision", "1", "SUP");
    CodeSystem fragment =
        codeSystemFragment("cs-fragment", "http://example.org/collision", "1", "FRAG");
    var cs = List.of(supplement, fragment);
    assertThatThrownBy(() -> underTest.consolidateByUrlAndVersion(cs))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("content=supplement");
  }

  private static CodeSystem codeSystem(
      String id, String url, String version, CodeSystemContentMode content, String... codes) {

    CodeSystem codeSystem = new CodeSystem();
    codeSystem.setId(id);
    codeSystem.setUrl(url);
    codeSystem.setVersion(version);
    codeSystem.setContent(content);

    for (String code : codes) {
      codeSystem.addConcept().setCode(code);
    }

    return codeSystem;
  }

  private static CodeSystem codeSystemFragment(
      String id, String url, String version, String... codes) {
    return codeSystem(id, url, version, CodeSystemContentMode.FRAGMENT, codes);
  }

  private static CodeSystem completeCodeSystem(
      String id, String url, String version, String... codes) {
    return codeSystem(id, url, version, CodeSystemContentMode.COMPLETE, codes);
  }

  private static CodeSystem notPresentCodeSystem(String id, String url, String version) {
    return codeSystem(id, url, version, CodeSystemContentMode.NOTPRESENT);
  }

  private static CodeSystem exampleCodeSystem(
      String id, String url, String version, String... codes) {
    return codeSystem(id, url, version, CodeSystemContentMode.EXAMPLE, codes);
  }

  private static CodeSystem supplementCodeSystem(
      String id, String url, String version, String... codes) {
    return codeSystem(id, url, version, CodeSystemContentMode.SUPPLEMENT, codes);
  }

  private static List<String> codes(CodeSystem codeSystem) {
    return codeSystem.getConcept().stream()
        .map(CodeSystem.ConceptDefinitionComponent::getCode)
        .toList();
  }
}
