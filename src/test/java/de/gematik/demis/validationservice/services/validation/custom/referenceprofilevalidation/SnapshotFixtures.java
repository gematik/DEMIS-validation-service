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

import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r4.model.StringType;

/// Test fixtures for building sliced snapshot elements and matcher inputs.
public final class SnapshotFixtures {
  public ElementDefinition slicingRoot(
      String path, ElementDefinition.DiscriminatorType type, String discriminatorPath) {
    ElementDefinition ed = new ElementDefinition();
    ed.setId(path);
    ed.setPath(path);
    ed.getSlicing().addDiscriminator().setType(type).setPath(discriminatorPath);
    ed.getSlicing().setRules(ElementDefinition.SlicingRules.CLOSED);
    return ed;
  }

  public ElementDefinition sliceHeader(String path, String sliceName) {
    ElementDefinition ed = new ElementDefinition();
    ed.setId(path + ":" + sliceName);
    ed.setPath(path);
    ed.setSliceName(sliceName);
    return ed;
  }

  public ElementDefinition fixedStringUnderSlice(
      String rootPath, String sliceName, String subPath, String value) {
    ElementDefinition ed = new ElementDefinition();
    ed.setId(rootPath + ":" + sliceName + "." + subPath);
    ed.setPath(rootPath + "." + subPath);
    ed.setFixed(new StringType(value));
    return ed;
  }

  public ElementDefinition referenceUnderSlice(
      String rootPath, String sliceName, String subPath, String targetProfile) {
    ElementDefinition ed = new ElementDefinition();
    ed.setId(rootPath + ":" + sliceName + "." + subPath);
    ed.setPath(rootPath + "." + subPath);
    TypeRefComponent type = ed.addType();
    type.setCode("Reference");
    type.addTargetProfile(targetProfile);
    return ed;
  }
}
