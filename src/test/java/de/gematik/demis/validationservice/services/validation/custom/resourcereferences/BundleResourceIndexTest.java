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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.demis.validationservice.services.validation.custom.resourcereferences.BundleResourceIndex.AmbiguousResourceReferenceException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Black-box tests of FHIR R4 Bundle fullUrl matching and the package's ambiguity policy.
 *
 * @see <a href="https://hl7.org/fhir/R4/bundle.html#references">FHIR R4 reference resolution</a>
 */
class BundleResourceIndexTest {

  private static final String PATIENT_URL = "https://example.org/fhir/Patient/patient-1";
  private static final String PATIENT_URN = "urn:uuid:123e4567-e89b-12d3-a456-426614174000";

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://example.org/fhir/Patient/patient-1",
        PATIENT_URL,
        PATIENT_URN,
        "urn:oid:1.2.276.0.76.4.8",
        "https://example.org/patients/patient-1",
        "https://example.org/_history-archive/fhir/Patient/patient-1",
        "https://example.org/_history/archive/fhir/Patient/patient-1"
      })
  void shouldResolveExactFullUrlWithoutRequiringLogicalId(String fullUrl) throws Exception {
    Patient patient = new Patient();
    Bundle bundle = new Bundle();
    addEntry(bundle, fullUrl, patient);

    assertThat(BundleResourceIndex.create(bundle).lookupFullUrlReference(fullUrl))
        .isSameAs(patient);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"1", "release-A.2"})
  void shouldResolveUnversionedReferenceRegardlessOfTargetVersion(String versionId)
      throws Exception {
    Patient patient = patient(versionId);
    Bundle bundle = new Bundle();
    addEntry(bundle, PATIENT_URL, patient);
    addEntry(bundle, PATIENT_URN, patient);
    BundleResourceIndex resources = BundleResourceIndex.create(bundle);

    assertThat(resources.lookupFullUrlReference(PATIENT_URL)).isSameAs(patient);
    assertThat(resources.lookupFullUrlReference(PATIENT_URN)).isSameAs(patient);
  }

  @Test
  void shouldResolveResourcesWithMixedAddressFormsAndResourceTypes() throws Exception {
    Bundle bundle = new Bundle();
    Patient patient = new Patient();
    Observation observation = new Observation();
    String observationUrl = "http://example.org/fhir/Observation/observation-1";
    addEntry(bundle, PATIENT_URN, patient);
    addEntry(bundle, observationUrl, observation);
    BundleResourceIndex resources = BundleResourceIndex.create(bundle);

    assertThat(resources.lookupFullUrlReference(PATIENT_URN)).isSameAs(patient);
    assertThat(resources.lookupFullUrlReference(observationUrl)).isSameAs(observation);
  }

  @ParameterizedTest
  @ValueSource(strings = {PATIENT_URL, PATIENT_URN, PATIENT_URL + "/_history/2"})
  void shouldReturnNoTargetForEmptyBundle(String reference) throws Exception {
    assertThat(BundleResourceIndex.create(new Bundle()).lookupFullUrlReference(reference)).isNull();
  }

  @Test
  void shouldIgnoreEntriesWithoutResourceOrFullUrl() throws Exception {
    Bundle bundle = new Bundle();
    bundle.addEntry().setFullUrl(PATIENT_URL);
    bundle.addEntry().setResource(new Patient().setId("patient-1"));
    bundle.addEntry().setFullUrl("").setResource(new Patient());
    bundle.addEntry();
    Patient patient = new Patient();
    addEntry(bundle, PATIENT_URN, patient);
    BundleResourceIndex resources = BundleResourceIndex.create(bundle);

    assertThat(resources.lookupFullUrlReference(PATIENT_URL)).isNull();
    assertThat(resources.lookupFullUrlReference("")).isNull();
    assertThat(resources.lookupFullUrlReference(PATIENT_URN)).isSameAs(patient);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://example.org/fhir/Patient/patient-1",
        "https://other.example.org/fhir/Patient/patient-1",
        "https://example.org/other/Patient/patient-1",
        "https://example.org/fhir/Patient/PATIENT-1",
        "https://example.org/fhir/Patient/patient-1-extra",
        "https://example.org/fhir/Patient/patient-1/",
        "https://example.org/fhir/Patient/patient-1?active=true",
        "https://example.org/fhir/Observation/patient-1",
        "Patient/patient-1",
        "patient-1",
        "#patient-1",
        PATIENT_URN
      })
  void shouldNotResolveBySimilarityLogicalIdOrAlias(String reference) throws Exception {
    Bundle bundle = new Bundle();
    Patient patient = new Patient();
    patient.setId("patient-1");
    addEntry(bundle, PATIENT_URL, patient);

    assertThat(BundleResourceIndex.create(bundle).lookupFullUrlReference(reference)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "urn:uuid:123e4567-e89b-12d3-a456-426614174001",
        "urn:uuid:123E4567-E89B-12D3-A456-426614174000",
        "Patient/patient-1",
        PATIENT_URL
      })
  void shouldMatchUrnOnlyByExactFullUrl(String reference) throws Exception {
    Bundle bundle = new Bundle();
    Patient patient = new Patient();
    patient.setId("patient-1");
    addEntry(bundle, PATIENT_URN, patient);

    assertThat(BundleResourceIndex.create(bundle).lookupFullUrlReference(reference)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://example.org/fhir/Patient/patient-1",
        PATIENT_URL,
        "https://example.org/_history-archive/fhir/Patient/patient-1",
        "https://example.org/_history/archive/fhir/Patient/patient-1"
      })
  void shouldResolveVersionedReferenceWithSingleMatchingTarget(String fullUrl) throws Exception {
    Bundle bundle = new Bundle();
    Patient patient = patient("release-A.2");
    addEntry(bundle, fullUrl, patient);

    assertThat(
            BundleResourceIndex.create(bundle)
                .lookupFullUrlReference(fullUrl + "/_history/release-A.2"))
        .isSameAs(patient);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"1", "02", "release-A.2"})
  void shouldReturnNullForSingleTargetWithoutRequestedVersion(String actualVersionId)
      throws Exception {
    Bundle bundle = new Bundle();
    addEntry(bundle, PATIENT_URL, patient(actualVersionId));

    assertThat(
            BundleResourceIndex.create(bundle).lookupFullUrlReference(PATIENT_URL + "/_history/2"))
        .isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "http://example.org/fhir/Patient/patient-1, 1, 2",
    "https://example.org/fhir/Patient/patient-1, 1, 2",
    "https://example.org/fhir/Patient/patient-1, 2, 1",
    "https://example.org/fhir/Patient/patient-1, 1, 01",
    "https://example.org/fhir/Patient/patient-1, release-a, release-A"
  })
  void shouldSelectEachExactVersionIndependentlyOfEntryOrder(
      String fullUrl, String firstVersionId, String secondVersionId) throws Exception {
    Bundle bundle = new Bundle();
    Patient first = patient(firstVersionId);
    Patient second = patient(secondVersionId);
    addEntry(bundle, fullUrl, first);
    addEntry(bundle, fullUrl, second);
    BundleResourceIndex resources = BundleResourceIndex.create(bundle);

    assertThat(resources.lookupFullUrlReference(fullUrl + "/_history/" + firstVersionId))
        .isSameAs(first);
    assertThat(resources.lookupFullUrlReference(fullUrl + "/_history/" + secondVersionId))
        .isSameAs(second);
  }

  @Test
  void shouldMatchVersionOnlyAmongEntriesWithMatchingFullUrl() throws Exception {
    Bundle bundle = new Bundle();
    Patient expected = patient("2");
    addEntry(bundle, "https://other.example.org/fhir/Patient/patient-1", patient("2"));
    addEntry(bundle, PATIENT_URL, patient("1"));
    addEntry(bundle, PATIENT_URL, expected);
    BundleResourceIndex resources = BundleResourceIndex.create(bundle);

    assertThat(resources.lookupFullUrlReference(PATIENT_URL + "/_history/2")).isSameAs(expected);
  }

  @Test
  void shouldResolveVersionEvenWhenAnotherCandidateHasNoVersion() throws Exception {
    Bundle bundle = new Bundle();
    Patient expected = patient("2");
    addEntry(bundle, PATIENT_URL, new Patient());
    addEntry(bundle, PATIENT_URL, expected);

    assertThat(
            BundleResourceIndex.create(bundle).lookupFullUrlReference(PATIENT_URL + "/_history/2"))
        .isSameAs(expected);
  }

  @Test
  void shouldReturnNullForMissingVersionInsteadOfChoosingAnotherVersion() throws Exception {
    Bundle bundle = new Bundle();
    addEntry(bundle, PATIENT_URL, patient("1"));
    addEntry(bundle, PATIENT_URL, patient("2"));

    assertThat(
            BundleResourceIndex.create(bundle).lookupFullUrlReference(PATIENT_URL + "/_history/3"))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {PATIENT_URL, PATIENT_URN})
  void shouldReportAmbiguousUnversionedReferenceRatherThanChooseAnEntry(String fullUrl) {
    Bundle bundle = new Bundle();
    addEntry(bundle, fullUrl, patient("1"));
    addEntry(bundle, fullUrl, patient("2"));

    assertThatThrownBy(() -> BundleResourceIndex.create(bundle).lookupFullUrlReference(fullUrl))
        .isInstanceOfSatisfying(
            AmbiguousResourceReferenceException.class,
            e -> assertThat(e.getFullUrlReference()).isEqualTo(fullUrl));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"1"})
  void shouldReportDuplicateUnversionedTargetsEvenWithoutDistinctVersions(String versionId) {
    Bundle bundle = new Bundle();
    addEntry(bundle, PATIENT_URL, patient(versionId));
    addEntry(bundle, PATIENT_URL, patient(versionId));

    assertThatThrownBy(() -> BundleResourceIndex.create(bundle).lookupFullUrlReference(PATIENT_URL))
        .isInstanceOfSatisfying(
            AmbiguousResourceReferenceException.class,
            e -> assertThat(e.getFullUrlReference()).isEqualTo(PATIENT_URL));
  }

  @Test
  void shouldReportAmbiguityWhenRequestedVersionOccursMoreThanOnce() {
    Bundle bundle = new Bundle();
    addEntry(bundle, PATIENT_URL, patient("2"));
    addEntry(bundle, PATIENT_URL, patient("1"));
    addEntry(bundle, PATIENT_URL, patient("2"));

    assertThatThrownBy(
            () ->
                BundleResourceIndex.create(bundle)
                    .lookupFullUrlReference(PATIENT_URL + "/_history/2"))
        .isInstanceOfSatisfying(
            AmbiguousResourceReferenceException.class,
            e -> assertThat(e.getFullUrlReference()).isEqualTo(PATIENT_URL + "/_history/2"));
  }

  private static Patient patient(String versionId) {
    Patient patient = new Patient();
    if (versionId != null) {
      patient.getMeta().setVersionId(versionId);
    }
    return patient;
  }

  private static void addEntry(Bundle bundle, String fullUrl, Resource resource) {
    bundle.addEntry().setFullUrl(fullUrl).setResource(resource);
  }
}
