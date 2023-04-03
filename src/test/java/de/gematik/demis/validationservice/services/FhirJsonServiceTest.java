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

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.validationservice.util.ContextUtil;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FhirJsonServiceTest {

  private FhirJsonService jsonService;

  @BeforeEach
  void setupFhirParsingService() {
    FhirContext ctx = ContextUtil.getFhirContextEn();
    jsonService = new FhirJsonService(ctx);
  }

  @Test
  void testParsingValidFileAndCheckThereIsAValidBundle() {
    Patient patient = new Patient();
    patient.addName().setFamily("Simpson").addGiven("James");

    String patientAsJson = jsonService.toJson(patient);

    assertEquals(
        "{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"Simpson\",\"given\":[\"James\"]}]}",
        patientAsJson);
  }
}
