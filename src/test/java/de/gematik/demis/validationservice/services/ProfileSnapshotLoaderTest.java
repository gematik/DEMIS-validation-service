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

import static de.gematik.demis.validationservice.util.ResourceFileConstants.TERMINOLOGY_PROFILES_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import java.util.List;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileSnapshotLoaderTest {

  @Mock private CodeSystemConsolidator consolidator;

  private ProfileParserService profileParserService;

  @BeforeEach
  void setUp() {
    profileParserService = new ProfileParserService(FhirContext.forR4Cached(), consolidator);
    lenient()
        .when(consolidator.consolidateByUrlAndVersion(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void loadResourcesForStructureDefinitionsUsesOnlyUnversionedLookupKeys() {
    final var loader = profileParserService.new ProfileSnapshotLoader(TERMINOLOGY_PROFILES_PATH);
    final var result = loader.loadResources(StructureDefinition.class, "StructureDefinition");

    assertThat(result).isNotEmpty();
    assertThat(result.keySet()).allMatch(key -> !key.contains("|"));
    assertThat(result.values()).allMatch(StructureDefinition.class::isInstance);
  }

  @Test
  void loadResourcesForValueSetsUsesVersionedAndUnversionedLookupKeys() {
    final var loader = profileParserService.new ProfileSnapshotLoader(TERMINOLOGY_PROFILES_PATH);
    final var result = loader.loadResources(ValueSet.class, ProfileParserService.FOLDER_VALUE_SET);

    assertThat(result).isNotEmpty();
    assertThat(result.keySet()).anyMatch(key -> key.contains("|"));
    assertThat(result.keySet()).anyMatch(key -> !key.contains("|"));
    assertThat(result.values()).allMatch(ValueSet.class::isInstance);
  }

  @Test
  void loadResourcesForCodeSystemsWithoutFeatureFlagDoesNotUseConsolidator() {
    final var loader = profileParserService.new ProfileSnapshotLoader(TERMINOLOGY_PROFILES_PATH);

    final var result =
        loader.loadResources(CodeSystem.class, ProfileParserService.FOLDER_CODESYSTEM);

    assertThat(result).isNotEmpty();
    verify(consolidator, never()).consolidateByUrlAndVersion(anyList());
  }

  @Test
  void loadResourcesForCodeSystemsWithFeatureFlagUsesConsolidatorResult() {
    profileParserService = new ProfileParserService(FhirContext.forR4Cached(), consolidator, true);
    final var loader = profileParserService.new ProfileSnapshotLoader(TERMINOLOGY_PROFILES_PATH);
    final CodeSystem consolidated =
        codeSystemFragment("cs-a2", "http://example.org/system-a", "2", "A2");
    when(consolidator.consolidateByUrlAndVersion(anyList())).thenReturn(List.of(consolidated));

    final var result =
        loader.loadResources(CodeSystem.class, ProfileParserService.FOLDER_CODESYSTEM);

    assertThat(result)
        .containsOnlyKeys("http://example.org/system-a", "http://example.org/system-a|2");
    assertThat(result.get("http://example.org/system-a")).isSameAs(consolidated);
    assertThat(result.get("http://example.org/system-a|2")).isSameAs(consolidated);
  }

  private static CodeSystem codeSystemFragment(
      String id, String url, String version, String... codes) {
    final CodeSystem codeSystem = new CodeSystem();
    codeSystem.setId(id);
    codeSystem.setUrl(url);
    codeSystem.setVersion(version);
    codeSystem.setContent(CodeSystemContentMode.FRAGMENT);
    for (String code : codes) {
      codeSystem.addConcept().setCode(code);
    }
    return codeSystem;
  }
}
