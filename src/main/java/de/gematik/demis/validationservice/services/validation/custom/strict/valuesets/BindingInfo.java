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

import java.util.List;

/**
 * Holds binding information for a specific element, including the ValueSet and optional system
 * constraints (patternUri) for slice-specific validation. Multiple systems can be specified to
 * support ValueSets composed of multiple CodeSystems.
 */
public record BindingInfo(String valueSetCanonical, List<String> expectedSystems) {
  BindingInfo(String valueSetCanonical) {
    this(valueSetCanonical, List.of());
  }

  BindingInfo(String valueSetCanonical, String expectedSystem) {
    this(valueSetCanonical, expectedSystem != null ? List.of(expectedSystem) : List.of());
  }

  /**
   * Checks if this binding applies to a coding with the given system. Returns true if no system
   * constraint is defined (empty list) or if the coding's system matches any of the expected
   * systems.
   */
  boolean appliesToSystem(String codingSystem) {
    if (expectedSystems.isEmpty()) {
      return true; // No constraint - applies to all systems
    }
    if (codingSystem == null) {
      return false; // null system never matches expected systems
    }
    return expectedSystems.contains(codingSystem);
  }
}
