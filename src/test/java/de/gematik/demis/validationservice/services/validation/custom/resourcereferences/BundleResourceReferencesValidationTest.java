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

import de.gematik.demis.validationservice.services.validation.custom.TestValidationMessages;
import de.gematik.demis.validationservice.services.validation.custom.TestValidationMessages.ValidationMessage;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BundleResourceReferencesValidationTest {

  private static final String OBSERVATION_FULL_URL =
      "https://example.org/fhir/Observation/observation-1";
  private static final String LOCATION =
      "Bundle.entry[0].resource/*Observation/observation-1*/.subject";

  private final TestValidationMessages messages = new TestValidationMessages();

  @ParameterizedTest
  @ValueSource(strings = {"patient-1", "ftp://example.org/Patient/patient-1", "/Patient/patient-1"})
  void shouldReportUnknownReferenceTypeAtReferenceLocation(String referenceValue) {
    Observation observation = observationWithSubject(referenceValue);
    Bundle bundle = bundleWith(observation);

    createBundleResourceReferencesValidation(bundle).validate();

    messages.assertSingleError("Unknown reference type! Reference: " + referenceValue, LOCATION);
  }

  private BundleResourceReferencesValidation createBundleResourceReferencesValidation(
      Bundle bundle) {
    final var properties = new ResourceReferencesBundleValidationConfigProperties(List.of());
    return new BundleResourceReferencesValidation(properties, messages, bundle);
  }

  @Test
  void shouldRejectUnknownReferenceTypeEvenWhenItMatchesAnEntryFullUrl() {
    String patientFullUrl = "unsupported-patient-address";
    Observation observation = observationWithSubject(patientFullUrl);
    Patient patient = new Patient();
    patient.setId("patient-1");
    Bundle bundle = bundleWith(observation);
    bundle.addEntry().setFullUrl(patientFullUrl).setResource(patient);

    createBundleResourceReferencesValidation(bundle).validate();

    messages.assertSingleError("Unknown reference type! Reference: " + patientFullUrl, LOCATION);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldIgnoreReferenceWithoutLiteralAddress(String referenceValue) {
    Observation observation = observationWithSubject(referenceValue);
    observation.getSubject().setDisplay("Patient without a literal reference");
    observation
        .getSubject()
        .setIdentifier(
            new Identifier().setSystem("https://example.org/patients").setValue("patient-1"));
    Bundle bundle = bundleWith(observation);

    createBundleResourceReferencesValidation(bundle).validate();

    messages.assertNoMessages();
  }

  @Test
  void shouldAcceptBundleWithoutReferences() {
    Bundle bundle = new Bundle();
    bundle.addEntry().setFullUrl(OBSERVATION_FULL_URL).setResource(new Observation());

    createBundleResourceReferencesValidation(bundle).validate();

    messages.assertNoMessages();
  }

  @Test
  void shouldAcceptEmptyBundle() {
    createBundleResourceReferencesValidation(new Bundle()).validate();

    messages.assertNoMessages();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Patient/patient-1",
        "urn:uuid:123e4567-e89b-12d3-a456-426614174000",
        "https://example.org/fhir/Patient/patient-1",
        "#patient-1"
      })
  void shouldDispatchSupportedReferenceTypesToTheirValidators(String referenceValue) {
    Patient patient = new Patient();
    patient.setId("patient-1");
    Observation observation = observationWithSubject(referenceValue);
    Bundle bundle = bundleWith(observation);
    if (referenceValue.startsWith("#")) {
      observation.addContained(patient);
    } else {
      String patientFullUrl =
          referenceValue.startsWith("urn:")
              ? referenceValue
              : "https://example.org/fhir/Patient/patient-1";
      bundle.addEntry().setFullUrl(patientFullUrl).setResource(patient);
    }

    createBundleResourceReferencesValidation(bundle).validate();

    messages.assertNoMessages();
  }

  @Test
  void shouldContinueValidatingOtherReferencesAfterUnknownReference() {
    Observation observation = observationWithSubject("unknown-patient");
    String missingUrn = "urn:uuid:123e4567-e89b-12d3-a456-426614174000";
    observation.addPerformer(new Reference(missingUrn));
    Bundle bundle = bundleWith(observation);

    createBundleResourceReferencesValidation(bundle).validate();

    assertThat(messages.getErrors())
        .containsExactlyInAnyOrder(
            new ValidationMessage("Unknown reference type! Reference: unknown-patient", LOCATION),
            new ValidationMessage(
                "Referenced resource is missing in FHIR Bundle! Reference: " + missingUrn,
                "Bundle.entry[0].resource/*Observation/observation-1*/.performer[0]"));
    assertThat(messages.getWarnings()).isEmpty();
    assertThat(messages.getInfos()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "Patient/patient-1, https://example.org/fhir/Patient/patient-1, false",
    "Patient/patient-1/_history/2, https://example.org/fhir/Patient/patient-1, true",
    "https://example.org/fhir/Patient/patient-1, https://example.org/fhir/Patient/patient-1, false",
    "https://example.org/fhir/Patient/patient-1/_history/2, https://example.org/fhir/Patient/patient-1, true",
    "urn:uuid:patient-1, urn:uuid:patient-1, false",
    "urn:oid:1.2.3, urn:oid:1.2.3, false"
  })
  void shouldReportExactlyOneErrorForAmbiguousReference(
      String reference, String fullUrl, boolean versioned) {
    Bundle bundle = bundleWithAmbiguousSubject(reference, fullUrl, versioned);
    final var properties =
        new ResourceReferencesBundleValidationConfigProperties(List.of("demis.rki.de"));

    new BundleResourceReferencesValidation(properties, messages, bundle).validate();

    messages.assertSingleError(ambiguityMessage(fullUrl, versioned), LOCATION);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "example.org"})
  void shouldNotAcceptAmbiguousHttpReferenceEvenWhenExternalPolicyAllowsIt(String trustedDomain) {
    String fullUrl = "https://example.org/fhir/Patient/patient-1";
    Bundle bundle = bundleWithAmbiguousSubject(fullUrl, fullUrl, false);
    final var properties =
        new ResourceReferencesBundleValidationConfigProperties(
            trustedDomain.isEmpty() ? List.of() : List.of(trustedDomain));

    new BundleResourceReferencesValidation(properties, messages, bundle).validate();

    messages.assertSingleError(ambiguityMessage(fullUrl, false), LOCATION);
  }

  @ParameterizedTest
  @CsvSource({
    "Patient/patient-1, https://example.org/fhir/Patient/patient-1, false",
    "Patient/patient-1/_history/2, https://example.org/fhir/Patient/patient-1, true",
    "https://example.org/fhir/Patient/patient-1, https://example.org/fhir/Patient/patient-1, false",
    "https://example.org/fhir/Patient/patient-1/_history/2, https://example.org/fhir/Patient/patient-1, true",
    "urn:uuid:patient-1, urn:uuid:patient-1, false"
  })
  void shouldContinueValidatingOtherReferencesAfterAmbiguousReference(
      String reference, String fullUrl, boolean versioned) {
    Bundle bundle = bundleWithAmbiguousSubject(reference, fullUrl, versioned);
    String missingUrn = "urn:uuid:missing";
    ((Observation) bundle.getEntryFirstRep().getResource()).addPerformer(new Reference(missingUrn));

    createBundleResourceReferencesValidation(bundle).validate();

    assertThat(messages.getErrors())
        .containsExactly(
            new ValidationMessage(ambiguityMessage(fullUrl, versioned), LOCATION),
            new ValidationMessage(
                "Referenced resource is missing in FHIR Bundle! Reference: " + missingUrn,
                "Bundle.entry[0].resource/*Observation/observation-1*/.performer[0]"));
    assertThat(messages.getWarnings()).isEmpty();
    assertThat(messages.getInfos()).isEmpty();
  }

  private Bundle bundleWithAmbiguousSubject(String reference, String fullUrl, boolean versioned) {
    Bundle bundle = bundleWith(observationWithSubject(reference));
    Patient first = new Patient();
    first.getMeta().setVersionId(versioned ? "2" : "1");
    Patient second = new Patient();
    second.getMeta().setVersionId("2");
    bundle.addEntry().setFullUrl(fullUrl).setResource(first);
    bundle.addEntry().setFullUrl(fullUrl).setResource(second);
    return bundle;
  }

  private String ambiguityMessage(String fullUrl, boolean versioned) {
    String reference = fullUrl + (versioned ? "/_history/2" : "");
    return "Unable to resolve ambiguous resource reference! Reference: "
        + reference
        + " Error: Ambiguous "
        + (versioned ? "versioned " : "")
        + "full URL reference: "
        + reference;
  }

  @Test
  void shouldReportUnknownReferenceInsideContainedResource() {
    Patient patient = new Patient();
    patient.setId("patient-1");
    patient.setManagingOrganization(new Reference("unknown-organization"));
    Observation observation = observationWithSubject("#patient-1");
    observation.addContained(patient);
    Bundle bundle = bundleWith(observation);

    createBundleResourceReferencesValidation(bundle).validate();

    messages.assertSingleError(
        "Unknown reference type! Reference: unknown-organization",
        "Bundle.entry[0].resource/*Observation/observation-1*/"
            + ".contained[0]/*Patient/patient-1*/.managingOrganization");
  }

  @Test
  void shouldAcceptBackReferenceFromContainedResource() {
    Patient patient = new Patient();
    patient.setId("patient-1");
    ListResource list = new ListResource();
    list.setId("contained-list");
    list.addEntry().setItem(new Reference("#"));
    patient.addContained(list);
    Bundle bundle = new Bundle();
    bundle.addEntry().setFullUrl("https://example.org/fhir/Patient/patient-1").setResource(patient);

    createBundleResourceReferencesValidation(bundle).validate();

    messages.assertNoMessages();
  }

  private Observation observationWithSubject(String referenceValue) {
    Observation observation = new Observation();
    observation.setId("observation-1");
    observation.setSubject(new Reference(referenceValue));
    return observation;
  }

  private Bundle bundleWith(Observation observation) {
    Bundle bundle = new Bundle();
    bundle.addEntry().setFullUrl(OBSERVATION_FULL_URL).setResource(observation);
    return bundle;
  }

  @Nested
  class ContainedBundles {

    @Test
    void shouldResolveUrnReferencesWithinEachBundle() {
      String outerPatientFullUrl = "urn:uuid:11111111-1111-4111-8111-111111111111";
      String innerPatientFullUrl = "urn:uuid:22222222-2222-4222-8222-222222222222";
      Bundle innerBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
      innerBundle.setId("inner-bundle");
      innerBundle
          .addEntry()
          .setFullUrl("urn:uuid:33333333-3333-4333-8333-333333333333")
          .setResource(observationWithSubject(innerPatientFullUrl));
      innerBundle.addEntry().setFullUrl(innerPatientFullUrl).setResource(new Patient());

      Bundle outerBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
      outerBundle
          .addEntry()
          .setFullUrl("urn:uuid:44444444-4444-4444-8444-444444444444")
          .setResource(innerBundle);
      outerBundle
          .addEntry()
          .setFullUrl("urn:uuid:55555555-5555-4555-8555-555555555555")
          .setResource(observationWithSubject(outerPatientFullUrl));
      outerBundle.addEntry().setFullUrl(outerPatientFullUrl).setResource(new Patient());

      createBundleResourceReferencesValidation(outerBundle).validate();

      messages.assertNoMessages();
    }

    @Test
    void shouldRejectInnerReferenceWhenTargetExistsOnlyInOuterBundle() {
      String patientFullUrl = "urn:uuid:11111111-1111-4111-8111-111111111111";
      Bundle innerBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
      innerBundle.setId("inner-bundle");
      innerBundle
          .addEntry()
          .setFullUrl("urn:uuid:22222222-2222-4222-8222-222222222222")
          .setResource(observationWithSubject(patientFullUrl));
      Bundle outerBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
      outerBundle
          .addEntry()
          .setFullUrl("urn:uuid:33333333-3333-4333-8333-333333333333")
          .setResource(innerBundle);
      outerBundle.addEntry().setFullUrl(patientFullUrl).setResource(new Patient());

      createBundleResourceReferencesValidation(outerBundle).validate();

      messages.assertSingleError(
          "Referenced resource is missing in FHIR Bundle! Reference: " + patientFullUrl,
          "Bundle.entry[0].resource/*Bundle/inner-bundle*/"
              + ".entry[0].resource/*Observation/observation-1*/.subject");
    }

    @Test
    void shouldRejectOuterReferenceWhenTargetExistsOnlyInInnerBundle() {
      String patientFullUrl = "urn:uuid:11111111-1111-4111-8111-111111111111";
      Bundle innerBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
      innerBundle.setId("inner-bundle");
      innerBundle.addEntry().setFullUrl(patientFullUrl).setResource(new Patient());
      Bundle outerBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
      outerBundle
          .addEntry()
          .setFullUrl("urn:uuid:22222222-2222-4222-8222-222222222222")
          .setResource(innerBundle);
      outerBundle
          .addEntry()
          .setFullUrl("urn:uuid:33333333-3333-4333-8333-333333333333")
          .setResource(observationWithSubject(patientFullUrl));

      createBundleResourceReferencesValidation(outerBundle).validate();

      messages.assertSingleError(
          "Referenced resource is missing in FHIR Bundle! Reference: " + patientFullUrl,
          "Bundle.entry[1].resource/*Observation/observation-1*/.subject");
    }

    @Test
    void shouldResolveUrnReferencesWithinThreeBundleLevelsAndRestoreOuterContexts() {
      String outerPatientFullUrl = "urn:uuid:11111111-1111-4111-8111-111111111111";
      String middlePatientFullUrl = "urn:uuid:22222222-2222-4222-8222-222222222222";
      String innerPatientFullUrl = "urn:uuid:33333333-3333-4333-8333-333333333333";
      Bundle innerBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
      innerBundle.setId("inner-bundle");
      innerBundle
          .addEntry()
          .setFullUrl("urn:uuid:44444444-4444-4444-8444-444444444444")
          .setResource(observationWithSubject(innerPatientFullUrl));
      innerBundle.addEntry().setFullUrl(innerPatientFullUrl).setResource(new Patient());

      Bundle middleBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
      middleBundle.setId("middle-bundle");
      middleBundle
          .addEntry()
          .setFullUrl("urn:uuid:55555555-5555-4555-8555-555555555555")
          .setResource(innerBundle);
      middleBundle
          .addEntry()
          .setFullUrl("urn:uuid:66666666-6666-4666-8666-666666666666")
          .setResource(observationWithSubject(middlePatientFullUrl));
      middleBundle.addEntry().setFullUrl(middlePatientFullUrl).setResource(new Patient());

      Bundle outerBundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
      outerBundle
          .addEntry()
          .setFullUrl("urn:uuid:77777777-7777-4777-8777-777777777777")
          .setResource(middleBundle);
      outerBundle
          .addEntry()
          .setFullUrl("urn:uuid:88888888-8888-4888-8888-888888888888")
          .setResource(observationWithSubject(outerPatientFullUrl));
      outerBundle.addEntry().setFullUrl(outerPatientFullUrl).setResource(new Patient());

      createBundleResourceReferencesValidation(outerBundle).validate();

      messages.assertNoMessages();
    }
  }
}
