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

import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SliceExtractor;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SlicingRoot;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;

@Slf4j
public class ReferenceProfileRulesBuilder {
  private static final String DEMIS_NAMESPACE_PREFIX =
      "https://demis.rki.de/fhir/StructureDefinition/";
  private static final String NOTIFICATION_BUNDLE_URL =
      DEMIS_NAMESPACE_PREFIX + "NotificationBundle";
  private static final String NOTIFICATION_ENTRY_SLICE_ID = "Bundle.entry:notification.resource";

  private final SliceExtractor sliceExtractor = new SliceExtractor();

  private Map<String, Map<String, Set<String>>> deepCopyImmutable(
      Map<String, Map<String, Set<String>>> src) {
    return src.entrySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                e ->
                    e.getValue().entrySet().stream()
                        .collect(
                            Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, inner -> Set.copyOf(inner.getValue())))));
  }

  public ReferenceProfileRules build(Map<String, IBaseResource> structureDefinitions) {
    Map<String, StructureDefinition> sds = filterStructureDefinitions(structureDefinitions);

    Set<String> rootBundles = deriveRootBundleWhitelist(sds);
    Map<String, String> compositionByBundle = deriveCompositionHops(rootBundles, sds);
    Map<String, StructureDefinition> validCompositions =
        resolveValidCompositions(compositionByBundle.values(), sds);
    Map<String, Map<String, Set<String>>> rulesByProfile = deriveReferenceRules(validCompositions);
    Map<String, Map<String, SlicingRoot>> slicingRoots = deriveSlicingRoots(validCompositions);
    Map<String, Set<String>> ancestors = deriveProfileAncestors(sds);

    log.info(
        "Reference profile rules built: {} root bundle profiles, {} composition profiles, {} rules total, {} slicing roots, {} ancestor chains",
        rootBundles.size(),
        compositionByBundle.size(),
        countRulesForDiagnostics(rulesByProfile),
        countSlicingRootsForDiagnostics(slicingRoots),
        ancestors.size());

    return new ReferenceProfileRules(
        Set.copyOf(rootBundles),
        deepCopyImmutable(rulesByProfile),
        Map.copyOf(compositionByBundle),
        Map.copyOf(ancestors),
        Map.copyOf(slicingRoots));
  }

  private Map<String, StructureDefinition> resolveValidCompositions(
      Collection<String> compositionProfiles, Map<String, StructureDefinition> sds) {
    Map<String, StructureDefinition> valid = new LinkedHashMap<>();
    for (String url : Set.copyOf(compositionProfiles)) {
      StructureDefinition sd = sds.get(url);
      if (sd == null) {
        log.warn("No StructureDefinition for composition profile {}, skipping", url);
        continue;
      }

      if (!sd.hasSnapshot()) {
        log.warn("No snapshot for composition profile {}, skipping", url);
        continue;
      }

      valid.put(url, sd);
    }
    return valid;
  }

  private Map<String, Set<String>> deriveProfileAncestors(Map<String, StructureDefinition> sds) {
    Map<String, Set<String>> result = new HashMap<>();
    for (StructureDefinition sd : sds.values()) {
      String url = sd.getUrl();
      if (url == null || !url.startsWith(DEMIS_NAMESPACE_PREFIX)) {
        continue;
      }
      result.put(url, computeAncestorsForDemisProfile(sd, sds));
    }
    log.info("Derived ancestor chains for {} DEMIS profiles", result.size());
    return result;
  }

  private Set<String> computeAncestorsForDemisProfile(
      StructureDefinition start, Map<String, StructureDefinition> sds) {
    Set<String> chain = new LinkedHashSet<>();
    StructureDefinition current = start;

    while (current != null) {
      String url = current.getUrl();
      if (url == null) {
        return Set.copyOf(chain);
      }

      if (!chain.add(url)) {
        break; // only remaining loop exit: cycle protection
      }

      String base = current.getBaseDefinition();
      if (base == null || !base.startsWith(DEMIS_NAMESPACE_PREFIX)) {
        return Set.copyOf(chain);
      }

      StructureDefinition next = sds.get(base);
      if (next == null) {
        log.warn(
            "Unresolved DEMIS baseDefinition '{}' for profile '{}' - ancestor chain truncated",
            base,
            url);
        return Set.copyOf(chain);
      }

      current = next;
    }

    return Set.copyOf(chain);
  }

  private long countRulesForDiagnostics(Map<String, Map<String, Set<String>>> rulesByProfile) {
    return rulesByProfile.values().stream().mapToLong(Map::size).sum();
  }

  private long countSlicingRootsForDiagnostics(Map<String, Map<String, SlicingRoot>> slicingRoots) {
    return slicingRoots.values().stream().mapToInt(Map::size).sum();
  }

  private Map<String, StructureDefinition> filterStructureDefinitions(
      Map<String, IBaseResource> structureDefinitions) {
    return structureDefinitions.entrySet().stream()
        .filter(e -> e.getValue() instanceof StructureDefinition)
        .collect(
            Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> (StructureDefinition) e.getValue()));
  }

  // First run
  private Set<String> deriveRootBundleWhitelist(Map<String, StructureDefinition> sds) {
    // Kandidaten einsammeln
    Map<String, String> demisBundleBaseByUrl =
        sds.values().stream()
            .filter(sd -> "Bundle".equals(sd.getType()))
            .filter(sd -> sd.getUrl() != null && sd.getUrl().startsWith(DEMIS_NAMESPACE_PREFIX))
            .filter(StructureDefinition::hasBaseDefinition)
            .collect(
                Collectors.toMap(
                    StructureDefinition::getUrl, StructureDefinition::getBaseDefinition));

    // Fixpunkt-Iteration: alle, die (transitiv) von NotificationBundle abgeleitet sind
    Set<String> whitelist = createWhitelist(demisBundleBaseByUrl);
    log.info("Derived {} root bundle profiles for reference validation", whitelist.size());
    return whitelist;
  }

  private Set<String> createWhitelist(Map<String, String> demisBundleBaseByUrl) {
    Set<String> whitelist = new HashSet<>();
    boolean changed = true;
    while (changed) {
      changed = false;
      for (Map.Entry<String, String> e : demisBundleBaseByUrl.entrySet()) {
        String url = e.getKey();
        String base = e.getValue();
        if (!whitelist.contains(url)
            && (NOTIFICATION_BUNDLE_URL.equals(base) || whitelist.contains(base))) {
          whitelist.add(url);
          changed = true;
        }
      }
    }
    return whitelist;
  }

  // Second run
  private Map<String, String> deriveCompositionHops(
      Set<String> rootBundles, Map<String, StructureDefinition> sds) {
    Map<String, String> hops = new HashMap<>();
    for (String bundleUrl : rootBundles) {
      Optional<String> compositionProfile = resolveCompositionProfile(bundleUrl, sds);
      compositionProfile.ifPresent(profile -> hops.put(bundleUrl, profile));
    }
    log.info("Derived {} bundle-to-composition hops", hops.size());
    return hops;
  }

  private Optional<String> resolveCompositionProfile(
      String bundleUrl, Map<String, StructureDefinition> sds) {
    StructureDefinition sd = sds.get(bundleUrl);
    if (sd == null) {
      log.warn("No StructureDefinition for bundle profile {}, skipping composition hop", bundleUrl);
      return Optional.empty();
    }

    if (!sd.hasSnapshot()) {
      log.warn("No snapshot for bundle profile {}, skipping composition hop", bundleUrl);
      return Optional.empty();
    }

    Optional<String> compositionProfile = findCompositionProfileAtNotificationSlot(sd);
    if (compositionProfile.isEmpty()) {
      log.warn("Could not derive composition profile for bundle {}", bundleUrl);
    }
    return compositionProfile;
  }

  private Optional<String> findCompositionProfileAtNotificationSlot(StructureDefinition sd) {
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (!NOTIFICATION_ENTRY_SLICE_ID.equals(ed.getId())) {
        continue;
      }

      return ed.getType().stream()
          .filter(type -> "Composition".equals(type.getCode()))
          .filter(type -> !type.getProfile().isEmpty())
          .map(type -> type.getProfile().getFirst().getValue())
          .findFirst();
    }
    return Optional.empty();
  }

  // Third run
  private Map<String, Map<String, Set<String>>> deriveReferenceRules(
      Map<String, StructureDefinition> compositions) {
    Map<String, Map<String, Set<String>>> rulesByProfile = new HashMap<>();
    compositions.forEach(
        (url, sd) -> {
          Map<String, Set<String>> rules = extractUnslicedReferenceRules(sd);
          if (!rules.isEmpty()) {
            rulesByProfile.put(url, rules);
          }
        });
    return rulesByProfile;
  }

  private Map<String, Map<String, SlicingRoot>> deriveSlicingRoots(
      Map<String, StructureDefinition> compositions) {
    Map<String, Map<String, SlicingRoot>> result = new HashMap<>();
    compositions.forEach(
        (url, sd) -> {
          Map<String, SlicingRoot> roots = sliceExtractor.extract(sd);
          if (!roots.isEmpty()) {
            result.put(url, roots);
          }
        });
    return result;
  }

  private Map<String, Set<String>> extractUnslicedReferenceRules(StructureDefinition sd) {
    Map<String, Set<String>> result = new HashMap<>();
    Set<String> slicingRoots = collectSlicingRoots(sd);
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      Set<String> allowed = collectTargetProfiles(ed);

      boolean relevant =
          !ed.hasSliceName()
              && !ed.hasSlicing()
              && !isUnderSlicingRoot(ed.getPath(), slicingRoots)
              && !allowed.isEmpty();

      if (relevant) {
        Set<String> previous = result.putIfAbsent(ed.getPath(), Set.copyOf(allowed));
        if (previous != null) {
          log.warn(
              "Duplicate reference rule for path {} in profile {} - keeping first entry. previous={}, ignored={}",
              ed.getPath(),
              sd.getUrl(),
              previous,
              allowed);
        }
      }
    }
    return result;
  }

  private Set<String> collectSlicingRoots(StructureDefinition sd) {
    return sd.getSnapshot().getElement().stream()
        .filter(ElementDefinition::hasSlicing)
        .map(ElementDefinition::getPath)
        .collect(Collectors.toSet());
  }

  private boolean isUnderSlicingRoot(String path, Set<String> slicingRoots) {
    return slicingRoots.stream().anyMatch(root -> path.startsWith(root + "."));
  }

  private Set<String> collectTargetProfiles(ElementDefinition ed) {
    Set<String> targets = new HashSet<>();
    for (ElementDefinition.TypeRefComponent type : ed.getType()) {
      if ("Reference".equals(type.getCode())) {
        for (CanonicalType tp : type.getTargetProfile()) {
          if (tp.hasValue()) targets.add(tp.getValue());
        }
      }
    }
    return targets;
  }
}
