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
import java.util.concurrent.atomic.AtomicReference;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RelativeReferenceValidationTest {

  private static final String PATIENT_REFERENCE = "Patient/patient-1";
  private static final String OBSERVATION_FULL_URL =
      "https://example.org/fhir/Observation/observation-1";
  private static final String PATIENT_FULL_URL = "https://example.org/fhir/Patient/patient-1";
  private static final String LOCATION =
      "Bundle.entry[0].resource/*Observation/observation-1*/.subject";

  private final TestValidationMessages messages = new TestValidationMessages();

  @ParameterizedTest
  @CsvSource({
    "http://example.org/Observation/observation-1, http://example.org/Patient/patient-1",
    "https://example.org/Observation/observation-1, https://example.org/Patient/patient-1",
    "https://example.org/fhir/Observation/observation-1, https://example.org/fhir/Patient/patient-1",
    "https://example.org/services/fhir/Observation/observation-1, https://example.org/services/fhir/Patient/patient-1"
  })
  void shouldResolveReferenceAgainstRestfulBaseOfSourceEntry(
      String observationFullUrl, String patientFullUrl)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Patient patient = new Patient();
    patient.setId("patient-1");
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Bundle bundle = bundleWith(observation, observationFullUrl);
    bundle.addEntry().setFullUrl(patientFullUrl).setResource(patient);

    validate(bundle, observation.getSubject());

    messages.assertNoMessages();
  }

  @Test
  void shouldReportMissingTargetWithResolvedFullUrl()
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Bundle bundle = bundleWith(observation, OBSERVATION_FULL_URL);

    validate(bundle, observation.getSubject());

    assertMissingTarget();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://example.org/fhir/Patient/patient-1",
        "https://other.example.org/fhir/Patient/patient-1",
        "https://example.org/other/Patient/patient-1",
        "https://example.org/fhir/Patient/PATIENT-1",
        "https://example.org/fhir/Patient/patient-1-extra",
        "Patient/patient-1",
        "urn:uuid:patient-1"
      })
  void shouldNotUseDifferentBaseOrIdAliasesInsteadOfExactResolvedFullUrl(String indexKey)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Patient patient = new Patient();
    patient.setId("patient-1");
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Bundle bundle = bundleWith(observation, OBSERVATION_FULL_URL);
    bundle.addEntry().setFullUrl(indexKey).setResource(patient);

    validate(bundle, observation.getSubject());

    assertMissingTarget();
  }

  @Test
  void shouldRejectUrnSourceFullUrlEvenWhenTargetHasMatchingRestfulFullUrl()
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    String observationFullUrl = "urn:uuid:123e4567-e89b-12d3-a456-426614174000";
    Patient patient = new Patient();
    patient.setId("patient-1");
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Bundle bundle = bundleWith(observation, observationFullUrl);
    bundle.addEntry().setFullUrl(PATIENT_FULL_URL).setResource(patient);

    validate(bundle, observation.getSubject());

    messages.assertSingleError(
        "Source entry has a URN fullUrl. Unable to resolve relative reference against a RESTful base. Reference: "
            + PATIENT_REFERENCE
            + " BaseResourceFullUrl: "
            + observationFullUrl,
        LOCATION);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldReportSourceEntryWithoutFullUrl(String observationFullUrl)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Bundle bundle = bundleWith(observation, observationFullUrl);
    bundle.addEntry().setFullUrl(PATIENT_FULL_URL).setResource(new Patient());

    validate(bundle, observation.getSubject());

    messages.assertSingleError(
        "Failed to find base resource of relative reference. Reference: " + PATIENT_REFERENCE,
        LOCATION);
  }

  @Test
  void shouldReportReferenceWithoutBundleEntryContext()
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Reference reference = observation.getSubject();
    ResourceWalker.ElementCtx context =
        new ResourceWalker.ElementCtx(
            LOCATION, "Observation.subject", observation, reference, null);
    Bundle bundle = bundleWith(new Patient(), PATIENT_FULL_URL);

    new RelativeReferenceValidation(messages, BundleResourceIndex.create(bundle))
        .validate(reference, context);

    messages.assertSingleError(
        "Failed to find base resource of relative reference. Reference: " + PATIENT_REFERENCE,
        LOCATION);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://example.org",
        "https://example.org/Observation",
        "https://example.org/fhir/Observation/observation-1?query=value",
        "https://example.org/fhir/Observation/observation-1#fragment",
        "https://example.org/fhir/Observation/invalid id",
        "ftp://example.org/fhir/Observation/observation-1"
      })
  void shouldReportSourceFullUrlWithoutUsableRestfulBase(String observationFullUrl)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Bundle bundle = bundleWith(observation, observationFullUrl);
    bundle.addEntry().setFullUrl(PATIENT_FULL_URL).setResource(new Patient());

    validate(bundle, observation.getSubject());

    messages.assertSingleError(
        "Illegal base resource full URL. Unable to build target resource full URL. Reference: "
            + PATIENT_REFERENCE
            + " BaseResourceFullUrl: "
            + observationFullUrl,
        LOCATION);
  }

  @Test
  void shouldReportInvalidRelativeReferenceWhenResolutionFails()
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    String invalidReference = "Patient/invalid id";
    Observation observation = observationWithSubject(invalidReference);
    Bundle bundle = bundleWith(observation, OBSERVATION_FULL_URL);

    validate(bundle, observation.getSubject());

    assertThat(messages.getErrors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.message())
                  .startsWith(
                      "Invalid relative reference. Unable to resolve against source entry base. Reference: "
                          + invalidReference
                          + " BaseResourceFullUrl: "
                          + OBSERVATION_FULL_URL
                          + " Error: ");
              assertThat(error.location()).isEqualTo(LOCATION);
            });
  }

  @Test
  void shouldResolveReferenceFromContainedResourceUsingEnclosingEntryFullUrl()
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    String organizationFullUrl = "https://example.org/fhir/Organization/organization-1";
    Organization organization = new Organization();
    organization.setId("organization-1");
    Reference reference = new Reference("Organization/organization-1");
    Patient containedPatient = new Patient();
    containedPatient.setId("contained-patient");
    containedPatient.setManagingOrganization(reference);
    Observation observation = observationWithSubject("#contained-patient");
    observation.addContained(containedPatient);
    Bundle bundle = bundleWith(observation, OBSERVATION_FULL_URL);
    bundle.addEntry().setFullUrl(organizationFullUrl).setResource(organization);

    validate(bundle, reference);

    messages.assertNoMessages();
  }

  @Test
  void shouldResolveRequestedVersionAmongDifferentVersions()
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Observation observation = observationWithSubject(PATIENT_REFERENCE + "/_history/2");
    Bundle bundle = bundleWith(observation, OBSERVATION_FULL_URL);
    addPatientVersion(bundle, "1");
    addPatientVersion(bundle, "2");

    validate(bundle, observation.getSubject());

    messages.assertNoMessages();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "1")
  void shouldReportMissingRequestedVersion(String targetVersion)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    String reference = PATIENT_REFERENCE + "/_history/2";
    Observation observation = observationWithSubject(reference);
    Bundle bundle = bundleWith(observation, OBSERVATION_FULL_URL);
    addPatientVersion(bundle, targetVersion);

    validate(bundle, observation.getSubject());

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: "
            + reference
            + " ResolvedFullUrl: "
            + PATIENT_FULL_URL
            + "/_history/2",
        LOCATION);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldPropagateAmbiguousReferenceWithResolvedFullUrl(boolean versioned) {
    String suffix = versioned ? "/_history/2" : "";
    Observation observation = observationWithSubject(PATIENT_REFERENCE + suffix);
    Bundle bundle = bundleWith(observation, OBSERVATION_FULL_URL);
    addPatientVersion(bundle, versioned ? "2" : "1");
    addPatientVersion(bundle, "2");
    String fullUrlReference = PATIENT_FULL_URL + suffix;

    assertThatExceptionOfType(BundleResourceIndex.AmbiguousResourceReferenceException.class)
        .isThrownBy(() -> validate(bundle, observation.getSubject()))
        .satisfies(e -> assertThat(e.getFullUrlReference()).isEqualTo(fullUrlReference));

    messages.assertNoMessages();
  }

  private void addPatientVersion(Bundle bundle, String version) {
    Patient patient = new Patient();
    patient.setId("patient-1");
    patient.getMeta().setVersionId(version);
    bundle.addEntry().setFullUrl(PATIENT_FULL_URL).setResource(patient);
  }

  private Observation observationWithSubject(String reference) {
    Observation observation = new Observation();
    observation.setId("observation-1");
    observation.setSubject(new Reference(reference));
    return observation;
  }

  private Bundle bundleWith(Resource sourceResource, String sourceFullUrl) {
    Bundle bundle = new Bundle();
    bundle.addEntry().setFullUrl(sourceFullUrl).setResource(sourceResource);
    return bundle;
  }

  private void validate(Bundle bundle, Reference reference)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    AtomicReference<ResourceWalker.ElementCtx> referenceContext = new AtomicReference<>();
    new ResourceWalker()
        .forEachElementDepthFirst(
            bundle,
            context -> {
              if (context.element() == reference) {
                referenceContext.set(context);
              }
            });
    assertThat(referenceContext.get()).isNotNull();

    new RelativeReferenceValidation(messages, BundleResourceIndex.create(bundle))
        .validate(reference, referenceContext.get());
  }

  private void assertMissingTarget() {
    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: "
            + PATIENT_REFERENCE
            + " ResolvedFullUrl: "
            + PATIENT_FULL_URL,
        LOCATION);
  }
}
