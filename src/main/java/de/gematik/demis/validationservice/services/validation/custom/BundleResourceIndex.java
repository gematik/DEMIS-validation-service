package de.gematik.demis.validationservice.services.validation.custom;

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

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Resource;

public class BundleResourceIndex {

  public Map<String, Resource> build(final Bundle bundle) {
    final Map<String, Resource> index = new HashMap<>();
    indexResource(bundle, null, index);
    for (final BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.hasResource()) {
        indexResource(entry.getResource(), entry.getFullUrl(), index);
      }
    }
    return Map.copyOf(index);
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

    if (resource instanceof Bundle inner) {
      for (final BundleEntryComponent entry : inner.getEntry()) {
        if (entry.hasResource()) {
          indexResource(entry.getResource(), entry.getFullUrl(), index);
        }
      }
    }
  }
}
