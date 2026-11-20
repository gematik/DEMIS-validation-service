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
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * Validates local fragment references in the enclosing container's contained-resource scope.
 *
 * <p>Contained siblings share their container's scope. R4 defines {@code #} as a contained
 * resource's reference back to its container, not as a lookup for an empty contained id.
 */
@RequiredArgsConstructor
@Slf4j
final class ContainedReferenceValidation implements ReferenceValidation {

  private final ValidationMessages validationMessages;

  private static Resource getContainingResource(
      @Nonnull ResourceWalker.ElementCtx referenceContext) {
    ResourceWalker.ElementCtx context = referenceContext;
    while (context != null && !(context.element() instanceof Resource)) {
      context = context.parent();
    }
    if (context == null) {
      return referenceContext.currentResource();
    }

    final Resource resource = (Resource) context.element();
    final ResourceWalker.ElementCtx parent = context.parent();
    if (parent != null
        && parent.element() instanceof DomainResource container
        && container.getContained().stream().anyMatch(contained -> contained == resource)) {
      return container;
    }
    return resource;
  }

  @Override
  public void validate(Reference reference, ResourceWalker.ElementCtx context) {
    final String containedReference = reference.getReference();
    final String containedId = containedReference.substring(1);
    final Resource containingResource = getContainingResource(context);
    Resource resolvedResource = null;
    if (containedId.isEmpty()) {
      if (containingResource != context.currentResource()) {
        resolvedResource = containingResource;
      }
    } else if (containingResource instanceof DomainResource domainResource) {
      resolvedResource =
          domainResource.getContained().stream()
              .filter(contained -> containedId.equals(contained.getIdPart()))
              .findFirst()
              .orElse(null);
    }
    if (resolvedResource == null) {
      validationMessages.addError(
          String.format(
              "Referenced resource is missing in FHIR Bundle! Reference: %s", containedReference),
          context.locationPath());
      return;
    }
    log.debug(
        "Validated contained resource reference of bundle! Reference: {}", containedReference);
  }
}
