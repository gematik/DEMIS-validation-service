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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;

class EffectiveReferenceRulesResolverTest {

  private static final String DEMIS = "https://demis.rki.de/fhir/StructureDefinition/";

  private static final String BUNDLE_A = DEMIS + "NotificationBundleA";
  private static final String BUNDLE_B = DEMIS + "NotificationBundleB";
  private static final String COMPOSITION_A = DEMIS + "NotificationA";
  private static final String COMPOSITION_B = DEMIS + "NotificationB";

  private static final String NOTIFIED_PERSON = DEMIS + "NotifiedPerson";
  private static final String NOTIFIED_PERSON_ANON = DEMIS + "NotifiedPersonAnonymous";
  private static final String NOTIFIER_ROLE = DEMIS + "NotifierRole";
  private static final String INFECT_PROTECT = DEMIS + "InfectProtectFacility";

  @Test
  void shouldReturnEmptyMapWhenBundleHasNoMetaProfile() {
    ReferenceProfileRules rules =
        new ReferenceProfileRules(
            Set.of(BUNDLE_A),
            Map.of(COMPOSITION_A, Map.of("Composition.subject", Set.of(NOTIFIED_PERSON))),
            Map.of(BUNDLE_A, COMPOSITION_A),
            Map.of(),
            Map.of());

    Map<String, Set<String>> effective =
        new EffectiveReferenceRulesResolver(rules).resolve(bundleWithProfiles());

    assertThat(effective).isEmpty();
  }

  @Test
  void shouldReturnEmptyMapWhenNoClaimedProfileIsKnown() {
    ReferenceProfileRules rules =
        new ReferenceProfileRules(
            Set.of(BUNDLE_A),
            Map.of(COMPOSITION_A, Map.of("Composition.subject", Set.of(NOTIFIED_PERSON))),
            Map.of(BUNDLE_A, COMPOSITION_A),
            Map.of(),
            Map.of());

    Map<String, Set<String>> effective =
        new EffectiveReferenceRulesResolver(rules)
            .resolve(bundleWithProfiles("http://example.org/fhir/StructureDefinition/Foreign"));

    assertThat(effective).isEmpty();
  }

  @Test
  void shouldReturnRulesOfMatchingRoot() {
    ReferenceProfileRules rules =
        new ReferenceProfileRules(
            Set.of(BUNDLE_A),
            Map.of(
                COMPOSITION_A,
                Map.of(
                    "Composition.subject", Set.of(NOTIFIED_PERSON),
                    "Composition.author", Set.of(NOTIFIER_ROLE))),
            Map.of(BUNDLE_A, COMPOSITION_A),
            Map.of(),
            Map.of());

    Map<String, Set<String>> effective =
        new EffectiveReferenceRulesResolver(rules).resolve(bundleWithProfiles(BUNDLE_A));

    assertThat(effective)
        .containsEntry("Composition.subject", Set.of(NOTIFIED_PERSON))
        .containsEntry("Composition.author", Set.of(NOTIFIER_ROLE))
        .hasSize(2);
  }

  @Test
  void shouldReturnEmptyMapWhenKnownRootHasNoCompositionHop() {
    ReferenceProfileRules rules =
        new ReferenceProfileRules(
            Set.of(BUNDLE_A),
            Map.of(COMPOSITION_A, Map.of("Composition.subject", Set.of(NOTIFIED_PERSON))),
            Map.of(), // Hop fehlt
            Map.of(),
            Map.of());

    Map<String, Set<String>> effective =
        new EffectiveReferenceRulesResolver(rules).resolve(bundleWithProfiles(BUNDLE_A));

    assertThat(effective).isEmpty();
  }

  @Test
  void shouldIntersectValuesForOverlappingPath() {
    ReferenceProfileRules rules =
        new ReferenceProfileRules(
            Set.of(BUNDLE_A, BUNDLE_B),
            Map.of(
                COMPOSITION_A,
                Map.of("Composition.subject", Set.of(NOTIFIED_PERSON, NOTIFIED_PERSON_ANON)),
                COMPOSITION_B,
                Map.of("Composition.subject", Set.of(NOTIFIED_PERSON))),
            Map.of(BUNDLE_A, COMPOSITION_A, BUNDLE_B, COMPOSITION_B),
            Map.of(),
            Map.of());

    Map<String, Set<String>> effective =
        new EffectiveReferenceRulesResolver(rules).resolve(bundleWithProfiles(BUNDLE_A, BUNDLE_B));

    assertThat(effective).containsEntry("Composition.subject", Set.of(NOTIFIED_PERSON));
  }

  @Test
  void shouldUnionPathsFromDifferentRoots() {
    ReferenceProfileRules rules =
        new ReferenceProfileRules(
            Set.of(BUNDLE_A, BUNDLE_B),
            Map.of(
                COMPOSITION_A,
                Map.of("Composition.subject", Set.of(NOTIFIED_PERSON)),
                COMPOSITION_B,
                Map.of("Composition.author", Set.of(NOTIFIER_ROLE))),
            Map.of(BUNDLE_A, COMPOSITION_A, BUNDLE_B, COMPOSITION_B),
            Map.of(),
            Map.of());

    Map<String, Set<String>> effective =
        new EffectiveReferenceRulesResolver(rules).resolve(bundleWithProfiles(BUNDLE_A, BUNDLE_B));

    assertThat(effective)
        .containsEntry("Composition.subject", Set.of(NOTIFIED_PERSON))
        .containsEntry("Composition.author", Set.of(NOTIFIER_ROLE))
        .hasSize(2);
  }

  @Test
  void shouldMixIntersectionAndPathUnion() {
    ReferenceProfileRules rules =
        new ReferenceProfileRules(
            Set.of(BUNDLE_A, BUNDLE_B),
            Map.of(
                COMPOSITION_A,
                Map.of(
                    "Composition.subject", Set.of(NOTIFIED_PERSON, NOTIFIED_PERSON_ANON),
                    "Composition.author", Set.of(NOTIFIER_ROLE)),
                COMPOSITION_B,
                Map.of(
                    "Composition.subject", Set.of(NOTIFIED_PERSON),
                    "Composition.section.entry", Set.of(INFECT_PROTECT))),
            Map.of(BUNDLE_A, COMPOSITION_A, BUNDLE_B, COMPOSITION_B),
            Map.of(),
            Map.of());

    Map<String, Set<String>> effective =
        new EffectiveReferenceRulesResolver(rules).resolve(bundleWithProfiles(BUNDLE_A, BUNDLE_B));

    assertThat(effective)
        .containsEntry("Composition.subject", Set.of(NOTIFIED_PERSON))
        .containsEntry("Composition.author", Set.of(NOTIFIER_ROLE))
        .containsEntry("Composition.section.entry", Set.of(INFECT_PROTECT))
        .hasSize(3);
  }

  private Bundle bundleWithProfiles(String... profileUrls) {
    Bundle bundle = new Bundle();
    for (String url : profileUrls) {
      bundle.getMeta().addProfile(url);
    }
    return bundle;
  }
}
