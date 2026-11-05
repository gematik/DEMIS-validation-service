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

import de.gematik.demis.validationservice.services.validation.custom.BundleResourceIndex;
import de.gematik.demis.validationservice.services.validation.custom.ParametersValidationModule;
import de.gematik.demis.validationservice.services.validation.custom.ResourceWalker;
import de.gematik.demis.validationservice.services.validation.custom.ValidationMessages;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

@Slf4j
public class ResourceReferencesValidationModule extends ParametersValidationModule {

  private final ResourceWalker resourceWalker = new ResourceWalker();
  private final BundleResourceIndex bundleResourceIndex = new BundleResourceIndex();

  @Override
  protected void validateIndividualResource(IBaseResource resource, ValidationMessages messages) {
    if (!(resource instanceof Bundle bundle)) {
      return;
    }
    final Map<String, Resource> referenceIndex = bundleResourceIndex.build(bundle);
    resourceWalker.forEachElementDepthFirst(
        bundle,
        elementCtx -> {
          if (elementCtx.element() instanceof Reference reference) {
            validateReference(messages, elementCtx, reference, referenceIndex);
          }
        });
  }

  private void validateReference(
      final ValidationMessages messages,
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
      messages.addError(
          String.format("Reference '%s' is not resolvable", referenceValue),
          elementCtx.locationPath());
    }
  }

  private boolean isInternalReference(final String referenceValue) {
    return referenceValue.startsWith("#")
        || referenceValue.startsWith("urn:")
        || !referenceValue.contains("://");
  }
}
