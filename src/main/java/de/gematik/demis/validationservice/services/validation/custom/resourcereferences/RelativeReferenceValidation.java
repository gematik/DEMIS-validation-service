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
import jakarta.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Validates relative references using the RESTful base of the source Bundle entry.
 *
 * <p>R4 already requires this context-sensitive resolution; a target's matching logical id cannot
 * replace it. Base recognition currently checks URL structure only and still needs strict FHIR
 * RESTful address validation. Version-specific references are resolved by the index using the
 * versionless URL and the target's {@code meta.versionId}.
 */
@RequiredArgsConstructor
@Slf4j
final class RelativeReferenceValidation implements ReferenceValidation {

  private final ValidationMessages validationMessages;
  private final BundleResourceIndex bundleResourceIndex;

  private static @Nullable URI getRestfulBase(final String fullUrl) {
    try {
      final URI uri = new URI(fullUrl);
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getRawAuthority() == null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null) {
        return null;
      }
      final String path = uri.getRawPath();
      if (path == null) {
        return null;
      }
      final String[] segments = path.split("/");
      if (segments.length < 3
          || segments[segments.length - 1].isEmpty()
          || segments[segments.length - 2].isEmpty()) {
        return null;
      }
      final int lastSegmentStart = path.lastIndexOf('/');
      final int resourceTypeStart = path.lastIndexOf('/', lastSegmentStart - 1);
      if (resourceTypeStart < 0) {
        return null;
      }
      final String basePath = path.substring(0, resourceTypeStart + 1);
      return URI.create(uri.getScheme() + "://" + uri.getRawAuthority() + basePath);
    } catch (URISyntaxException | IllegalArgumentException _) {
      return null;
    }
  }

  private static @Nullable String getBaseResourceFullUrl(
      ResourceWalker.ElementCtx referenceContext) {
    ResourceWalker.ElementCtx context = referenceContext;
    while (context != null) {
      if (context.element() instanceof Bundle.BundleEntryComponent entry) {
        return entry.hasFullUrl() ? entry.getFullUrl() : null;
      }
      context = context.parent();
    }
    return null;
  }

  @Override
  public void validate(Reference reference, ResourceWalker.ElementCtx context)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    final String fullUrlReference = createFullUrlReference(reference, context);
    if (fullUrlReference != null) {
      validateFullUrlReference(reference, context, fullUrlReference);
    }
  }

  private String createFullUrlReference(Reference reference, ResourceWalker.ElementCtx context) {
    final String relativeReference = reference.getReference();
    final String baseResourceFullUrl = getBaseResourceFullUrl(context);
    if (baseResourceFullUrl == null) {
      validationMessages.addError(
          String.format(
              "Failed to find base resource of relative reference. Reference: %s",
              relativeReference),
          context.locationPath());
      return null;
    }
    final ReferenceType baseResourceFullUrlType = ReferenceType.from(baseResourceFullUrl);
    if (baseResourceFullUrlType == ReferenceType.URN) {
      validationMessages.addError(
          String.format(
              "Source entry has a URN fullUrl. Unable to resolve relative reference against a RESTful base. Reference: %s BaseResourceFullUrl: %s",
              relativeReference, baseResourceFullUrl),
          context.locationPath());
      return null;
    }
    final URI fullUrlBase = getRestfulBase(baseResourceFullUrl);
    if (fullUrlBase == null) {
      validationMessages.addError(
          String.format(
              "Illegal base resource full URL. Unable to build target resource full URL. Reference: %s BaseResourceFullUrl: %s",
              relativeReference, baseResourceFullUrl),
          context.locationPath());
      return null;
    }
    try {
      return fullUrlBase.resolve(relativeReference).toString();
    } catch (IllegalArgumentException e) {
      validationMessages.addError(
          String.format(
              "Invalid relative reference. Unable to resolve against source entry base. Reference: %s BaseResourceFullUrl: %s Error: %s",
              relativeReference, baseResourceFullUrl, e.getMessage()),
          context.locationPath());
      return null;
    }
  }

  private void validateFullUrlReference(
      Reference reference, ResourceWalker.ElementCtx context, String fullUrlReference)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    final Resource resolvedResource = bundleResourceIndex.lookupFullUrlReference(fullUrlReference);
    if (resolvedResource == null) {
      validationMessages.addError(
          String.format(
              "Referenced resource is missing in FHIR Bundle! Reference: %s ResolvedFullUrl: %s",
              reference.getReference(), fullUrlReference),
          context.locationPath());
    }
  }
}
