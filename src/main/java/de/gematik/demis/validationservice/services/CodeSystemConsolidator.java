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

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CodeSystem;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class CodeSystemConsolidator {

  /**
   * Consolidates CodeSystem resources by {@code url|version}, ensuring that at most one usable
   * CodeSystem is returned for each combination of URL and version.
   *
   * <p>Only {@code complete}, {@code fragment}, and {@code supplement} are considered usable.
   * {@code example} and {@code not-present} are ignored.
   *
   * <p>If exactly one usable CodeSystem exists, it is returned as-is. Otherwise, collisions are
   * resolved according to the following rules:
   *
   * <ul>
   *   <li>If a complete CodeSystem exists, it is preferred over fragments.
   *   <li>If no complete CodeSystem exists, fragments are merged by concept code.
   *   <li>A collision involving a {@code supplement} CodeSystem results in an exception.
   * </ul>
   *
   * @param codeSystems CodeSystems to consolidate
   * @return one consolidated CodeSystem per {@code url|version}
   */
  List<CodeSystem> consolidateByUrlAndVersion(List<CodeSystem> codeSystems) {
    Map<GroupKey, List<CodeSystem>> groups =
        codeSystems.stream().collect(Collectors.groupingBy(this::toGroupKey));

    List<CodeSystem> result = new ArrayList<>();

    for (var group : groups.entrySet()) {
      getConsolidatedCodeSystem(group.getKey(), group.getValue()).ifPresent(result::add);
    }

    return List.copyOf(result);
  }

  private Optional<CodeSystem> getConsolidatedCodeSystem(
      GroupKey groupKey, List<CodeSystem> codeSystemsInGroup) {

    logDuplicates(groupKey, codeSystemsInGroup);

    List<CodeSystem> usableCodeSystems =
        codeSystemsInGroup.stream().filter(this::isUsableContent).toList();

    validateNoSupplementDuplicate(groupKey, usableCodeSystems);

    return selectSingleUsableCodeSystem(usableCodeSystems)
        .or(() -> selectCompleteCodeSystem(groupKey, usableCodeSystems))
        .or(() -> mergeFragmentCodeSystems(groupKey, usableCodeSystems))
        .or(() -> noUsableContent(groupKey));
  }

  private void validateNoSupplementDuplicate(GroupKey groupKey, List<CodeSystem> codeSystems) {

    if (codeSystems.size() > 1 && codeSystems.stream().anyMatch(this::isSupplementContent)) {
      throw new IllegalStateException(
          ("Invalid duplicate CodeSystem group for url='%s', version='%s': "
                  + "CodeSystem with content=supplement must not have duplicates.")
              .formatted(groupKey.url(), groupKey.version()));
    }
  }

  private Optional<CodeSystem> selectSingleUsableCodeSystem(List<CodeSystem> usableCodeSystems) {
    if (usableCodeSystems.size() == 1) {
      return Optional.of(usableCodeSystems.getFirst().copy());
    }
    return Optional.empty();
  }

  private Optional<CodeSystem> selectCompleteCodeSystem(
      GroupKey groupKey, List<CodeSystem> codeSystems) {

    List<CodeSystem> completeCodeSystems =
        codeSystems.stream().filter(this::isCompleteContent).toList();

    if (completeCodeSystems.isEmpty()) {
      return Optional.empty();
    }

    if (completeCodeSystems.size() > 1) {
      log.warn(
          "Multiple complete CodeSystems found for url='{}', version='{}'. Using the first one.",
          groupKey.url(),
          groupKey.version());
    }
    return Optional.of(completeCodeSystems.getFirst().copy());
  }

  private Optional<CodeSystem> mergeFragmentCodeSystems(
      GroupKey groupKey, List<CodeSystem> codeSystems) {

    List<CodeSystem> fragments = codeSystems.stream().filter(this::isFragmentContent).toList();

    if (fragments.isEmpty()) {
      return Optional.empty();
    }

    if (fragments.size() == 1) {
      return Optional.of(fragments.getFirst().copy());
    }

    CodeSystem merged = mergeFragments(groupKey, fragments);

    log.info(
        "CodeSystem consolidation strategy=MERGE_FRAGMENTS for url='{}', version='{}': "
            + "fragmentCount={}, mergedConceptCount={}",
        groupKey.url(),
        groupKey.version(),
        fragments.size(),
        merged.getConcept().size());

    return Optional.of(merged);
  }

  private CodeSystem mergeFragments(GroupKey groupKey, List<CodeSystem> fragmentCodeSystems) {

    CodeSystem merged = fragmentCodeSystems.getFirst().copy();
    merged.getConcept().clear();

    Map<String, List<CodeSystem.ConceptDefinitionComponent>> conceptsByCode =
        groupConceptsByCode(fragmentCodeSystems);

    for (var entry : conceptsByCode.entrySet()) {
      List<CodeSystem.ConceptDefinitionComponent> candidates = entry.getValue();

      CodeSystem.ConceptDefinitionComponent concept =
          candidates.size() == 1
              ? candidates.getFirst()
              : choosePreferredConcept(groupKey, candidates);

      merged.addConcept(concept.copy());
    }

    return merged;
  }

  private Map<String, List<CodeSystem.ConceptDefinitionComponent>> groupConceptsByCode(
      List<CodeSystem> codeSystems) {

    Map<String, List<CodeSystem.ConceptDefinitionComponent>> conceptsByCode = new LinkedHashMap<>();

    for (var codeSystem : codeSystems) {
      for (var concept : codeSystem.getConcept()) {
        conceptsByCode
            .computeIfAbsent(concept.getCode(), ignored -> new ArrayList<>())
            .add(concept);
      }
    }

    return conceptsByCode;
  }

  private CodeSystem.ConceptDefinitionComponent choosePreferredConcept(
      GroupKey groupKey, List<CodeSystem.ConceptDefinitionComponent> candidates) {

    CodeSystem.ConceptDefinitionComponent preferred =
        candidates.stream()
            .max(
                Comparator.comparing(this::hasGermanDesignation)
                    .thenComparingInt(this::completenessScore))
            .orElseThrow();

    log.warn(
        "CodeSystem concept code='{}' occurs in {} fragments for url='{}', version='{}'. "
            + "Selected preferred concept based on German designation and completeness.",
        preferred.getCode(),
        candidates.size(),
        groupKey.url(),
        groupKey.version());

    return preferred;
  }

  private boolean hasGermanDesignation(CodeSystem.ConceptDefinitionComponent concept) {

    return concept.getDesignation().stream()
        .map(CodeSystem.ConceptDefinitionDesignationComponent::getLanguage)
        .filter(Objects::nonNull)
        .map(language -> language.toLowerCase(Locale.ROOT))
        .anyMatch(language -> language.equals("de") || language.startsWith("de-"));
  }

  /**
   * Calculates a completeness score for a CodeSystem concept.
   *
   * <p>Each populated optional concept element contributes one point: {@code display}, {@code
   * definition}, {@code designation}, {@code property}, and nested {@code concept}.
   *
   * @param concept the concept to evaluate
   * @return the number of populated optional concept elements
   */
  private int completenessScore(CodeSystem.ConceptDefinitionComponent concept) {

    int score = 0;

    if (concept.hasDisplay() && !concept.getDisplay().isBlank()) {
      score++;
    }

    if (concept.hasDefinition() && !concept.getDefinition().isBlank()) {
      score++;
    }

    if (concept.hasDesignation()) {
      score++;
    }

    if (concept.hasProperty()) {
      score++;
    }

    if (concept.hasConcept()) {
      score++;
    }

    return score;
  }

  private boolean isUsableContent(CodeSystem codeSystem) {
    return isCompleteContent(codeSystem)
        || isFragmentContent(codeSystem)
        || isSupplementContent(codeSystem);
  }

  private boolean isCompleteContent(CodeSystem codeSystem) {
    return codeSystem.getContent() == CodeSystem.CodeSystemContentMode.COMPLETE;
  }

  private boolean isFragmentContent(CodeSystem codeSystem) {
    return codeSystem.getContent() == CodeSystem.CodeSystemContentMode.FRAGMENT;
  }

  private boolean isSupplementContent(CodeSystem codeSystem) {
    return codeSystem.getContent() == CodeSystem.CodeSystemContentMode.SUPPLEMENT;
  }

  private Optional<CodeSystem> noUsableContent(GroupKey groupKey) {
    log.warn(
        "No usable CodeSystem content found for url='{}', version='{}'.",
        groupKey.url(),
        groupKey.version());

    return Optional.empty();
  }

  private void logDuplicates(GroupKey groupKey, List<CodeSystem> codeSystems) {

    if (codeSystems.size() <= 1) {
      return;
    }

    Map<CodeSystem.CodeSystemContentMode, Long> countByContent =
        codeSystems.stream()
            .collect(Collectors.groupingBy(CodeSystem::getContent, Collectors.counting()));

    log.info(
        "Multiple CodeSystems detected for url='{}', version='{}': count={}, content={}.",
        groupKey.url(),
        groupKey.version(),
        codeSystems.size(),
        countByContent);
  }

  private GroupKey toGroupKey(CodeSystem codeSystem) {
    return new GroupKey(codeSystem.getUrl(), codeSystem.getVersion());
  }

  private record GroupKey(String url, String version) {}
}
