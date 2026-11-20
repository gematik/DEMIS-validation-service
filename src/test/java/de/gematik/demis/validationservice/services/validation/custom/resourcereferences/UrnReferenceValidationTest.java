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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import de.gematik.demis.validationservice.services.validation.custom.ResourceWalker;
import de.gematik.demis.validationservice.services.validation.custom.TestValidationMessages;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UrnReferenceValidationTest {

  private static final String PATIENT_FULL_URL = "urn:uuid:123e4567-e89b-12d3-a456-426614174000";
  private static final String LOCATION =
      "Bundle.entry[1].resource/*Observation/observation-1*/.subject";

  private final TestValidationMessages messages = new TestValidationMessages();

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "patient-1")
  void shouldAcceptExactUrnFullUrlMatchRegardlessOfTargetLogicalId(String logicalId)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Patient patient = new Patient();
    patient.setId(logicalId);

    validate(PATIENT_FULL_URL, bundleWith(PATIENT_FULL_URL, patient));

    messages.assertNoMessages();
  }

  @Test
  void shouldReportMissingTargetInEmptyBundleIndex()
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    validate(PATIENT_FULL_URL, new Bundle());

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: " + PATIENT_FULL_URL, LOCATION);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "urn:uuid:123e4567-e89b-12d3-a456-426614174001",
        "urn:uuid:123e4567-e89b-12d3-a456-426614174000-extra",
        "urn:uuid:123E4567-E89B-12D3-A456-426614174000"
      })
  void shouldRequireExactUrnFullUrlMatch(String reference)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Patient patient = new Patient();
    validate(reference, bundleWith(PATIENT_FULL_URL, patient));

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: " + reference, LOCATION);
  }

  @Test
  void shouldNotResolveUrnReferenceByLogicalIdAlone()
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    String patientId = "123e4567-e89b-12d3-a456-426614174000";
    Patient patient = new Patient();
    patient.setId(patientId);

    String patientRelativeReference = "Patient/" + patientId;
    validate(PATIENT_FULL_URL, bundleWith(patientRelativeReference, patient));

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: " + PATIENT_FULL_URL, LOCATION);
  }

  @ParameterizedTest
  @ValueSource(strings = {PATIENT_FULL_URL, "urn:oid:1.2.840.113619.21"})
  void shouldAcceptUuidOrOidReferenceMatchingInternalFullUrl(String reference)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    validate(reference, bundleWith(reference, new Patient()));

    messages.assertNoMessages();
  }

  @ParameterizedTest
  @ValueSource(strings = {PATIENT_FULL_URL, "urn:oid:1.2.840.113619.21"})
  void shouldPropagateAmbiguousUrnReference(String reference) {
    Patient first = new Patient();
    first.getMeta().setVersionId("1");
    Patient second = new Patient();
    second.getMeta().setVersionId("2");
    Bundle bundle = bundleWith(reference, first);
    bundle.addEntry().setFullUrl(reference).setResource(second);

    assertThatExceptionOfType(BundleResourceIndex.AmbiguousResourceReferenceException.class)
        .isThrownBy(() -> validate(reference, bundle))
        .satisfies(e -> assertThat(e.getFullUrlReference()).isEqualTo(reference));

    messages.assertNoMessages();
  }

  private Bundle bundleWith(String fullUrl, Resource resource) {
    Bundle bundle = new Bundle();
    bundle.addEntry().setFullUrl(fullUrl).setResource(resource);
    return bundle;
  }

  private void validate(String referenceValue, Bundle bundle)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Reference reference = new Reference(referenceValue);
    Observation observation = new Observation();
    observation.setId("observation-1");
    observation.setSubject(reference);
    ResourceWalker.ElementCtx context =
        new ResourceWalker.ElementCtx(
            LOCATION, "Observation.subject", observation, reference, null);

    new UrnReferenceValidation(messages, BundleResourceIndex.create(bundle))
        .validate(reference, context);
  }
}
