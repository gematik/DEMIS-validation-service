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
import lombok.RequiredArgsConstructor;

/**
 * A literal resource reference prepared for matching against {@code Bundle.entry.fullUrl}.
 *
 * <p>The input must already be an absolute resource URL or a URN. Relative RESTful references must
 * first be resolved against the RESTful base of the enclosing Bundle entry. Contained-resource
 * fragments require separate resolution and are not supported by this contract.
 *
 * <p>Unlike an entry's {@code fullUrl}, the input may be a version-specific resource URL ending in
 * {@code /_history/<versionId>}. Matching such a reference requires both the versionless {@link
 * #fullUrl()} and the target resource's {@code meta.versionId}.
 *
 * <p>This class only separates the address and optional version; it neither validates reference
 * syntax nor resolves the target resource.
 *
 * @see <a href="https://hl7.org/fhir/R4/bundle.html#references">FHIR R4 Bundle reference
 *     resolution</a>
 */
@RequiredArgsConstructor
final class BundleResourceReference {

  private static final String VERSION_MARKER = "/_history/";

  private final String resourceReference;

  /**
   * Returns the address to match exactly against {@code Bundle.entry.fullUrl}, removing a terminal
   * version suffix if present. Otherwise, returns the input unchanged.
   */
  String fullUrl() {
    final String versionId = versionId();
    if (versionId == null) {
      return resourceReference;
    }
    return resourceReference.substring(0, resourceReference.lastIndexOf(VERSION_MARKER));
  }

  /**
   * Returns the version identifier from a terminal {@code /_history/<versionId>} suffix, or {@code
   * null} if absent. The identifier is preserved verbatim, without numeric interpretation.
   */
  @Nullable
  String versionId() {
    final int versionIndex = resourceReference.lastIndexOf(VERSION_MARKER);
    if (versionIndex <= 0) {
      return null;
    }

    final String versionId = resourceReference.substring(versionIndex + VERSION_MARKER.length());
    return !versionId.isEmpty() && !versionId.contains("/") ? versionId : null;
  }

  @Override
  public String toString() {
    return resourceReference;
  }
}
