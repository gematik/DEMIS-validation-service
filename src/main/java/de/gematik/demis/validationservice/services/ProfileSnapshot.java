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

import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.Builder;
import org.hl7.fhir.instance.model.api.IBaseResource;

@Builder
public record ProfileSnapshot(
    String name,
    boolean withTerminologyResources,
    @Nonnull Map<String, IBaseResource> structureDefinitions,
    @Nonnull Map<String, IBaseResource> codeSystems,
    @Nonnull Map<String, IBaseResource> valueSets,
    @Nonnull Map<String, IBaseResource> questionnaires) {

  public int getTotalCount() {
    return Stream.of(structureDefinitions, codeSystems, valueSets, questionnaires)
        .mapToInt(Map::size)
        .sum();
  }

  @Override
  public String toString() {
    return String.format(
        "{name=%s, withTerminologyResources=%s, StructureDefinitions=%s, CodeSystems=%s, ValueSets=%s, Questionaires=%s}",
        name,
        withTerminologyResources,
        structureDefinitions.size(),
        codeSystems.size(),
        valueSets.size(),
        questionnaires.size());
  }
}
