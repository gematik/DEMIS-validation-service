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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Validates absolute HTTP(S) references against resources available in the notification.
 *
 * <p>An absolute URL can identify an internal Bundle entry. Rejecting an unmatched external target
 * is DEMIS policy; it is not a general FHIR prohibition. No network retrieval is performed.
 * Version-specific references are resolved by the index using the versionless {@code fullUrl} and
 * the target's {@code meta.versionId}.
 */
@RequiredArgsConstructor
@Slf4j
final class HttpReferenceValidation implements ReferenceValidation {

  private final ValidationMessages validationMessages;
  private final BundleResourceIndex bundleResourceIndex;
  private final List<String> trustedExternalReferencesDomains;

  @Override
  public void validate(Reference reference, ResourceWalker.ElementCtx context)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    final String httpReference = reference.getReference();
    final Resource resolvedResource = bundleResourceIndex.lookupFullUrlReference(httpReference);
    if (resolvedResource != null) {
      log.debug(
          "Validated HTTP(S) resource reference as resolvable internal resource! Reference: {}",
          httpReference);
      return;
    }
    if (trustedExternalReferencesDomains.isEmpty()) {
      log.debug(
          "Validated HTTP(S) resource reference as external resource! Reference: {}",
          httpReference);
      return;
    }
    if (isTrustedExternalReference(httpReference)) {
      log.debug(
          "Validated HTTP(S) resource reference as trusted external resource! Reference: {}",
          httpReference);
      return;
    }
    validationMessages.addError(
        String.format(
            "References to untrusted external resources are prohibited FHIR Bundle notifications! Reference: %s",
            httpReference),
        context.locationPath());
  }

  private boolean isTrustedExternalReference(String externalReference) {
    try {
      final URI uri = new URI(externalReference);
      final String host = uri.getHost();
      return uri.isAbsolute()
          && "https".equalsIgnoreCase(uri.getScheme())
          && (host != null)
          && isTrustedExternalReferenceHost(host);
    } catch (URISyntaxException _) {
      return false;
    }
  }

  private boolean isTrustedExternalReferenceHost(String host) {
    return trustedExternalReferencesDomains.stream()
        .map(String::toLowerCase)
        .anyMatch(domain -> hasDomainSuffix(host, domain));
  }

  private boolean hasDomainSuffix(String host, String suffix) {
    return host.equalsIgnoreCase(suffix) || host.toLowerCase(Locale.ROOT).endsWith("." + suffix);
  }
}
