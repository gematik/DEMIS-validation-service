package de.gematik.demis.validationservice.services.validation.custom.resourcereferences;

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

import de.gematik.demis.validationservice.services.validation.custom.ResourceWalker;
import de.gematik.demis.validationservice.services.validation.custom.ValidationMessages;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.jspecify.annotations.NonNull;

/**
 * Validates literal resource references in a FHIR Bundle, including nested Bundles.
 *
 * <p>Each Bundle provides a separate resolution context for references in its entries, using only
 * its direct entries' {@code fullUrl} values for Bundle-local lookup. No fallback to enclosing or
 * nested Bundles is performed. Contained-resource references retain their resource-local scope;
 * unresolved HTTP(S) references are subject to the configured external-reference policy.
 */
@RequiredArgsConstructor
@Slf4j
final class BundleResourceReferencesValidation {

  private final ResourceReferencesBundleValidationConfigProperties configProperties;
  private final ValidationMessages validationMessages;
  private final Bundle rootBundle;
  private final Map<Bundle, BundleReferenceContext> bundleReferenceContexts = new HashMap<>();

  /** Traverses the root Bundle and nested Bundles, reporting reference validation messages. */
  void validate() {
    new ResourceWalker().forEachElementDepthFirst(rootBundle, this::validate);
  }

  private void validate(ResourceWalker.ElementCtx elementCtx) {
    switch (elementCtx.element()) {
      case Bundle bundle -> createAndRegisterBundleReferenceContext(bundle);
      case Reference reference -> validate(elementCtx, reference);
      default -> {
        // ignore other elements
      }
    }
  }

  private void createAndRegisterBundleReferenceContext(Bundle bundle) {
    bundleReferenceContexts.put(bundle, createBundleReferenceContext(bundle));
  }

  private @NonNull BundleReferenceContext createBundleReferenceContext(Bundle bundle) {
    final BundleResourceIndex bundleResourceIndex = BundleResourceIndex.create(bundle);
    final var relativeReferenceValidation =
        new RelativeReferenceValidation(validationMessages, bundleResourceIndex);
    final var urnReferenceValidation =
        new UrnReferenceValidation(validationMessages, bundleResourceIndex);
    final var containedReferenceValidation = new ContainedReferenceValidation(validationMessages);
    final var httpReferenceValidation =
        new HttpReferenceValidation(
            validationMessages, bundleResourceIndex, configProperties.trustedExternalDomains());
    return new BundleReferenceContext(
        validationMessages,
        relativeReferenceValidation,
        urnReferenceValidation,
        containedReferenceValidation,
        httpReferenceValidation);
  }

  private void validate(ResourceWalker.ElementCtx elementCtx, Reference reference) {
    getBundleReferenceContext(elementCtx).validate(elementCtx, reference);
  }

  private @NonNull BundleReferenceContext getBundleReferenceContext(
      ResourceWalker.ElementCtx elementCtx) {
    return Objects.requireNonNull(
        findBundleReferenceContext(elementCtx),
        "Hit a bundle element without parent bundle. This can only be a bug in resource walker.");
  }

  private BundleReferenceContext findBundleReferenceContext(ResourceWalker.ElementCtx elementCtx) {
    final ResourceWalker.ElementCtx parent = elementCtx.parent();
    if (parent != null) {
      if (parent.element() instanceof Bundle containedBundle) {
        return bundleReferenceContexts.get(containedBundle);
      }
      return findBundleReferenceContext(parent);
    }
    return null;
  }

  /** Reference validators sharing one Bundle's resolution context. */
  private record BundleReferenceContext(
      ValidationMessages validationMessages,
      RelativeReferenceValidation relativeReferenceValidation,
      UrnReferenceValidation urnReferenceValidation,
      ContainedReferenceValidation containedReferenceValidation,
      HttpReferenceValidation httpReferenceValidation) {

    private void validate(ResourceWalker.ElementCtx elementCtx, Reference reference) {
      try {
        switch (ReferenceType.from(reference)) {
          case RELATIVE -> relativeReferenceValidation.validate(reference, elementCtx);
          case URN -> urnReferenceValidation.validate(reference, elementCtx);
          case CONTAINED -> containedReferenceValidation.validate(reference, elementCtx);
          case EXTERNAL -> httpReferenceValidation.validate(reference, elementCtx);
          case UNKNOWN ->
              validationMessages.addError(
                  String.format("Unknown reference type! Reference: %s", reference.getReference()),
                  elementCtx.locationPath());
          case EMPTY -> log.debug("Ignoring empty reference at reference.");
        }
      } catch (BundleResourceIndex.AmbiguousResourceReferenceException e) {
        validationMessages.addError(
            String.format(
                "Unable to resolve ambiguous resource reference! Reference: %s Error: %s",
                e.getFullUrlReference(), e.getMessage()),
            elementCtx.locationPath());
      }
    }
  }
}
