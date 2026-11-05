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

import de.gematik.demis.validationservice.services.validation.custom.BundleResourceIndex;
import de.gematik.demis.validationservice.services.validation.custom.ParametersValidationModule;
import de.gematik.demis.validationservice.services.validation.custom.ResourceWalker;
import de.gematik.demis.validationservice.services.validation.custom.ResourceWalker.ElementCtx;
import de.gematik.demis.validationservice.services.validation.custom.ValidationMessages;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.rules.EffectiveReferenceRulesResolver;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.rules.ReferenceProfileRules;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.rules.SlicingRootResolver;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SliceDef;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SliceMatcher;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SlicingRoot;
import java.util.*;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/// Custom DEMIS validator enforcing which FHIR profiles a referenced resource may declare in its
/// `meta.profile` at a given reference path within a [Bundle].
///
/// The base FHIR validator does not constrain *which* profile a referencing element expects
/// at a specific path, as DEMIS profiles do so. For each Bundle-local [Reference], this module
/// checks the target's declared profile claims against the allowed set for that path. A claim is
/// accepted when the claimed profile or any of its ancestors (self-inclusive) is allowed. A target
/// without any profile is an error when a rule applies.
///
/// External references (contained `#...`, absolute `://`) are skipped, and unresolvable references
/// are left to `[ResourceReferencesValidationModule]`.
///
/// Rules come from two sources per Bundle: flat path rules ([EffectiveReferenceRulesResolver]) and
/// slicing-aware rules ([SlicingRootResolver]). A matching [SliceDef] beneath a [SlicingRoot] takes
/// precedence; otherwise the flat rule is the fallback. If no rules apply, validation returns
/// early.
///
/// @see ParametersValidationModule
/// @see ReferenceProfileRules
@Slf4j
public class ReferenceProfileValidationModule extends ParametersValidationModule {

  private final ResourceWalker resourceWalker = new ResourceWalker();
  private final BundleResourceIndex bundleResourceIndex = new BundleResourceIndex();
  private final ReferenceProfileRules rules;
  private final EffectiveReferenceRulesResolver resolver;
  private final SlicingRootResolver slicingRootResolver;
  private final SliceMatcher sliceMatcher = new SliceMatcher();

  public ReferenceProfileValidationModule(ReferenceProfileRules rules) {
    this.rules = rules;
    this.resolver = new EffectiveReferenceRulesResolver(rules);
    this.slicingRootResolver = new SlicingRootResolver(rules);
  }

  private void handleReference(
      ValidationMessages theCtx,
      ElementCtx ctx,
      Reference reference,
      Map<String, Resource> referenceIndex,
      Map<String, Set<String>> effectiveRules,
      Map<String, SlicingRoot> slicingRoots) {
    final String referenceValue = reference.getReference();
    if (isBundleExternalReference(referenceValue)) {
      return;
    }

    final Resource target = referenceIndex.get(referenceValue);
    if (target == null) {
      // Case is handled by ResourceReferencesValidationModule, this validator keeps quiet
      return;
    }

    final Optional<Set<String>> allowedProfiles =
        resolveAllowedProfiles(ctx, effectiveRules, slicingRoots);
    if (allowedProfiles.isEmpty()) {
      return;
    }

    verifyClaims(target, allowedProfiles.get(), ctx, theCtx);

    log.debug(
        "Bundle local reference at {} -> {} (target: {}, claims: {})",
        ctx.locationPath(),
        referenceValue,
        target.fhirType(),
        target.getMeta().getProfile().stream()
            .map(org.hl7.fhir.instance.model.api.IPrimitiveType::getValue)
            .toList());
  }

  /// Resolves the set of profiles allowed at the current reference path.
  ///
  /// Lookup order:
  /// - If the current path lies beneath a known slicing root and the slice matcher identifies a
  /// slice, the slice's own reference rules apply.
  /// - Otherwise, including cases where a slicing root covers the path but no slice matches, the
  /// flat unsliced rule for the path is returned as a fallback.
  ///
  /// @return the allowed profiles, or an empty Optional if no rule applies (treated as skip).
  private Optional<Set<String>> resolveAllowedProfiles(
      ElementCtx ctx,
      Map<String, Set<String>> effectiveRules,
      Map<String, SlicingRoot> slicingRoots) {

    for (Entry<String, SlicingRoot> entry : slicingRoots.entrySet()) {
      Optional<Set<String>> matched = matchSliceRule(ctx, entry.getKey(), entry.getValue());
      if (matched.isPresent()) {
        return matched;
      }
    }
    return Optional.ofNullable(effectiveRules.get(ctx.resourcePath()));
  }

  private Optional<Set<String>> matchSliceRule(
      ElementCtx ctx, String rootPath, SlicingRoot slicingRoot) {
    if (!ctx.resourcePath().startsWith(rootPath + ".")) {
      return Optional.empty();
    }

    Base rootElement = findRootElement(ctx, rootPath);
    if (rootElement == null) {
      return Optional.empty();
    }

    Optional<SliceDef> slice = sliceMatcher.matchSlice(rootElement, slicingRoot);
    if (slice.isEmpty()) {
      return Optional.empty();
    }

    String subPath = ctx.resourcePath().substring(rootPath.length() + 1);
    return Optional.ofNullable(slice.get().allowedProfilesByReferencePath().get(subPath));
  }

  private Base findRootElement(ElementCtx ctx, String rootPath) {
    for (ElementCtx c = ctx.parent(); c != null; c = c.parent()) {
      if (rootPath.equals(c.resourcePath())) {
        return c.element();
      }
    }
    return null;
  }

  // contained ("#") and external ("://) references are not resolvable at this point
  private boolean isBundleExternalReference(final String referenceValue) {
    return referenceValue.startsWith("#") && !referenceValue.contains("://");
  }

  private void verifyClaims(
      final Resource target,
      final Set<String> allowedProfiles,
      final ElementCtx ctx,
      final ValidationMessages messages) {
    final List<String> claims =
        target.getMeta().getProfile().stream()
            .map(org.hl7.fhir.instance.model.api.IPrimitiveType::getValue)
            .toList();

    if (claims.isEmpty()) {
      messages.addError(
          String.format(
              "Referenced resource does not declare any profile. Allowed: %s", allowedProfiles),
          ctx.locationPath());
      return;
    }
    for (final String claim : claims) {
      if (!isDerivedFromAllowed(claim, allowedProfiles)) {
        messages.addError(
            String.format(
                "Referenced resource declares profile '%s' which is not allowed. Allowed: %s",
                claim, allowedProfiles),
            ctx.locationPath());
      }
    }
  }

  private boolean isDerivedFromAllowed(final String claim, Set<String> allowedProfiles) {
    final Set<String> ancestors = rules.profileAncestorsIncludingSelf().get(claim);
    if (ancestors == null) {
      return false;
    }
    return !Collections.disjoint(ancestors, allowedProfiles);
  }

  @Override
  protected void validateIndividualResource(IBaseResource resource, ValidationMessages messages) {
    if (!(resource instanceof Bundle bundle)) {
      return;
    }

    Map<String, Set<String>> effectiveRules = resolver.resolve(bundle);
    Map<String, SlicingRoot> slicingRoots = slicingRootResolver.resolve(bundle);
    if (effectiveRules.isEmpty() && slicingRoots.isEmpty()) {
      return;
    }
    final Map<String, Resource> referenceIndex = bundleResourceIndex.build(bundle);
    resourceWalker.forEachElementDepthFirst(
        bundle,
        ctx -> {
          if (ctx.element() instanceof Reference reference
              && StringUtils.isNotBlank(reference.getReference())) {
            handleReference(messages, ctx, reference, referenceIndex, effectiveRules, slicingRoots);
          }
        });
  }
}
