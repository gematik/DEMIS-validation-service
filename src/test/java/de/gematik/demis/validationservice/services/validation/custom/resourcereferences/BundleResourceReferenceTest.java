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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests address preservation and version extraction, not reference resolution or input validation.
 *
 * @see <a href="https://hl7.org/fhir/R4/bundle.html#references">FHIR R4 reference resolution</a>
 */
class BundleResourceReferenceTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Patient/patient-1",
        "Observation/observation-1",
        "http://example.org/fhir/Patient/patient-1",
        "https://example.org/fhir/Patient/patient-1",
        "https://example.org:8443/fhir/Patient/patient-1",
        "https://example.org/Patient/patient-1",
        "https://example.org/fhir/Patient/PATIENT-1",
        "https://example.org/fhir/Patient/01",
        "https://example.org/patients/patient-1",
        "urn:uuid:123e4567-e89b-12d3-a456-426614174000",
        "urn:oid:1.2.276.0.76.4.8",
        "#patient-1",
        "#",
        "https://example.org/_history-archive/fhir/Patient/patient-1",
        "https://example.org/_history/archive/fhir/Patient/patient-1",
        "https://example.org/_history/archive/_history/other/Patient/patient-1",
        "https://example.org/fhir/_history/Patient/patient-1"
      })
  void shouldPreserveUnversionedAddressWithoutInventingVersion(String referenceValue) {
    assertReferenceParts(referenceValue, referenceValue, null);
  }

  @ParameterizedTest
  @CsvSource({
    "Patient/patient-1/_history/2, Patient/patient-1, 2",
    "Observation/observation-1/_history/1, Observation/observation-1, 1",
    "http://example.org/fhir/Patient/patient-1/_history/2,"
        + " http://example.org/fhir/Patient/patient-1, 2",
    "https://example.org/fhir/Patient/patient-1/_history/2,"
        + " https://example.org/fhir/Patient/patient-1, 2",
    "https://example.org:8443/fhir/Patient/patient-1/_history/2,"
        + " https://example.org:8443/fhir/Patient/patient-1, 2",
    "https://example.org/Patient/patient-1/_history/2,"
        + " https://example.org/Patient/patient-1, 2",
    "https://example.org/fhir/Patient/PATIENT-1/_history/2,"
        + " https://example.org/fhir/Patient/PATIENT-1, 2",
    "https://example.org/_history-archive/fhir/Patient/patient-1/_history/2,"
        + " https://example.org/_history-archive/fhir/Patient/patient-1, 2",
    "https://example.org/_history/archive/fhir/Patient/patient-1/_history/2,"
        + " https://example.org/_history/archive/fhir/Patient/patient-1, 2",
    "https://example.org/_history/archive/_history/other/Patient/patient-1/_history/2,"
        + " https://example.org/_history/archive/_history/other/Patient/patient-1, 2"
  })
  void shouldRemoveOnlyTerminalVersionSuffix(
      String referenceValue, String expectedFullUrl, String expectedVersionId) {
    assertReferenceParts(referenceValue, expectedFullUrl, expectedVersionId);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "0",
        "1",
        "01",
        "release-A.2",
        "release-a.2",
        "a",
        "-",
        ".",
        "1234567890123456789012345678901234567890123456789012345678901234"
      })
  void shouldPreserveVersionIdAsOpaqueCaseSensitiveFhirId(String versionId) {
    assertReferenceParts("Patient/patient-1/_history/" + versionId, "Patient/patient-1", versionId);
    assertReferenceParts(
        "https://example.org/fhir/Patient/patient-1/_history/" + versionId,
        "https://example.org/fhir/Patient/patient-1",
        versionId);
  }

  private static void assertReferenceParts(
      String referenceValue, String expectedFullUrl, String expectedVersionId) {
    BundleResourceReference fullUrlFirst = new BundleResourceReference(referenceValue);
    assertThat(fullUrlFirst).hasToString(referenceValue);
    assertThat(fullUrlFirst.fullUrl()).isEqualTo(expectedFullUrl);
    assertThat(fullUrlFirst.versionId()).isEqualTo(expectedVersionId);
    assertThat(fullUrlFirst.fullUrl()).isEqualTo(expectedFullUrl);
    assertThat(fullUrlFirst.versionId()).isEqualTo(expectedVersionId);
    assertThat(fullUrlFirst).hasToString(referenceValue);

    BundleResourceReference versionFirst = new BundleResourceReference(referenceValue);
    assertThat(versionFirst.versionId()).isEqualTo(expectedVersionId);
    assertThat(versionFirst.fullUrl()).isEqualTo(expectedFullUrl);
    assertThat(versionFirst.versionId()).isEqualTo(expectedVersionId);
    assertThat(versionFirst.fullUrl()).isEqualTo(expectedFullUrl);
    assertThat(versionFirst).hasToString(referenceValue);
  }
}
