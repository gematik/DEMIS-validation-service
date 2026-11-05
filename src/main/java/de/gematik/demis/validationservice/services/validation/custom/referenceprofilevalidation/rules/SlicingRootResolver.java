package de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.rules;

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

import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SlicingRoot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.Bundle;

/// Assembles the slicing roots applicable to a given Bundle instance.
///
/// The result maps each slicing-root path (e.g. `Composition.section`) to the
/// `SlicingRoot` that governs it. Multiple claimed bundle profiles are unioned
/// by root path; if two profiles disagree on the same root path, the entry from
/// the first matching profile wins (deterministic, driven by claim order).
///
/// An empty result means: no known root profiles claimed, or none of them define
/// slicing roots -> the module should skip slice-aware handling entirely.
public class SlicingRootResolver {

  private final ReferenceProfileRules rules;

  public SlicingRootResolver(ReferenceProfileRules rules) {
    this.rules = rules;
  }

  public Map<String, SlicingRoot> resolve(Bundle bundle) {
    List<String> claimedRoots =
        bundle.getMeta().getProfile().stream()
            .map(IPrimitiveType::getValue)
            .filter(rules.knownRootBundleProfiles()::contains)
            .toList();

    if (claimedRoots.isEmpty()) {
      return Map.of();
    }

    Map<String, SlicingRoot> result = new HashMap<>();
    claimedRoots.stream()
        .map(rules.compositionProfileByBundleProfile()::get)
        .filter(Objects::nonNull)
        .map(rules.slicingRootsByProfileAndPath()::get)
        .filter(Objects::nonNull)
        .forEach(perProfile -> perProfile.forEach(result::putIfAbsent));

    return result;
  }
}
