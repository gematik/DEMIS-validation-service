package de.gematik.demis.validationservice.services.validation.custom.strict.valuesets;

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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.FhirTerser;
import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.IValidatorModule;
import ca.uhn.fhir.validation.SingleValidationMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;

/**
 * Strict validator module: validates that a Coding is a member of the bound ValueSet (via its
 * expansion). Emits ERROR if the code is not contained. HAPI FHIR does not return an error for an
 * unknown or wrong code on a bound value set when the corresponding code system is fragmented. we
 * have to validate this through a custom validator module.
 */
public class StrictValueSetMembershipValidator implements IValidatorModule {

  /** stores all value sets */
  private final Map<String, ValueSet> valueSetMap;

  /** stores all structure definitions */
  private final Map<String, StructureDefinition> structureDefinitionMap;

  // Canonical prefix for DEMIS-specific ValueSets
  private static final String DEMIS_VALUESET_PREFIX = "https://demis.rki.de/fhir/ValueSet/";

  /**
   * Constructs the validator with preloaded ValueSets and StructureDefinitions so terminology and
   * structural constraints can be applied without remote resolution. This ensures deterministic,
   * offline validation aligned with the profiles declared in resource meta.
   */
  public StrictValueSetMembershipValidator(
      Map<String, IBaseResource> valueSetMap, Map<String, IBaseResource> structureDefinitionMap) {
    var tmpMapForValueSets = new HashMap<String, ValueSet>();
    valueSetMap.entrySet().stream()
        .filter(e -> e.getValue() instanceof ValueSet)
        .forEach(e -> tmpMapForValueSets.put(e.getKey(), (ValueSet) e.getValue()));
    this.valueSetMap = Collections.unmodifiableMap(tmpMapForValueSets);

    var tmpMapForStructureDefinitions = new HashMap<String, StructureDefinition>();
    structureDefinitionMap.entrySet().stream()
        .filter(e -> e.getValue() instanceof StructureDefinition)
        .forEach(
            e -> tmpMapForStructureDefinitions.put(e.getKey(), (StructureDefinition) e.getValue()));
    this.structureDefinitionMap = Collections.unmodifiableMap(tmpMapForStructureDefinitions);
  }

  /**
   * Validates a resource against REQUIRED bindings of its declared profile. This enforces strict
   * terminology conformance by ensuring all coded elements adhere to the bound ValueSets, which is
   * critical for interoperability and consistent semantics across systems. *
   *
   * @param context
   */
  @Override
  public void validateResource(IValidationContext<IBaseResource> context) {
    IBaseResource resource = context.getResource();
    if (resource instanceof Parameters parameters) {
      Bundle bundle = (Bundle) parameters.getParameter().getFirst().getResource();
      validateBundleEntries(context, bundle);
    } else if (resource instanceof Bundle bundle) {
      validateBundleEntries(context, bundle);
    }
  }

  /** just a helper to step through entries of a bundle */
  private void validateBundleEntries(IValidationContext<IBaseResource> context, Bundle bundle) {
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      validateResource(context, entry.getResource());
    }
  }

  private void validateResource(IValidationContext<IBaseResource> context, IBaseResource resource) {
    // If no profile is declared in meta, there's nothing to validate
    if (resource.getMeta() == null
        || resource.getMeta().getProfile() == null
        || resource.getMeta().getProfile().isEmpty()) {
      return;
    }
    // Resolve StructureDefinition via the first declared profile (canonical URL)
    String profileCanonical = resource.getMeta().getProfile().getFirst().getValueAsString();
    StructureDefinition sd = this.structureDefinitionMap.get(profileCanonical);
    if (sd == null) {
      // Without a StructureDefinition we can't evaluate paths and bindings
      return;
    }

    // Collect all element paths with REQUIRED bindings including bound ValueSet canonical URL from
    // the StructureDefinition
    Map<String, List<BindingInfo>> requiredBindings = collectRequiredBindings(sd);
    if (requiredBindings.isEmpty()) {
      return; // Nothing to validate
    }

    // For each path, validate codings in the resource against the bound ValueSet
    FhirContext fhirContext = FhirContext.forR4Cached();
    var terser = fhirContext.newTerser();

    requiredBindings.forEach(
        (path, bindingInfos) -> validatePath(context, resource, terser, path, bindingInfos));
  }

  /**
   * Identifies all element paths in the StructureDefinition that declare REQUIRED terminology
   * bindings. This targets only coded data types to enforce mandatory value set membership where
   * profiles demand strict semantic alignment. For sliced elements, also captures the expected
   * system (patternUri) to ensure validation only applies to matching codings.
   */
  private Map<String, List<BindingInfo>> collectRequiredBindings(
      StructureDefinition structureDefinition) {
    Map<String, List<BindingInfo>> result = new HashMap<>();
    if (structureDefinition.hasSnapshot() && structureDefinition.getSnapshot().hasElement()) {
      for (ElementDefinition ed : structureDefinition.getSnapshot().getElement()) {
        if (ed.hasBinding()
            && ed.getBinding().hasStrength()
            && ed.getBinding().getStrength() == Enumerations.BindingStrength.REQUIRED
            && ed.getBinding().hasValueSet()
            && isCodedElement(ed)) {
          String canonical = ed.getBinding().getValueSet();
          if (shouldStrictlyValidate(canonical)) {
            // Find the expected system (patternUri) for this slice
            String expectedSystem = findExpectedSystemForSlice(structureDefinition, ed);
            BindingInfo bindingInfo = new BindingInfo(canonical, expectedSystem);
            result.computeIfAbsent(ed.getPath(), k -> new ArrayList<>()).add(bindingInfo);
          }
        }
      }
    }
    return result;
  }

  /**
   * Finds the expected CodeSystem (patternUri or fixedUri) for a sliced element by looking for the
   * .system child element. This allows slice-specific validation where only codings with matching
   * systems are checked against the slice's ValueSet.
   */
  private String findExpectedSystemForSlice(
      StructureDefinition sd, ElementDefinition sliceElement) {
    // If the element has no sliceName, it's not a slice
    if (!sliceElement.hasSliceName()) {
      return null;
    }

    // Build the expected ID for the .system element
    // e.g., "Observation.interpretation.coding:interpretationSNOMED" ->
    // "Observation.interpretation.coding:interpretationSNOMED.system"
    String sliceId = sliceElement.getId();
    String systemId = sliceId + ".system";

    // Look for the .system element definition with matching ID
    for (ElementDefinition ed : sd.getSnapshot().getElement()) {
      if (systemId.equals(ed.getId())) {
        // Check for patternUri or fixedUri
        if (ed.hasPattern() && ed.getPattern() instanceof org.hl7.fhir.r4.model.UriType uriType) {
          return uriType.getValue();
        }
        if (ed.hasFixed() && ed.getFixed() instanceof org.hl7.fhir.r4.model.UriType uriType) {
          return uriType.getValue();
        }
      }
    }
    return null;
  }

  private boolean shouldStrictlyValidate(String canonical) {
    if (canonical == null) {
      return false;
    }
    Predicate<String> isDemis = c -> c.startsWith(DEMIS_VALUESET_PREFIX);
    if (isDemis.test(canonical)) {
      return true;
    }
    // For non-DEMIS: strictly validate only when local ValueSet exists with expansion
    // (we need the expansion to perform the check)
    ValueSet vs = resolveValueSet(canonical);
    return vs != null && vs.hasExpansion() && vs.getExpansion().hasContains();
  }

  /**
   * Enforces bound terminology for a specific element path by checking all codings in the resource
   * against the declared ValueSet. This guarantees that required bindings are honored, preventing
   * invalid or out-of-scope codes from passing validation. For sliced elements, only validates
   * codings whose system matches the slice's expected system (patternUri).
   */
  private void validatePath(
      IValidationContext<IBaseResource> context,
      IBaseResource resource,
      FhirTerser terser,
      String elementPath,
      List<BindingInfo> bindingInfos) {

    List<IBase> values = safeGetValues(terser, resource, elementPath);
    for (IBase v : values) {
      List<Coding> codings = extractCodings(v);
      for (Coding coding : codings) {
        validateCodingAgainstBindings(context, coding, bindingInfos, elementPath);
      }
    }
  }

  /**
   * Validates a single coding against applicable bindings, checking system constraints and ValueSet
   * membership.
   */
  private void validateCodingAgainstBindings(
      IValidationContext<IBaseResource> context,
      Coding coding,
      List<BindingInfo> bindingInfos,
      String elementPath) {

    List<BindingInfo> applicableBindings = findApplicableBindings(coding, bindingInfos);

    if (applicableBindings.isEmpty()) {
      // No binding applies to this coding (e.g., wrong system for all slices)
      return;
    }

    List<ValueSet> boundVs =
        applicableBindings.stream()
            .map(bi -> resolveValueSet(bi.valueSetCanonical()))
            .filter(Objects::nonNull)
            .toList();

    if (boundVs.isEmpty()) {
      reportMissingValueSets(context, applicableBindings, elementPath);
      return;
    }

    if (!isCodingInValueSetExpansion(coding, boundVs)) {
      reportCodeNotInValueSet(context, coding, boundVs, elementPath);
    }
  }

  private void reportMissingValueSets(
      IValidationContext<IBaseResource> context,
      List<BindingInfo> applicableBindings,
      String elementPath) {
    String vsCanonicals =
        applicableBindings.stream()
            .map(BindingInfo::valueSetCanonical)
            .collect(java.util.stream.Collectors.joining(", "));
    addMessage(
        context,
        ca.uhn.fhir.validation.ResultSeverityEnum.WARNING,
        "Bound ValueSet not found: " + vsCanonicals + " for path " + elementPath,
        elementPath);
  }

  private void reportCodeNotInValueSet(
      IValidationContext<IBaseResource> context,
      Coding coding,
      List<ValueSet> boundVs,
      String elementPath) {
    String valueSetNames =
        boundVs.stream()
            .map(vs -> vs.getUrl() != null ? vs.getUrl() : vs.getId())
            .collect(java.util.stream.Collectors.joining(", "));
    addMessage(
        context,
        ca.uhn.fhir.validation.ResultSeverityEnum.ERROR,
        String.format(
            "Code '%s' (system '%s') is not contained in any of the bound ValueSets [%s] for path '%s'",
            nullSafe(coding.getCode()), nullSafe(coding.getSystem()), valueSetNames, elementPath),
        elementPath);
  }

  /**
   * Filters bindings to only those applicable to the given coding based on the expected systems
   * (patternUri). If a binding has no expected system constraints, it applies to all codings. If it
   * has expected systems, it only applies when the coding's system matches one of them.
   */
  private List<BindingInfo> findApplicableBindings(Coding coding, List<BindingInfo> bindingInfos) {
    String codingSystem = nullSafe(coding.getSystem());
    return bindingInfos.stream().filter(bi -> bi.appliesToSystem(codingSystem)).toList();
  }

  /**
   * Creates and records a validation message for the current context. This centralizes reporting to
   * keep error handling consistent and focused on semantic conformance rather than structural
   * details.
   */
  private void addMessage(
      IValidationContext<IBaseResource> context,
      ca.uhn.fhir.validation.ResultSeverityEnum severity,
      String text,
      String locationPath) {
    SingleValidationMessage msg = new SingleValidationMessage();
    msg.setSeverity(severity);
    msg.setMessage(text);
    msg.setLocationString(locationPath);
    context.addValidationMessage(msg);
  }

  /**
   * Determines whether a StructureDefinition element represents a coded value subject to
   * terminology binding. This limits enforcement to types where value sets govern permissible
   * codes, supporting reliable semantics.
   */
  private boolean isCodedElement(ElementDefinition ed) {
    if (!ed.hasType()) return false;
    for (ElementDefinition.TypeRefComponent t : ed.getType()) {
      String code = t.getCode();
      if ("CodeableConcept".equals(code) || "Coding".equals(code) || "code".equals(code)) {
        return true;
      }
      // Identifier.type is a CodeableConcept, Quantity.code is a code – covered above.
      // If the type is Identifier or Quantity, check specific sub-paths.
      if ("Identifier".equals(code) || "Quantity".equals(code)) {
        String path = ed.getPath();
        if (path != null && (path.endsWith(".type") || path.endsWith(".code"))) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Reads values from the resource for a given path defensively. This avoids hard failures on
   * complex paths while ensuring that terminology checks proceed wherever data is present.
   */
  private List<IBase> safeGetValues(FhirTerser terser, IBaseResource resource, String path) {
    try {
      List<IBase> values = terser.getValues(resource, path);
      return values != null ? values : List.of();
    } catch (Exception e) {
      return List.of();
    }
  }

  /**
   * Extracts all codings represented by the given value. This normalizes different coded types into
   * a uniform set of codings, enabling consistent terminology enforcement across heterogeneous
   * elements.
   */
  private List<Coding> extractCodings(IBase value) {
    List<Coding> result = new ArrayList<>();
    if (value instanceof CodeableConcept cc) {
      result.addAll(cc.getCoding());
    } else if (value instanceof Coding coding) {
      result.add(coding);
    } else if (value instanceof CodeType ct) {
      // For plain code type without system, only the code can be compared; system stays empty
      Coding c = new Coding();
      c.setCode(ct.getValue());
      result.add(c);
    }
    return result;
  }

  /**
   * Resolves the declared ValueSet so that bound terminology can be checked locally. This favors
   * canonical lookups and falls back to URL/id matching, supporting robust offline validation
   * scenarios.
   */
  private ValueSet resolveValueSet(String canonicalOrUrl) {
    if (canonicalOrUrl == null) return null;
    ValueSet vs = valueSetMap.get(canonicalOrUrl);
    if (vs != null) return vs;
    for (ValueSet v : valueSetMap.values()) {
      if (canonicalOrUrl.equals(v.getUrl()) || canonicalOrUrl.equals(v.getId())) {
        return v;
      }
    }
    return null;
  }

  /**
   * Checks strict membership of a coding within the ValueSet expansion. This enforces REQUIRED
   * bindings by rejecting codes not explicitly contained, ensuring predictable, interoperable
   * semantics. Both code and system must match, except for primitive code types where no system is
   * available.
   */
  private boolean isCodingInValueSetExpansion(Coding coding, List<ValueSet> valueSetList) {
    return valueSetList.stream().anyMatch(vs -> isCodingInValueSetExpansion(coding, vs));
  }

  private boolean isCodingInValueSetExpansion(Coding coding, ValueSet vs) {
    if (vs == null) return false;
    if (!vs.hasExpansion() || !vs.getExpansion().hasContains()) {
      return false;
    }
    String code = nullSafe(coding.getCode());
    String system = nullSafe(coding.getSystem());

    // For primitive code types (no system), only compare code
    boolean isPrimitiveCode = system.isEmpty();

    for (ValueSet.ValueSetExpansionContainsComponent contains : vs.getExpansion().getContains()) {
      String cCode = nullSafe(contains.getCode());
      String cSystem = nullSafe(contains.getSystem());

      if (isPrimitiveCode) {
        // For primitive codes, only check code match
        if (code.equals(cCode)) {
          return true;
        }
      } else {
        // For Coding/CodeableConcept, both code and system must match
        if (code.equals(cCode) && system.equals(cSystem)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Provides a null-safe string for comparisons and message formatting. This avoids incidental
   * null-handling issues that could obscure terminology errors.
   */
  private String nullSafe(String s) {
    return s == null ? "" : s;
  }
}
