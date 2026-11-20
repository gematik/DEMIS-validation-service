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

import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

class ReferenceTypeTest {

  @Nested
  class Classification {

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("referenceTypes")
    void shouldClassifyLiteralReference(String value, ReferenceType expectedType) {
      assertTypeForStringAndReference(value, expectedType);
    }

    static Stream<Arguments> referenceTypes() {
      return Stream.of(
          Arguments.of("urn:uuid:123e4567-e89b-12d3-a456-426614174000", ReferenceType.URN),
          Arguments.of("urn:oid:1.2.276.0.76.4.8", ReferenceType.URN),
          Arguments.of("Patient/patient-1", ReferenceType.RELATIVE),
          Arguments.of("Observation/observation-1/_history/2", ReferenceType.RELATIVE),
          Arguments.of("#patient-1", ReferenceType.CONTAINED),
          Arguments.of("#", ReferenceType.CONTAINED),
          Arguments.of("#urn:uuid:123e4567-e89b-12d3-a456-426614174000", ReferenceType.CONTAINED),
          Arguments.of("http://example.org/fhir/Patient/patient-1", ReferenceType.EXTERNAL),
          Arguments.of("https://example.org/fhir/Patient/patient-1", ReferenceType.EXTERNAL),
          Arguments.of("https://example.org/fhir/Patient/patient-1#local", ReferenceType.EXTERNAL),
          Arguments.of("patient-1", ReferenceType.UNKNOWN),
          Arguments.of("urn", ReferenceType.UNKNOWN),
          Arguments.of("urn-uuid:123e4567-e89b-12d3-a456-426614174000", ReferenceType.UNKNOWN),
          Arguments.of("http:patient-1", ReferenceType.UNKNOWN),
          Arguments.of("https:/example.org/Patient/patient-1", ReferenceType.UNKNOWN),
          Arguments.of("ftp://example.org/Patient/patient-1", ReferenceType.UNKNOWN),
          Arguments.of("/Patient/patient-1", ReferenceType.UNKNOWN),
          Arguments.of("Patient-Invalid/patient-1", ReferenceType.UNKNOWN),
          Arguments.of("123/patient-1", ReferenceType.UNKNOWN));
    }

    @Test
    void shouldClassifyEmptyStringAsUnknownRatherThanEmptyReference() {
      assertThat(ReferenceType.from("")).isEqualTo(ReferenceType.UNKNOWN);
    }
  }

  @Nested
  class EmptyReferences {

    @ParameterizedTest
    @NullSource
    @MethodSource("referencesWithoutLiteralAddress")
    void shouldClassifyMissingLiteralReferenceAsEmpty(Reference reference) {
      assertThat(ReferenceType.from(reference)).isEqualTo(ReferenceType.EMPTY);
    }

    static Stream<Reference> referencesWithoutLiteralAddress() {
      return Stream.of(
          new Reference(),
          new Reference(""),
          new Reference().setDisplay("Patient"),
          new Reference().setIdentifier(new Identifier().setValue("patient-1")));
    }
  }

  @Nested
  class CaseSensitivity {

    @ParameterizedTest
    @CsvSource({
      "urn:uuid:123e4567-e89b-12d3-a456-426614174000",
      "URN:uuid:123e4567-e89b-12d3-a456-426614174000",
      "UrN:uuid:123e4567-e89b-12d3-a456-426614174000"
    })
    void shouldRecognizeUrnSchemeRegardlessOfCase(String value) {
      assertTypeForStringAndReference(value, ReferenceType.URN);
    }

    @ParameterizedTest
    @CsvSource({
      "http://example.org/fhir/Patient/patient-1",
      "HTTP://example.org/fhir/Patient/patient-1",
      "HtTp://example.org/fhir/Patient/patient-1",
      "https://example.org/fhir/Patient/patient-1",
      "HTTPS://example.org/fhir/Patient/patient-1",
      "HtTpS://example.org/fhir/Patient/patient-1"
    })
    void shouldRecognizeHttpSchemeRegardlessOfCase(String value) {
      assertTypeForStringAndReference(value, ReferenceType.EXTERNAL);
    }

    @ParameterizedTest
    @CsvSource({"Patient/patient-1", "patient/patient-1", "PATIENT/PATIENT-1", "pAtIeNt/PaTiEnT-1"})
    void shouldClassifyRelativeReferenceWithoutDependingOnSegmentCase(String value) {
      assertTypeForStringAndReference(value, ReferenceType.RELATIVE);
    }
  }

  private static void assertTypeForStringAndReference(String value, ReferenceType expectedType) {
    assertThat(ReferenceType.from(value)).isEqualTo(expectedType);
    assertThat(ReferenceType.from(new Reference(value))).isEqualTo(expectedType);
  }
}
