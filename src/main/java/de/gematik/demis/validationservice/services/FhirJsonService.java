/*
 * Copyright [2023], gematik GmbH
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either expressed or implied.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 */

package de.gematik.demis.validationservice.services;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Service;

/** Service to deserialize FHIR resources to JSON. */
@Service
public class FhirJsonService {
  private final FhirContextService fhirContextService;
  private IParser jsonParser;

  public FhirJsonService(FhirContextService fhirContextService) {
    this.fhirContextService = fhirContextService;
  }

  public String toJson(IBaseResource resource) {
    return getJsonParser().encodeResourceToString(resource);
  }

  private IParser getJsonParser() {
    if (jsonParser == null) {
      jsonParser = fhirContextService.getFhirContext().newJsonParser();
    }

    return jsonParser;
  }
}
