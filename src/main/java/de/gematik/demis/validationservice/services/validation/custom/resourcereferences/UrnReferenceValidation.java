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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Validates URN references by exact target-entry {@code fullUrl} match.
 *
 * <p>The source entry's address form does not restrict URN resolution, and the target's logical id
 * is not a substitute for its {@code fullUrl}.
 */
@RequiredArgsConstructor
@Slf4j
final class UrnReferenceValidation implements ReferenceValidation {

  private final ValidationMessages validationMessages;
  private final BundleResourceIndex bundleResourceIndex;

  @Override
  public void validate(Reference reference, ResourceWalker.ElementCtx context)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    final String absoluteReference = reference.getReference();
    final Resource resolvedResource = bundleResourceIndex.lookupFullUrlReference(absoluteReference);
    if (resolvedResource == null) {
      validationMessages.addError(
          String.format(
              "Referenced resource is missing in FHIR Bundle! Reference: %s", absoluteReference),
          context.locationPath());
      return;
    }
    log.debug("Validated absolute resource reference of bundle! Reference: {}", absoluteReference);
  }
}
