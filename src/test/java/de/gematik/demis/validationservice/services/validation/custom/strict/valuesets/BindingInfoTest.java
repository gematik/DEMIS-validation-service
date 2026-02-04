package de.gematik.demis.validationservice.services.validation.custom.strict.valuesets;

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

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BindingInfoTest {

  @Test
  @DisplayName("Should create BindingInfo with empty system list using single-arg constructor")
  void shouldCreateBindingInfoWithEmptyList() {
    BindingInfo bindingInfo = new BindingInfo("someValueSetId");

    assertThat(bindingInfo.expectedSystems()).isEmpty();
    assertThat(bindingInfo.valueSetCanonical()).isEqualTo("someValueSetId");
  }

  @Test
  @DisplayName("Should create BindingInfo with single system using two-arg constructor")
  void shouldCreateBindingInfoWithSingleSystem() {
    BindingInfo bindingInfo = new BindingInfo("someValueSetId", "http://snomed.info/sct");

    assertThat(bindingInfo.expectedSystems()).containsExactly("http://snomed.info/sct");
    assertThat(bindingInfo.valueSetCanonical()).isEqualTo("someValueSetId");
  }

  @Test
  @DisplayName("Should create BindingInfo with null system as empty list")
  void shouldCreateBindingInfoWithNullSystemAsEmptyList() {
    BindingInfo bindingInfo = new BindingInfo("someValueSetId", (String) null);

    assertThat(bindingInfo.expectedSystems()).isEmpty();
  }

  @Test
  @DisplayName("Should create BindingInfo with multiple systems using list constructor")
  void shouldCreateBindingInfoWithMultipleSystems() {
    List<String> systems = List.of("http://snomed.info/sct", "http://loinc.org");
    BindingInfo bindingInfo = new BindingInfo("someValueSetId", systems);

    assertThat(bindingInfo.expectedSystems())
        .containsExactly("http://snomed.info/sct", "http://loinc.org");
  }

  @Test
  @DisplayName("Should apply to any system when no expected systems are defined")
  void shouldApplyToAnySystemWhenNoExpectedSystemsDefined() {
    BindingInfo bindingInfo = new BindingInfo("someValueSetId");

    assertThat(bindingInfo.appliesToSystem("http://snomed.info/sct")).isTrue();
    assertThat(bindingInfo.appliesToSystem("http://loinc.org")).isTrue();
    assertThat(bindingInfo.appliesToSystem("any-system")).isTrue();
    assertThat(bindingInfo.appliesToSystem(null))
        .isTrue(); // even null system applies when no constraint
  }

  @Test
  @DisplayName("Should apply only to matching system when expected system is defined")
  void shouldApplyOnlyToMatchingSystemWhenExpectedSystemDefined() {
    BindingInfo bindingInfo = new BindingInfo("someValueSetId", "http://snomed.info/sct");

    assertThat(bindingInfo.appliesToSystem("http://snomed.info/sct")).isTrue();
    assertThat(bindingInfo.appliesToSystem("http://loinc.org")).isFalse();
    assertThat(bindingInfo.appliesToSystem("other-system")).isFalse();
    assertThat(bindingInfo.appliesToSystem(null)).isFalse();
  }

  @Test
  @DisplayName("Should apply to any of multiple expected systems")
  void shouldApplyToAnyOfMultipleExpectedSystems() {
    List<String> systems = List.of("http://snomed.info/sct", "http://loinc.org");
    BindingInfo bindingInfo = new BindingInfo("someValueSetId", systems);

    assertThat(bindingInfo.appliesToSystem("http://snomed.info/sct")).isTrue();
    assertThat(bindingInfo.appliesToSystem("http://loinc.org")).isTrue();
    assertThat(bindingInfo.appliesToSystem("http://hl7.org/fhir")).isFalse();
  }
}
