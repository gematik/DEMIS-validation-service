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

import de.gematik.demis.validationservice.services.validation.custom.ResourceWalker;
import de.gematik.demis.validationservice.services.validation.custom.TestValidationMessages;
import java.util.concurrent.atomic.AtomicReference;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContainedReferenceValidationTest {

  private static final String LOCATION =
      "Bundle.entry[0].resource/*Observation/observation-1*/.subject";
  private static final String PATIENT_REFERENCE = "#contained-patient";

  private final TestValidationMessages messages = new TestValidationMessages();
  private final ContainedReferenceValidation validator = new ContainedReferenceValidation(messages);

  @Test
  void shouldAcceptReferenceToContainedResource() {
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Patient patient = new Patient();
    patient.setId("contained-patient");
    observation.addContained(patient);
    Bundle bundle = bundleWith(observation);

    validate(bundle, observation.getSubject());

    messages.assertNoMessages();
  }

  @Test
  void shouldReportMissingContainedResource() {
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Bundle bundle = bundleWith(observation);

    validate(bundle, observation.getSubject());

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: " + PATIENT_REFERENCE, LOCATION);
  }

  @ParameterizedTest
  @ValueSource(strings = {"#missing-patient", "#contained-patient-extra", "#Contained-Patient"})
  void shouldRequireExactContainedIdMatch(String reference) {
    Observation observation = observationWithSubject(reference);
    Patient patient = new Patient();
    patient.setId("contained-patient");
    observation.addContained(patient);
    Bundle bundle = bundleWith(observation);

    validate(bundle, observation.getSubject());

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: " + reference, LOCATION);
  }

  @Test
  void shouldResolveReferenceFromContainedResourceToSiblingContainedResource() {
    Organization organization = new Organization();
    organization.setId("contained-organization");
    Patient patient = new Patient();
    patient.setId("contained-patient");
    Reference reference = new Reference("#contained-organization");
    patient.setManagingOrganization(reference);
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    observation.addContained(patient);
    observation.addContained(organization);
    Bundle bundle = bundleWith(observation);

    validate(bundle, reference);

    messages.assertNoMessages();
  }

  @ParameterizedTest
  @ValueSource(strings = {"patient-1", ""})
  void shouldResolveReferenceFromContainedResourceBackToItsContainer(String containerId) {
    Reference reference = new Reference("#");
    ListResource list = new ListResource();
    list.setId("contained-list");
    list.addEntry().setItem(reference);
    Patient patient = new Patient();
    patient.setId(containerId);
    patient.addContained(list);

    validate(bundleWith(patient), reference);

    messages.assertNoMessages();
  }

  @Test
  void shouldResolveReferenceToContainedResourceItself() {
    Patient patient = new Patient();
    patient.setId("contained-patient");
    Reference reference = new Reference(PATIENT_REFERENCE);
    patient.addLink().setOther(reference);
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    observation.addContained(patient);

    validate(bundleWith(observation), reference);

    messages.assertNoMessages();
  }

  @Test
  void shouldNotResolveContainerByIdInsteadOfBackReference() {
    Patient patient = new Patient();
    patient.setId("contained-patient");
    Reference reference = new Reference("#observation-1");
    patient.setManagingOrganization(reference);
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    observation.addContained(patient);

    validate(bundleWith(observation), reference);

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: #observation-1",
        "Bundle.entry[0].resource/*Observation/observation-1*/"
            + ".contained[0]/*Patient/contained-patient*/.managingOrganization");
  }

  @Test
  void shouldRejectBackReferenceOutsideContainedResourceEvenWithAnIdlessContainedResource() {
    Observation observation = observationWithSubject("#");
    observation.addContained(new Patient());

    validate(bundleWith(observation), observation.getSubject());

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: #", LOCATION);
  }

  @Test
  void shouldReportMissingSiblingContainedResourceAtReferenceLocation() {
    Patient patient = new Patient();
    patient.setId("contained-patient");
    Reference reference = new Reference("#missing-organization");
    patient.setManagingOrganization(reference);
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    observation.addContained(patient);
    Bundle bundle = bundleWith(observation);

    validate(bundle, reference);

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: #missing-organization",
        "Bundle.entry[0].resource/*Observation/observation-1*/"
            + ".contained[0]/*Patient/contained-patient*/.managingOrganization");
  }

  @Test
  void shouldNotResolveContainedReferenceAgainstAnotherBundleEntry() {
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Observation otherObservation = new Observation();
    otherObservation.setId("observation-2");
    Patient patient = new Patient();
    patient.setId("contained-patient");
    otherObservation.addContained(patient);
    Bundle bundle = bundleWith(observation);
    bundle.addEntry().setResource(otherObservation);

    validate(bundle, observation.getSubject());

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: " + PATIENT_REFERENCE, LOCATION);
  }

  @Test
  void shouldNotResolveContainedReferenceAgainstStandaloneBundleResource() {
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Patient patient = new Patient();
    patient.setId("contained-patient");
    Bundle bundle = bundleWith(observation);
    bundle.addEntry().setResource(patient);

    validate(bundle, observation.getSubject());

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: " + PATIENT_REFERENCE, LOCATION);
  }

  @Test
  void shouldNotResolveContainedReferenceAgainstAnotherParametersEntry() {
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Observation otherObservation = new Observation();
    otherObservation.setId("observation-2");
    Patient patient = new Patient();
    patient.setId("contained-patient");
    otherObservation.addContained(patient);
    Parameters parameters = new Parameters();
    parameters.addParameter().setName("source").setResource(observation);
    parameters.addParameter().setName("other").setResource(otherObservation);

    validate(parameters, observation.getSubject());

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: " + PATIENT_REFERENCE,
        "Parameters.parameter[0].resource/*Observation/observation-1*/.subject");
  }

  @Test
  void shouldResolveSiblingWithinParametersResourceScope() {
    Organization organization = new Organization();
    organization.setId("contained-organization");
    Patient patient = new Patient();
    patient.setId("contained-patient");
    Reference reference = new Reference("#contained-organization");
    patient.setManagingOrganization(reference);
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    observation.addContained(patient);
    observation.addContained(organization);
    Parameters parameters = new Parameters();
    parameters.addParameter().setName("source").setResource(observation);

    validate(parameters, reference);

    messages.assertNoMessages();
  }

  @Test
  void shouldResolveContainedReferenceInStandaloneResource() {
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Patient patient = new Patient();
    patient.setId("contained-patient");
    observation.addContained(patient);

    validate(observation, observation.getSubject());

    messages.assertNoMessages();
  }

  @Test
  void shouldUseCurrentResourceWhenContextHasNoAncestry() {
    Observation observation = observationWithSubject(PATIENT_REFERENCE);
    Patient patient = new Patient();
    patient.setId("contained-patient");
    observation.addContained(patient);
    Reference reference = observation.getSubject();
    ResourceWalker.ElementCtx context =
        new ResourceWalker.ElementCtx(
            LOCATION, "Observation.subject", observation, reference, null);

    validator.validate(reference, context);

    messages.assertNoMessages();
  }

  @Test
  void shouldRejectBackReferenceWhenContextHasNoContainerAncestry() {
    Observation observation = observationWithSubject("#");
    Reference reference = observation.getSubject();
    ResourceWalker.ElementCtx context =
        new ResourceWalker.ElementCtx(
            LOCATION, "Observation.subject", observation, reference, null);

    validator.validate(reference, context);

    messages.assertSingleError(
        "Referenced resource is missing in FHIR Bundle! Reference: #", LOCATION);
  }

  private Observation observationWithSubject(String reference) {
    Observation observation = new Observation();
    observation.setId("observation-1");
    observation.setSubject(new Reference(reference));
    return observation;
  }

  private Bundle bundleWith(Resource resource) {
    Bundle bundle = new Bundle();
    bundle
        .addEntry()
        .setFullUrl("urn:uuid:123e4567-e89b-12d3-a456-426614174000")
        .setResource(resource);
    return bundle;
  }

  private void validate(Resource resource, Reference reference) {
    AtomicReference<ResourceWalker.ElementCtx> referenceContext = new AtomicReference<>();
    new ResourceWalker()
        .forEachElementDepthFirst(
            resource,
            context -> {
              if (context.element() == reference) {
                referenceContext.set(context);
              }
            });
    assertThat(referenceContext.get()).isNotNull();

    validator.validate(reference, referenceContext.get());
  }
}
