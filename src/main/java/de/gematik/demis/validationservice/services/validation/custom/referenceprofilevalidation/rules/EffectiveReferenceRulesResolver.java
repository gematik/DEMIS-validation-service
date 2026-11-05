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

import java.util.*;
import org.hl7.fhir.r4.model.Bundle;

public class EffectiveReferenceRulesResolver {

  private final ReferenceProfileRules rules;

  public EffectiveReferenceRulesResolver(ReferenceProfileRules rules) {
    this.rules = rules;
  }

  /// Returns the effective rules (path -> allowed target profiles) for the given Bundle instance.
  /// Union of paths, intersection of values across all matching root bundle profiles.
  /// An empty map means: no known root profiles claimed -> module should not validate anything.
  public Map<String, Set<String>> resolve(Bundle bundle) {
    List<String> claimedRoots =
        bundle.getMeta().getProfile().stream()
            .map(org.hl7.fhir.instance.model.api.IPrimitiveType::getValue)
            .filter(rules.knownRootBundleProfiles()::contains)
            .toList();

    if (claimedRoots.isEmpty()) {
      return Map.of();
    }

    List<Map<String, Set<String>>> perRoot =
        claimedRoots.stream()
            .map(rules.compositionProfileByBundleProfile()::get)
            .filter(java.util.Objects::nonNull)
            .map(rules.allowedProfilesByProfileAndPath()::get)
            .filter(java.util.Objects::nonNull)
            .toList();

    if (perRoot.isEmpty()) {
      return Map.of();
    }

    return combineByPathUnionValueIntersection(perRoot);
  }

  private Map<String, Set<String>> combineByPathUnionValueIntersection(
      List<Map<String, Set<String>>> perRoot) {
    Map<String, Set<String>> result = new HashMap<>();
    for (Map<String, Set<String>> ruleMap : perRoot) {
      for (Map.Entry<String, Set<String>> entry : ruleMap.entrySet()) {
        result.merge(
            entry.getKey(),
            new HashSet<>(entry.getValue()),
            (existing, incoming) -> {
              existing.retainAll(incoming);
              return existing;
            });
      }
    }
    return result;
  }
}
