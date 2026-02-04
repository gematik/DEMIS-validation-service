package de.gematik.demis.validationservice.services.validation.extension;

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

import static java.util.Map.entry;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableSet;

import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.Strings;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.springframework.stereotype.Component;

@Component
class StructureDefinitionExtensionExtractor {

  public static final AllowedExtensionUrls NO_URLS_ALLOWED = new AllowedExtensionUrls(Map.of());

  private static final String TYPE_CODE_EXTENSION = "Extension";
  private static final String PATH_SUFFIX_URL = ".url";
  private static final String PATH_EXTENSION_URL = TYPE_CODE_EXTENSION + PATH_SUFFIX_URL;

  static Set<String> unionSets(final Set<String> first, final Set<String> second) {
    if (second.isEmpty() || first.containsAll(second)) {
      return first;
    }
    if (first.isEmpty() || second.containsAll(first)) {
      return second;
    }
    final HashSet<String> result = new HashSet<>(first);
    result.addAll(second);
    return Set.copyOf(result);
  }

  private static Map<String, Set<String>> mergeMaps(
      final Map<String, Set<String>> first, final Map<String, Set<String>> second) {
    final var result = new HashMap<>(first);
    second.forEach((k, v) -> result.merge(k, v, StructureDefinitionExtensionExtractor::unionSets));
    return Map.copyOf(result);
  }

  public AllowedExtensionUrls buildAllowedExtensionUrlsMap(
      final StructureDefinition structureDefinition) {
    final List<ElementDefinition> elements = structureDefinition.getSnapshot().getElement();

    // Key: element (extension) Path, Value: Set of allowedUrls
    final Map<String, Set<String>> result =
        mergeMaps(profileUrlsPerPath(elements), fixedUrlsPerPath(elements));

    return new AllowedExtensionUrls(result);
  }

  private Map<String, Set<String>> profileUrlsPerPath(final List<ElementDefinition> elements) {
    return elements.stream()
        .flatMap(
            elementDef ->
                elementDef.getType().stream()
                    .filter(type -> TYPE_CODE_EXTENSION.equals(type.getCode()) && type.hasProfile())
                    .map(type -> entry(elementDef.getPath(), type)))
        .collect(
            groupingBy(
                Map.Entry::getKey,
                flatMapping(
                    entry -> entry.getValue().getProfile().stream().map(PrimitiveType::getValue),
                    toUnmodifiableSet())));
  }

  private Map<String, Set<String>> fixedUrlsPerPath(final List<ElementDefinition> elements) {
    return elements.stream()
        .filter(elementDef -> PATH_EXTENSION_URL.equals(elementDef.getBase().getPath()))
        .filter(ElementDefinition::hasFixedOrPattern)
        .collect(
            groupingBy(
                elementDef -> Strings.CI.removeEnd(elementDef.getPath(), PATH_SUFFIX_URL),
                mapping(elementDef -> elementDef.getFixedOrPattern().primitiveValue(), toSet())));
  }

  public record AllowedExtensionUrls(Map<String, Set<String>> pathUrlsMap) {
    @NotNull
    public Set<String> urlsByExtensionPath(final String path) {
      return pathUrlsMap.getOrDefault(path, Set.of());
    }
  }
}
