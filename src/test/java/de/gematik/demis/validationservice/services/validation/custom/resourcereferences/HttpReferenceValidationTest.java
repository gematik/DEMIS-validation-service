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
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HttpReferenceValidationTest {

  public static final String EXPECTED_MESSAGE_UNTRUSTED_DOMAIN =
      "References to untrusted external resources are prohibited FHIR Bundle notifications! Reference: ";
  private static final String PATIENT_FULL_URL = "https://example.org/fhir/Patient/patient-1";
  private static final String LOCATION =
      "Bundle.entry[1].resource/*Observation/observation-1*/.subject";
  private final TestValidationMessages messages = new TestValidationMessages();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://example.org/fhir/Patient/patient-1",
        "https://example.org/fhir/Patient/patient-1"
      })
  void shouldAcceptHttpOrHttpsReferenceMatchingInternalFullUrl(String reference)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Patient patient = new Patient();
    patient.setId("patient-1");
    validate(reference, bundleWith(reference, patient));

    messages.assertNoMessages();
  }

  @Test
  void shouldAcceptInternalFullUrlMatchWithoutTargetLogicalId()
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Patient patient = new Patient();
    validate(PATIENT_FULL_URL, bundleWith(PATIENT_FULL_URL, patient));

    messages.assertNoMessages();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://example.org/fhir/Patient/patient-1",
        "https://example.org/fhir/Patient/patient-1"
      })
  void shouldRejectHttpOrHttpsReferenceWithoutInternalTarget(String reference)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    validate(reference, new Bundle());

    messages.assertSingleError(EXPECTED_MESSAGE_UNTRUSTED_DOMAIN + reference, LOCATION);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://demis.rki.de/fhir/Patient/patient-1",
        "https://api.demis.rki.de/fhir/Patient/patient-1"
      })
  void shouldAllowDemisExternalHttpsReferences(String reference)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    validate(reference, new Bundle(), List.of("demis.rki.de"));

    messages.assertNoMessages();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://api.demis.rki.de/fhir/Patient/patient-1",
        "https://notdemis.rki.de/fhir/Patient/patient-1",
        "https://demis.rki.de.example.org/fhir/Patient/patient-1"
      })
  void shouldRejectReferencesOutsideDemisHttpsDomains(String reference)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    validate(reference, new Bundle(), List.of("demis.rki.de"));

    messages.assertSingleError(EXPECTED_MESSAGE_UNTRUSTED_DOMAIN + reference, LOCATION);
  }

  @Test
  void shouldAllowHttpsReferenceForConfiguredTrustedDomain()
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    String reference = "https://api.example.org/fhir/Patient/patient-1";

    validate(reference, new Bundle(), List.of("example.org"));

    messages.assertNoMessages();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://api.demis.rki.de/fhir/Patient/patient-1",
        "https://api.example.org/fhir/Patient/patient-1"
      })
  void shouldAllowReferencesForMultipleTrustedDomains(String reference)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    validate(reference, new Bundle(), List.of("demis.rki.de", "example.org"));

    messages.assertNoMessages();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://api.example.org/fhir/Patient/patient-1",
        "https://unrelated.example.net/fhir/Patient/patient-1"
      })
  void shouldAllowExternalReferencesWhenTrustedDomainListIsEmpty(String reference)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    validate(reference, new Bundle(), List.of());

    messages.assertNoMessages();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://example.org/fhir/Patient/patient-1",
        "https://other.example.org/fhir/Patient/patient-1",
        "https://example.org/other/Patient/patient-1",
        "https://example.org/fhir/Patient/patient-1-extra"
      })
  void shouldRejectReferenceWithoutExactInternalFullUrlMatch(String reference)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Patient patient = new Patient();
    patient.setId("patient-1");

    validate(reference, bundleWith(PATIENT_FULL_URL, patient));

    messages.assertSingleError(EXPECTED_MESSAGE_UNTRUSTED_DOMAIN + reference, LOCATION);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Patient/patient-1", "urn:uuid:patient-1"})
  void shouldNotResolveExternalReferenceByLogicalIdOrUrnAlias(String indexKey)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Patient patient = new Patient();
    patient.setId("patient-1");

    validate(PATIENT_FULL_URL, bundleWith(indexKey, patient));

    messages.assertSingleError(EXPECTED_MESSAGE_UNTRUSTED_DOMAIN + PATIENT_FULL_URL, LOCATION);
  }

  @Test
  void shouldResolveRequestedVersionAmongDifferentVersions()
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Bundle bundle = new Bundle();
    addPatientVersion(bundle, "1");
    addPatientVersion(bundle, "2");

    validate(PATIENT_FULL_URL + "/_history/2", bundle);

    messages.assertNoMessages();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "1")
  void shouldRejectReferenceWithoutRequestedVersion(String targetVersion)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Bundle bundle = new Bundle();
    addPatientVersion(bundle, targetVersion);
    String reference = PATIENT_FULL_URL + "/_history/2";

    validate(reference, bundle);

    messages.assertSingleError(EXPECTED_MESSAGE_UNTRUSTED_DOMAIN + reference, LOCATION);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldPropagateAmbiguousReference(boolean versioned) {
    Bundle bundle = new Bundle();
    addPatientVersion(bundle, versioned ? "2" : "1");
    addPatientVersion(bundle, "2");
    String reference = PATIENT_FULL_URL + (versioned ? "/_history/2" : "");

    assertThatExceptionOfType(BundleResourceIndex.AmbiguousResourceReferenceException.class)
        .isThrownBy(() -> validate(reference, bundle))
        .satisfies(e -> assertThat(e.getFullUrlReference()).isEqualTo(reference));

    messages.assertNoMessages();
  }

  @Nested
  class CaseSensitivity {

    @ParameterizedTest
    @CsvSource({
      "demis.rki.de, demis.rki.de",
      "demis.rki.de, DEMIS.RKI.DE",
      "demis.rki.de, DeMiS.RkI.De",
      "DEMIS.RKI.DE, demis.rki.de",
      "DEMIS.RKI.DE, DEMIS.RKI.DE",
      "DEMIS.RKI.DE, DeMiS.RkI.De",
      "DeMiS.RkI.De, demis.rki.de",
      "DeMiS.RkI.De, DEMIS.RKI.DE",
      "DeMiS.RkI.De, DeMiS.RkI.De",
      "demis.rki.de, api.demis.rki.de",
      "demis.rki.de, API.DEMIS.RKI.DE",
      "demis.rki.de, Api.DeMiS.RkI.De",
      "DEMIS.RKI.DE, api.demis.rki.de",
      "DEMIS.RKI.DE, API.DEMIS.RKI.DE",
      "DEMIS.RKI.DE, Api.DeMiS.RkI.De",
      "DeMiS.RkI.De, api.demis.rki.de",
      "DeMiS.RkI.De, API.DEMIS.RKI.DE",
      "DeMiS.RkI.De, Api.DeMiS.RkI.De",
      "DeMiS.RkI.De, Nested.Api.DeMiS.RkI.De"
    })
    void shouldTrustDomainAndSubdomainsRegardlessOfHostAndConfigurationCase(
        String trustedDomain, String host)
        throws BundleResourceIndex.AmbiguousResourceReferenceException {
      validate("https://" + host + "/fhir/Patient/patient-1", new Bundle(), List.of(trustedDomain));

      messages.assertNoMessages();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https", "HTTPS", "HtTpS"})
    void shouldAllowTrustedExternalHttpsReferenceRegardlessOfSchemeCase(String scheme)
        throws BundleResourceIndex.AmbiguousResourceReferenceException {
      validate(
          scheme + "://demis.rki.de/fhir/Patient/patient-1", new Bundle(), List.of("demis.rki.de"));

      messages.assertNoMessages();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "HTTP", "HtTp"})
    void shouldRejectTrustedExternalHttpReferenceRegardlessOfSchemeCase(String scheme)
        throws BundleResourceIndex.AmbiguousResourceReferenceException {
      String reference = scheme + "://DEMIS.RKI.DE/fhir/Patient/patient-1";

      validate(reference, new Bundle(), List.of("DeMiS.RkI.De"));

      messages.assertSingleError(EXPECTED_MESSAGE_UNTRUSTED_DOMAIN + reference, LOCATION);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://NotDeMiS.RkI.De/fhir/Patient/patient-1",
          "https://DeMiS.RkI.De.Example.Org/fhir/Patient/patient-1",
          "https://ApiDeMiS.RkI.De/fhir/Patient/patient-1",
          "https://Unrelated.Example.Org/fhir/Patient/patient-1"
        })
    void shouldNotTrustUnrelatedHostsRegardlessOfCase(String reference)
        throws BundleResourceIndex.AmbiguousResourceReferenceException {
      validate(reference, new Bundle(), List.of("DeMiS.RkI.De"));

      messages.assertSingleError(EXPECTED_MESSAGE_UNTRUSTED_DOMAIN + reference, LOCATION);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://demis.rki.de/FHIR/Patient/PaTiEnT-1",
          "HTTPS://API.DEMIS.RKI.DE/FHIR/Patient/PaTiEnT-1"
        })
    void shouldNotUsePathOrLogicalIdCaseForExternalDomainTrust(String reference)
        throws BundleResourceIndex.AmbiguousResourceReferenceException {
      validate(reference, new Bundle(), List.of("demis.rki.de"));

      messages.assertNoMessages();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "HTTPS://example.org/fhir/Patient/patient-1",
          "https://EXAMPLE.ORG/fhir/Patient/patient-1",
          "https://example.org/FHIR/Patient/patient-1",
          "https://example.org/fhir/patient/patient-1",
          "https://example.org/fhir/PATIENT/patient-1",
          "https://example.org/fhir/Patient/PATIENT-1"
        })
    void shouldRequireCaseSensitiveInternalFullUrlMatchAccordingToFhirR4(String reference)
        throws BundleResourceIndex.AmbiguousResourceReferenceException {
      Patient patient = new Patient();
      patient.setId("patient-1");

      validate(reference, bundleWith(PATIENT_FULL_URL, patient));

      messages.assertSingleError(EXPECTED_MESSAGE_UNTRUSTED_DOMAIN + reference, LOCATION);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "HTTPS://example.org/fhir/Patient/patient-1",
          "https://EXAMPLE.ORG/fhir/Patient/patient-1",
          "https://example.org/FHIR/Patient/patient-1",
          "https://example.org/fhir/Patient/PaTiEnT-1"
        })
    void shouldAcceptExactInternalFullUrlMatchWithUppercaseCharacters(String fullUrl)
        throws BundleResourceIndex.AmbiguousResourceReferenceException {
      Patient patient = new Patient();

      validate(fullUrl, bundleWith(fullUrl, patient));

      messages.assertNoMessages();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "HTTPS://example.org/fhir/Patient/patient-1",
          "https://EXAMPLE.ORG/fhir/Patient/patient-1",
          "https://example.org/FHIR/Patient/patient-1",
          "https://example.org/fhir/Patient/PATIENT-1"
        })
    void shouldKeepInternalFullUrlsDifferingOnlyInCaseDistinct(String fullUrl)
        throws BundleResourceIndex.AmbiguousResourceReferenceException {
      Bundle bundle = bundleWith(PATIENT_FULL_URL, new Patient());
      bundle.addEntry().setFullUrl(fullUrl).setResource(new Patient());

      validate(fullUrl, bundle);

      messages.assertNoMessages();
    }

    @ParameterizedTest
    @CsvSource({"Release-A, Release-A", "release-a, release-a"})
    void shouldResolveVersionWithExactlyMatchingCase(String targetVersion, String requestedVersion)
        throws BundleResourceIndex.AmbiguousResourceReferenceException {
      Bundle bundle = new Bundle();
      addPatientVersion(bundle, targetVersion);

      validate(PATIENT_FULL_URL + "/_history/" + requestedVersion, bundle);

      messages.assertNoMessages();
    }

    @ParameterizedTest
    @CsvSource({"Release-A, release-a", "release-a, Release-A"})
    void shouldRejectVersionDifferingOnlyInCase(String targetVersion, String requestedVersion)
        throws BundleResourceIndex.AmbiguousResourceReferenceException {
      Bundle bundle = new Bundle();
      addPatientVersion(bundle, targetVersion);
      String reference = PATIENT_FULL_URL + "/_history/" + requestedVersion;

      validate(reference, bundle);

      messages.assertSingleError(EXPECTED_MESSAGE_UNTRUSTED_DOMAIN + reference, LOCATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Release-A", "release-a"})
    void shouldDistinguishVersionsDifferingOnlyInCase(String requestedVersion)
        throws BundleResourceIndex.AmbiguousResourceReferenceException {
      Bundle bundle = new Bundle();
      addPatientVersion(bundle, "Release-A");
      addPatientVersion(bundle, "release-a");

      validate(PATIENT_FULL_URL + "/_history/" + requestedVersion, bundle);

      messages.assertNoMessages();
    }

    @ParameterizedTest
    @ValueSource(strings = {"_HISTORY", "_History"})
    void shouldNotInterpretDifferentlyCasedPathAsVersionMarker(String historySegment)
        throws BundleResourceIndex.AmbiguousResourceReferenceException {
      Bundle bundle = new Bundle();
      addPatientVersion(bundle, "Release-A");
      String reference = PATIENT_FULL_URL + "/" + historySegment + "/Release-A";

      validate(reference, bundle);

      messages.assertSingleError(EXPECTED_MESSAGE_UNTRUSTED_DOMAIN + reference, LOCATION);
    }
  }

  private void addPatientVersion(Bundle bundle, String version) {
    Patient patient = new Patient();
    patient.setId("patient-1");
    patient.getMeta().setVersionId(version);
    bundle.addEntry().setFullUrl(PATIENT_FULL_URL).setResource(patient);
  }

  private Bundle bundleWith(String fullUrl, Resource resource) {
    Bundle bundle = new Bundle();
    bundle.addEntry().setFullUrl(fullUrl).setResource(resource);
    return bundle;
  }

  private void validate(String referenceValue, Bundle bundle)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    validate(referenceValue, bundle, List.of("demis.rki.de"));
  }

  private void validate(
      String referenceValue, Bundle bundle, List<String> trustedExternalReferencesDomains)
      throws BundleResourceIndex.AmbiguousResourceReferenceException {
    Reference reference = new Reference(referenceValue);
    Observation observation = new Observation();
    observation.setId("observation-1");
    observation.setSubject(reference);
    ResourceWalker.ElementCtx context =
        new ResourceWalker.ElementCtx(
            LOCATION, "Observation.subject", observation, reference, null);

    new HttpReferenceValidation(
            messages, BundleResourceIndex.create(bundle), trustedExternalReferencesDomains)
        .validate(reference, context);
  }
}
