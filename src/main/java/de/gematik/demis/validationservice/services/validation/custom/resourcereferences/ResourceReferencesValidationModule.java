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

import ca.uhn.fhir.validation.IValidationContext;
import de.gematik.demis.validationservice.services.validation.custom.DemisValidationModule;
import de.gematik.demis.validationservice.services.validation.custom.ResourceWalker;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

@Slf4j
public class ResourceReferencesValidationModule extends DemisValidationModule {

  private final ResourceWalker resourceWalker = new ResourceWalker();

  public ResourceReferencesValidationModule(final boolean strictSingleBundleValidation) {
    super(strictSingleBundleValidation);
  }

  @Override
  protected void validateBundle(IValidationContext<IBaseResource> theCtx, Bundle bundle) {
    final Map<String, Resource> bundleResourcesIndex = indexResources(bundle);
    resourceWalker.forEachElementDepthFirst(
        bundle,
        elementCtx -> {
          if (elementCtx.element() instanceof Reference reference) {
            validateReference(theCtx, elementCtx, reference, bundleResourcesIndex);
          }
        });
  }

  private void validateReference(
      final IValidationContext<IBaseResource> validationContext,
      final ResourceWalker.ElementCtx elementCtx,
      final Reference reference,
      final Map<String, Resource> resourceIndex) {
    final String referenceValue = reference.getReference();
    if (StringUtils.isBlank(referenceValue) || !isInternalReference(referenceValue)) {
      return;
    }

    final Resource targetResource = resourceIndex.get(referenceValue);
    if (targetResource == null) {
      log.debug(
          "Unresolvable internal reference: {} at {}", referenceValue, elementCtx.locationPath());
      addValidationError(
          validationContext,
          String.format("Reference '%s' is not resolvable", referenceValue),
          elementCtx.locationPath());
    }
  }

  private Map<String, Resource> indexResources(final Bundle bundle) {
    final Map<String, Resource> bundleIndex = new HashMap<>();
    indexResource(bundle, null, bundleIndex);
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.hasResource()) {
        indexResource(entry.getResource(), entry.getFullUrl(), bundleIndex);
      }
    }
    return bundleIndex;
  }

  private void indexResource(
      final Resource resource, final String fullUrl, final Map<String, Resource> index) {
    if (resource == null) {
      return;
    }

    if (StringUtils.isNotBlank(fullUrl)) {
      index.put(fullUrl, resource);
    }

    final String idPart = resource.getIdPart();
    if (StringUtils.isNotBlank(idPart)) {
      index.put(resource.fhirType() + "/" + idPart, resource);
    }

    if (resource instanceof Bundle bundle) {
      for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
        if (entry.hasResource()) {
          indexResource(entry.getResource(), entry.getFullUrl(), index);
        }
      }
    }
  }

  private boolean isInternalReference(final String referenceValue) {
    return referenceValue.startsWith("#")
        || referenceValue.startsWith("urn:")
        || !referenceValue.contains("://");
  }
}
