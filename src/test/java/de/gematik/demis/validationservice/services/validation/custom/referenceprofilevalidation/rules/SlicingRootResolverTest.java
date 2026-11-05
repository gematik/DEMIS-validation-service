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

import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SlicingRoot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;

class SlicingRootResolverTest {

  private static final String DEMIS = "https://demis.rki.de/fhir/StructureDefinition/";
  private static final String BUNDLE_A = DEMIS + "NotificationBundleA";
  private static final String COMPOSITION_A = DEMIS + "NotificationA";

  @Test
  void shouldReturnSlicingRootsOfMatchingCompositionProfile() {
    SlicingRoot sectionRoot = new SlicingRoot("Composition.section", List.of(), true);

    ReferenceProfileRules rules =
        new ReferenceProfileRules(
            Set.of(BUNDLE_A),
            Map.of(),
            Map.of(BUNDLE_A, COMPOSITION_A),
            Map.of(),
            Map.of(COMPOSITION_A, Map.of("Composition.section", sectionRoot)));

    Map<String, SlicingRoot> resolved =
        new SlicingRootResolver(rules).resolve(bundleWithProfiles(BUNDLE_A));

    assertThat(resolved).containsEntry("Composition.section", sectionRoot).hasSize(1);
  }

  private Bundle bundleWithProfiles(String... profileUrls) {
    Bundle bundle = new Bundle();
    for (String url : profileUrls) {
      bundle.getMeta().addProfile(url);
    }
    return bundle;
  }
}
