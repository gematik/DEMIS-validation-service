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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

class BundleResourceIndexTest {

  private final BundleResourceIndex underTest = new BundleResourceIndex();

  @Test
  void shouldIndexBundleByFullUrlAndIdPart() {
    Patient patient = new Patient();
    patient.setId("patient-1");

    Bundle bundle = new Bundle();
    bundle.addEntry().setFullUrl("urn:uuid:aaa-bbb").setResource(patient);

    Map<String, Resource> index = underTest.build(bundle);

    assertThat(index).containsKeys("urn:uuid:aaa-bbb", "Patient/patient-1");
    assertThat(index.get("urn:uuid:aaa-bbb")).isSameAs(patient);
    assertThat(index.get("Patient/patient-1")).isSameAs(patient);
  }

  @Test
  void shouldIndexInnerBundleEntries() {
    Observation observation = new Observation();
    observation.setId("obs-1");

    Bundle innerBundle = new Bundle();
    innerBundle.setId("inner-bundle");
    innerBundle.addEntry().setFullUrl("urn:uuid:obs-full").setResource(observation);

    Bundle outerBundle = new Bundle();
    outerBundle.addEntry().setFullUrl("urn:uuid:inner-full").setResource(innerBundle);

    Map<String, Resource> index = underTest.build(outerBundle);

    assertThat(index)
        .containsKeys(
            "urn:uuid:inner-full", "Bundle/inner-bundle", "urn:uuid:obs-full", "Observation/obs-1");
    assertThat(index.get("Observation/obs-1")).isSameAs(observation);
  }

  @Test
  void shouldSkipEntriesWithoutResource() {
    Bundle bundle = new Bundle();
    BundleEntryComponent emptyEntry = bundle.addEntry();
    emptyEntry.setFullUrl("urn:uuid:no-resource");

    Patient patient = new Patient();
    patient.setId("patient-1");
    bundle.addEntry().setFullUrl("urn:uuid:with-resource").setResource(patient);

    Map<String, Resource> index = underTest.build(bundle);

    assertThat(index)
        .doesNotContainKey("urn:uuid:no-resource")
        .containsKey("urn:uuid:with-resource")
        .containsKey("Patient/patient-1");
  }

  @Test
  void shouldReturnUnmodifiableMap() {
    Bundle bundle = new Bundle();

    Map<String, Resource> index = underTest.build(bundle);

    Patient dummyPatient = new Patient();
    assertThatThrownBy(() -> index.put("dummy", dummyPatient))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
