package de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation;

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

import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.Discriminator;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SliceDef;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SlicingRoot;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Composition.SectionComponent;

/// Test fixtures for building slice rule inputs (`SlicingRoot`, `SliceDef`) and
/// HAPI runtime elements used to exercise `SliceMatcher`.
public final class SliceMatcherFixtures {

  public SlicingRoot slicingRoot(SliceDef... slices) {
    return new SlicingRoot("Composition.section", List.of(slices), true);
  }

  public SliceDef valueSlice(String name, String path, Object expected) {
    return new SliceDef(
        name,
        List.of(new Discriminator(Discriminator.DiscriminatorType.VALUE, path, expected)),
        Map.of());
  }

  public SectionComponent sectionWithCode(String system, String code) {
    SectionComponent section = new SectionComponent();
    section.getCode().addCoding().setSystem(system).setCode(code);
    return section;
  }

  public Discriminator valueDiscriminator(String path, Object expected) {
    return new Discriminator(Discriminator.DiscriminatorType.VALUE, path, expected);
  }

  public SliceDef slice(String name, Discriminator... discriminators) {
    return new SliceDef(name, List.of(discriminators), Map.of());
  }

  public SectionComponent sectionWithCodes(String system, String... codes) {
    SectionComponent section = new SectionComponent();
    for (String code : codes) {
      section.getCode().addCoding().setSystem(system).setCode(code);
    }
    return section;
  }

  public SliceDef patternSlice(String name, String path, Object expected) {
    return new SliceDef(
        name,
        List.of(new Discriminator(Discriminator.DiscriminatorType.PATTERN, path, expected)),
        Map.of());
  }
}
