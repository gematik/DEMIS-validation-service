package de.gematik.demis.validationservice.services.validation.custom.profile;

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

import static ca.uhn.fhir.validation.ResultSeverityEnum.ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AbstractProfileValidatorTest {

  private static FhirContext ctx;
  private static IParser jsonParser;
  private static Map<String, IBaseResource> structureDefinitions;
  private static Bundle testBundleWithAbstractObservationProfile;
  private static Parameters testParametersWithAbstractObservationProfile;

  @BeforeAll
  static void init() throws IOException {
    ctx = FhirContext.forR4Cached();
    jsonParser = ctx.newJsonParser();

    structureDefinitions = new HashMap<>();

    readStructureDefinitonsFromPath(
        structureDefinitions,
        Path.of(
            "src/test/resources/integrationtests/customValidators/strictCodeValidation/fixedBinding/6.1.8/Fhir/StructureDefinition"));

    String json =
        Files.readString(
            Path.of(
                "src/test/resources/integrationtests/customValidators/input/abstractProfileObservation.json"));
    testBundleWithAbstractObservationProfile = jsonParser.parseResource(Bundle.class, json);

    testParametersWithAbstractObservationProfile = new Parameters();
    testParametersWithAbstractObservationProfile
        .addParameter()
        .setName("content")
        .setResource(testBundleWithAbstractObservationProfile);
  }

  private static void readStructureDefinitonsFromPath(
      Map<String, IBaseResource> structureDefinitions, Path pathStructureDefinitions)
      throws IOException {
    Files.list(pathStructureDefinitions)
        .forEach(
            tmpPath -> {
              try {
                String jsonTmpString = Files.readString(tmpPath);
                StructureDefinition tmpStructureDefinition =
                    jsonParser.parseResource(StructureDefinition.class, jsonTmpString);
                structureDefinitions.put(tmpStructureDefinition.getUrl(), tmpStructureDefinition);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Nested
  @DisplayName("Tests with reference to abstract profile in an instance")
  class AbstractProfileValidation {

    private static Stream<Arguments> invalidAbstractProfileResources() {
      return Stream.of(
          Arguments.of("Bundle", testBundleWithAbstractObservationProfile),
          Arguments.of("Bundle of Parameters", testParametersWithAbstractObservationProfile));
    }

    @ParameterizedTest(name = "Should not validate abstract meta.profile in {0}")
    @MethodSource("invalidAbstractProfileResources")
    @DisplayName("Should not validate Observation because of abstract meta.profile")
    void shouldNotValidateAbstractMetaProfileWithParameters(
        String caseName, IBaseResource resource) {
      // Instantiate custom validator module
      AbstractProfileValidator customValidator = new AbstractProfileValidator(structureDefinitions);

      FhirValidator validator = ctx.newValidator();
      validator.registerValidatorModule(customValidator);

      ValidationResult result = validator.validateWithResult(resource);

      List<SingleValidationMessage> messages = result.getMessages();

      assertThat(messages)
          .anyMatch(
              m ->
                  m.getSeverity() == ERROR
                      && m.getMessage()
                          .contains(
                              "The profile 'https://demis.rki.de/fhir/StructureDefinition/PathogenDetection' is marked as abstract"));
    }

    @Test
    @DisplayName(
        "Should validate Observation with abstract meta.profile because of missing validator module")
    void shouldValidateAbstractMetaProfileInObservation() {
      FhirValidator validator = ctx.newValidator();

      ValidationResult result =
          validator.validateWithResult(testBundleWithAbstractObservationProfile);

      List<SingleValidationMessage> messages = result.getMessages();

      assertThat(messages)
          .noneMatch(
              m ->
                  m.getSeverity() == ERROR
                      && m.getMessage()
                          .contains(
                              "The profile 'https://demis.rki.de/fhir/StructureDefinition/PathogenDetection' is marked as abstract"));
    }
  }
}
