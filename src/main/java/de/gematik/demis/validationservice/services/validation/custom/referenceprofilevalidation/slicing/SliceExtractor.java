package de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing;

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
import org.hl7.fhir.r4.model.*;

/// Extracts slice-level reference-profile rules from a composition profile's snapshot.
///
/// Only a subset of FHIR slicing is supported:
/// - discriminator types are limited to `value` and `pattern`
/// - discriminator paths must be simple dot paths (no FHIRPath expressions)
/// - pattern matching follows the FHIR "is-subset" semantics
/// - `Bundle.entry` slicings that use `type=type` are skipped with a dedicated warning
///
/// Nested slicings under a supported root are silently skipped and only surfaced in
/// the aggregate summary log at the end of extraction.
@Slf4j
public class SliceExtractor {

  public Map<String, SlicingRoot> extract(StructureDefinition compositionProfile) {
    if (!compositionProfile.hasSnapshot()) {
      return Map.of();
    }

    String profileUrl = compositionProfile.getUrl();
    ExtractionState state = new ExtractionState();

    for (ElementDefinition ed : compositionProfile.getSnapshot().getElement()) {
      processElement(ed, profileUrl, state);
    }

    if (state.ctx.isActive()) {
      finalizeRoot(state.ctx, state.result);
    }

    log.info(
        "Slice extraction for {}: {} slicing roots kept, {} nested slicings skipped, "
            + "{} roots skipped due to unsupported discriminator",
        profileUrl,
        state.result.size(),
        state.nestedSkipped,
        state.unsupportedRoots);

    return Map.copyOf(state.result);
  }

  private void processElement(ElementDefinition ed, String profileUrl, ExtractionState state) {
    if (state.ctx.isActive() && !isSelfOrDescendant(ed.getPath(), state.ctx.rootPath)) {
      finalizeRoot(state.ctx, state.result);
      state.ctx = RootContext.none();
    }

    if (ed.hasSlicing()) {
      if (state.ctx.isActive()) {
        state.nestedSkipped++;
        return;
      }

      state.ctx = openRoot(profileUrl, ed);
      if (!state.ctx.supported) {
        state.unsupportedRoots++;
      }
      return;
    }

    if (!state.ctx.isActive() || !state.ctx.supported) {
      return;
    }

    if (ed.hasSliceName() && ed.getPath().equals(state.ctx.rootPath)) {
      state.ctx.openSlice(ed.getSliceName());
      return;
    }

    Optional<String> sliceName = sliceNameFromId(ed.getId(), state.ctx.rootPath);
    if (sliceName.isEmpty()) {
      return;
    }

    SliceBuilder slice = state.ctx.slices.get(sliceName.get());
    if (slice == null) {
      return;
    }

    String relativePath = relativize(ed.getPath(), state.ctx.rootPath);
    handleSliceElement(ed, relativePath, state.ctx.discriminatorTypes, slice);
  }

  private void handleSliceElement(
      ElementDefinition ed,
      String relativePath,
      Map<String, Discriminator.DiscriminatorType> discriminatorTypes,
      SliceBuilder slice) {

    if (discriminatorTypes.containsKey(relativePath)) {
      Object expected = normalizeFixedOrPattern(ed);
      if (expected != null) {
        slice.discriminatorValues.put(relativePath, expected);
      }
    }

    Set<String> targetProfiles = collectTargetProfiles(ed);
    if (!targetProfiles.isEmpty()) {
      slice.allowedProfiles.put(relativePath, targetProfiles);
    }
  }

  private RootContext openRoot(String profileUrl, ElementDefinition ed) {
    String path = ed.getPath();

    if ("Bundle.entry".equals(path)) {
      warnUnsupportedRoot(profileUrl, path, "Bundle.entry uses discriminator type=type");
      return new RootContext(path, false, false, Map.of());
    }

    // Single pass: collect supported paths, warn on unsupported ones.
    Map<String, Discriminator.DiscriminatorType> types = new LinkedHashMap<>();
    boolean allSupported = true;
    for (ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent d :
        ed.getSlicing().getDiscriminator()) {
      if (isSupportedDiscriminatorType(d.getType()) && isSimpleDotPath(d.getPath())) {
        types.put(d.getPath(), fromHapi(d.getType()));
      } else {
        allSupported = false;
        warnUnsupportedRoot(
            profileUrl,
            path,
            "unsupported discriminator (type=" + d.getType() + ", path='" + d.getPath() + "')");
      }
    }

    boolean supported = allSupported;
    boolean closed = ed.getSlicing().getRules() == ElementDefinition.SlicingRules.CLOSED;
    return new RootContext(path, supported, closed, supported ? Map.copyOf(types) : Map.of());
  }

  private void finalizeRoot(RootContext ctx, Map<String, SlicingRoot> result) {
    if (!ctx.supported || ctx.slices.isEmpty()) {
      return;
    }
    List<SliceDef> slices =
        ctx.slices.values().stream()
            .map(sb -> buildSliceDef(sb, ctx.discriminatorTypes))
            .filter(Objects::nonNull)
            .toList();

    if (!slices.isEmpty()) {
      result.put(ctx.rootPath, new SlicingRoot(ctx.rootPath, slices, ctx.closed));
    }
  }

  private SliceDef buildSliceDef(
      SliceBuilder sb, Map<String, Discriminator.DiscriminatorType> discriminatorTypes) {
    // A slice without a full set of discriminator expected values is malformed and is
    // dropped with a warning.
    // Keeping it would produce a non-matching zombie.
    List<Discriminator> discriminators = new ArrayList<>();
    for (Map.Entry<String, Discriminator.DiscriminatorType> e : discriminatorTypes.entrySet()) {
      Object expected = sb.discriminatorValues.get(e.getKey());
      if (expected == null) {
        log.warn(
            "Slice {} is missing an expected value for discriminator {} - slice ignored",
            sb.sliceName,
            e.getKey());
        return null;
      }
      discriminators.add(new Discriminator(e.getValue(), e.getKey(), expected));
    }
    return new SliceDef(sb.sliceName, discriminators, Map.copyOf(sb.allowedProfiles));
  }

  private Object normalizeFixedOrPattern(ElementDefinition ed) {
    Type value = extractFixedOrPatternValue(ed);
    return switch (value) {
      case CodeType c -> c.getValue();
      case StringType s -> s.getValue();
      case UriType u -> u.getValue();
      case BooleanType b -> b.getValue();
      case IntegerType i -> i.getValue();
      case DecimalType d -> d.getValue();
      case Coding coding -> normalizeCoding(coding);
      case CodeableConcept cc -> normalizeCodeableConcept(cc);
      case null -> null;
      default -> {
        log.warn(
            "Unsupported fixed/pattern type {} at {} - discriminator ignored",
            value.fhirType(),
            ed.getPath());
        yield null;
      }
    };
  }

  private Type extractFixedOrPatternValue(ElementDefinition ed) {
    if (ed.hasFixed()) {
      return ed.getFixed();
    } else if (ed.hasPattern()) {
      return ed.getPattern();
    }
    return null;
  }

  private Map<String, Object> normalizeCoding(Coding coding) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (coding.hasSystem()) result.put("system", coding.getSystem());
    if (coding.hasCode()) result.put("code", coding.getCode());
    if (coding.hasVersion()) result.put("version", coding.getVersion());
    return Map.copyOf(result);
  }

  private Map<String, Object> normalizeCodeableConcept(CodeableConcept cc) {
    List<Object> codings = cc.getCoding().stream().map(c -> (Object) normalizeCoding(c)).toList();
    return Map.of("coding", codings);
  }

  private Set<String> collectTargetProfiles(ElementDefinition ed) {
    return ed.getType().stream()
        .filter(t -> "Reference".equals(t.getCode()))
        .flatMap(t -> t.getTargetProfile().stream())
        .filter(CanonicalType::hasValue)
        .map(CanonicalType::getValue)
        .collect(Collectors.toUnmodifiableSet());
  }

  private boolean isSelfOrDescendant(String path, String rootPath) {
    return path.equals(rootPath) || path.startsWith(rootPath + ".");
  }

  private String relativize(String path, String rootPath) {
    return path.substring(rootPath.length() + 1);
  }

  private Optional<String> sliceNameFromId(String id, String rootPath) {
    if (id == null) return Optional.empty();
    int colon = id.indexOf(':', rootPath.length());
    if (colon < 0) return Optional.empty();
    int dot = id.indexOf('.', colon);
    return Optional.of(dot < 0 ? id.substring(colon + 1) : id.substring(colon + 1, dot));
  }

  private boolean isSupportedDiscriminatorType(ElementDefinition.DiscriminatorType type) {
    return type == ElementDefinition.DiscriminatorType.VALUE
        || type == ElementDefinition.DiscriminatorType.PATTERN;
  }

  private boolean isSimpleDotPath(String path) {
    if (path == null || path.isEmpty()) return false;
    return Arrays.stream(path.split("\\.", -1)).allMatch(this::isValidJavaIdentifierSegment);
  }

  private boolean isValidJavaIdentifierSegment(String segment) {
    if (segment.isEmpty()) return false;
    return Character.isJavaIdentifierStart(segment.charAt(0))
        && segment.chars().skip(1).allMatch(Character::isJavaIdentifierPart);
  }

  private Discriminator.DiscriminatorType fromHapi(ElementDefinition.DiscriminatorType type) {
    return type == ElementDefinition.DiscriminatorType.PATTERN
        ? Discriminator.DiscriminatorType.PATTERN
        : Discriminator.DiscriminatorType.VALUE;
  }

  private void warnUnsupportedRoot(String profileUrl, String path, String reason) {
    log.warn("Skipping slicing at {} in profile {}: {}", path, profileUrl, reason);
  }

  private static final class RootContext {
    final String rootPath;
    final boolean supported;
    final boolean closed;
    final Map<String, Discriminator.DiscriminatorType> discriminatorTypes;
    final Map<String, SliceBuilder> slices = new LinkedHashMap<>();

    private RootContext(
        String rootPath,
        boolean supported,
        boolean closed,
        Map<String, Discriminator.DiscriminatorType> discriminatorTypes) {
      this.rootPath = rootPath;
      this.supported = supported;
      this.closed = closed;
      this.discriminatorTypes = discriminatorTypes;
    }

    static RootContext none() {
      return new RootContext("", false, false, Map.of());
    }

    boolean isActive() {
      return !rootPath.isEmpty();
    }

    void openSlice(String name) {
      slices.putIfAbsent(name, new SliceBuilder(name));
    }
  }

  private static final class SliceBuilder {
    final String sliceName;
    final Map<String, Object> discriminatorValues = new LinkedHashMap<>();
    final Map<String, Set<String>> allowedProfiles = new LinkedHashMap<>();

    SliceBuilder(String sliceName) {
      this.sliceName = sliceName;
    }
  }

  private static final class ExtractionState {
    RootContext ctx = RootContext.none();
    final Map<String, SlicingRoot> result = new LinkedHashMap<>();
    int nestedSkipped = 0;
    int unsupportedRoots = 0;
  }
}
