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

import static de.gematik.demis.validationservice.util.ResourceFileConstants.EMPTY_PROFILES_PATH;
import static de.gematik.demis.validationservice.util.ResourceFileConstants.MINIMLAL_PROFILES_PATH;
import static de.gematik.demis.validationservice.util.ResourceFileConstants.NOT_EXISTING_PROFILES_PATH;
import static de.gematik.demis.validationservice.util.ResourceFileConstants.TERMINOLOGY_PROFILES_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.validationservice.services.terminology.remote.TerminologyServerConfigProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProfileParserServiceTest {

  private final TerminologyServerConfigProperties terminologyServerConfigProperties =
      Mockito.mock(TerminologyServerConfigProperties.class);
  private final ProfileParserService profileParserService =
      new ProfileParserService(FhirContext.forR4Cached(), terminologyServerConfigProperties);

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

  @Test
  void featureFlagRemoteTerminologyServerEnabled() {
    Mockito.when(terminologyServerConfigProperties.enabled()).thenReturn(true);
    final var result = profileParserService.parseProfile(TERMINOLOGY_PROFILES_PATH);
    assertThat(result).isNotNull();
    final var structureDefinitions = result.structureDefinitions();
    assertThat(structureDefinitions).isNotNull().hasSize(1);
    assertThat(result.valueSets()).isEmpty();
    assertThat(result.codeSystems()).isEmpty();
    assertThat(result.withTerminologyResources()).isFalse();
  }

  @Test
  void featureFlagRemoteTerminologyServerDisabled() {
    Mockito.when(terminologyServerConfigProperties.enabled()).thenReturn(false);
    final var result = profileParserService.parseProfile(TERMINOLOGY_PROFILES_PATH);
    assertThat(result.valueSets()).isNotNull().isNotEmpty();
    assertThat(result.codeSystems()).isNotNull().isNotEmpty();
    assertThat(result.withTerminologyResources()).isTrue();
  }
}
