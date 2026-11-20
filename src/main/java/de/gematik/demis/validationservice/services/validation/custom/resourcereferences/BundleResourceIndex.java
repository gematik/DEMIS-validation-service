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

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

/**
 * Indexes only a Bundle's direct entries for exact full URL lookup, with version matching and
 * ambiguity detection. Relative references must be resolved against their source context before
 * lookup; see the package documentation.
 *
 * <p>Unlike {@link
 * de.gematik.demis.validationservice.services.validation.custom.BundleResourceIndex}, this index
 * neither flattens nested Bundles nor adds {@code ResourceType/id} aliases.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class BundleResourceIndex {

  private final Map<String, List<Resource>> resourcesByFullUrl;

  static BundleResourceIndex create(Bundle bundle) {
    final Map<String, List<Resource>> resourcesByFullUrl = new HashMap<>();
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      final Resource resource = entry.getResource();
      if ((resource != null) && entry.hasFullUrl()) {
        resourcesByFullUrl
            .computeIfAbsent(entry.getFullUrl(), _ -> new ArrayList<>())
            .add(resource);
      }
    }
    return new BundleResourceIndex(resourcesByFullUrl);
  }

  /**
   * Matches a {@code fullUrl} exactly, additionally matching {@code meta.versionId} for versioned
   * references.
   *
   * @return the unique target, or {@code null} if no entry matches the fullUrl and, if specified,
   *     the requested version
   * @throws AmbiguousResourceReferenceException if more than one entry matches the fullUrl and, if
   *     specified, the requested version
   */
  @Nullable
  Resource lookupFullUrlReference(String fullUrlReference)
      throws AmbiguousResourceReferenceException {
    final BundleResourceReference reference = new BundleResourceReference(fullUrlReference);
    final String fullUrl = reference.fullUrl();
    final List<Resource> resources = this.resourcesByFullUrl.get(fullUrl);
    if (resources == null) {
      return null;
    }
    final String expectedVersionId = reference.versionId();
    if (expectedVersionId != null) {
      return lookupVersionedFullUrlReference(fullUrlReference, expectedVersionId, resources);
    }
    if (resources.size() == 1) {
      return resources.getFirst();
    }
    throw new AmbiguousResourceReferenceException(
        fullUrlReference, "Ambiguous full URL reference: " + fullUrlReference);
  }

  @Nullable
  private Resource lookupVersionedFullUrlReference(
      String versionedFullUrlReference, String expectedVersionId, List<Resource> resources)
      throws AmbiguousResourceReferenceException {
    log.debug("Resolving versioned resource reference: {}", versionedFullUrlReference);
    Resource match = null;
    for (Resource resource : resources) {
      if (resource.hasMeta() && resource.getMeta().hasVersionId()) {
        final String versionId = resource.getMeta().getVersionId();
        if (expectedVersionId.equals(versionId)) {
          if (match != null) {
            throw new AmbiguousResourceReferenceException(
                versionedFullUrlReference,
                "Ambiguous versioned full URL reference: " + versionedFullUrlReference);
          }
          match = resource;
        }
      }
    }
    return match;
  }

  @Getter
  static final class AmbiguousResourceReferenceException extends Exception {
    private final String fullUrlReference;

    AmbiguousResourceReferenceException(String fullUrlReference, String message) {
      super(message);
      this.fullUrlReference = fullUrlReference;
    }
  }
}
