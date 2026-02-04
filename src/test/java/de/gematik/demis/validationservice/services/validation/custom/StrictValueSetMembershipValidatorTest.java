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

import static ca.uhn.fhir.validation.ResultSeverityEnum.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.FhirTerser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import de.gematik.demis.validationservice.services.validation.custom.strict.valuesets.BindingInfo;
import de.gematik.demis.validationservice.services.validation.custom.strict.valuesets.StrictValueSetMembershipValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Positive test for StrictValueSetMembershipValidator: the specimen-status code is part of the
 * provided ValueSet expansion; no ERROR should be raised by the custom validator.
 */
class StrictValueSetMembershipValidatorTest {

  private static FhirContext ctx;
  private static IParser jsonParser;

  @BeforeAll
  static void init() {
    ctx = FhirContext.forR4Cached();
    jsonParser = ctx.newJsonParser();
  }

  @Nested
  @DisplayName("Tests with slice-specific bindings (one slice with binding, one without)")
  class SliceSpecificBindings {

    @Test
    @DisplayName("Should not validate HL7 coding when only SNOMED slice has required binding")
    void shouldNotValidateHL7CodingWhenOnlySnomedSliceHasBinding() throws IOException {
      // Load bundle test input with HL7 interpretation coding
      String json =
          Files.readString(
              Path.of(
                  "src/test/resources/integrationtests/customValidators/input/strictCodingCheckExample.json"));
      Bundle bundle = jsonParser.parseResource(Bundle.class, json);

      // Load StructureDefinitions and ValueSets from brokenBinding folder
      Map<String, IBaseResource> structureDefinitions = new HashMap<>();
      Map<String, IBaseResource> valueSets = new HashMap<>();

      readStructureDefinitonsFromPath(
          structureDefinitions,
          Path.of(
              "src/test/resources/integrationtests/customValidators/strictCodeValidation/brokenBinding/6.1.8/Fhir/StructureDefinition"));
      readValueSetsFromPath(
          valueSets,
          Path.of(
              "src/test/resources/integrationtests/customValidators/strictCodeValidation/brokenBinding/6.1.8/Fhir/ValueSet"));

      // Instantiate custom validator module
      StrictValueSetMembershipValidator customValidator =
          new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

      FhirValidator validator = ctx.newValidator();
      validator.registerValidatorModule(customValidator);

      ValidationResult result = validator.validateWithResult(bundle);

      List<SingleValidationMessage> messages = result.getMessages();

      // Assert: No ERROR for HL7 coding because the HL7 slice has no required binding
      // Only the SNOMED slice has a required binding, and HL7 codings should not be validated
      // against it
      assertThat(messages)
          .filteredOn(m -> m.getSeverity() == ERROR)
          .filteredOn(m -> m.getMessage().contains("interpretation"))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("Tests with fixed binding on observations.interpretation slices")
  class FixedBindings {

    @Test
    void shouldValidateBundleWithRequiredCodingPresent() throws IOException {
      // Load bundle test input
      String json =
          Files.readString(
              Path.of(
                  "src/test/resources/integrationtests/customValidators/input/strictCodingCheckExample.json"));
      Bundle bundle = jsonParser.parseResource(Bundle.class, json);

      // Simulate available StructureDefinition(s) and ValueSet(s) relevant for Specimen.status
      Map<String, IBaseResource> structureDefinitions = new HashMap<>();
      Map<String, IBaseResource> valueSets = new HashMap<>();

      // Load Specimen profile (required for path/binding extraction)
      readStructureDefinitions(structureDefinitions);

      // Load ValueSets
      // get all files from
      // src/test/resources/integrationtests/customValidators/strictCodeValidation/6.1.8/Fhir/ValueSet
      readValueSets(valueSets);

      // Instantiate custom validator module
      StrictValueSetMembershipValidator customValidator =
          new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

      FhirValidator validator = ctx.newValidator();
      validator.registerValidatorModule(customValidator);

      ValidationResult result = validator.validateWithResult(bundle);

      List<SingleValidationMessage> messages = result.getMessages();

      assertThat(messages)
          .as("No ERROR messages expected for specimen-status membership")
          .noneMatch(m -> m.getSeverity() == ERROR);

      assertThat(messages).filteredOn(m -> m.getSeverity() == ERROR).isEmpty();
    }

    @Test
    void shouldCheckBundlesWithParametersIdentical() throws IOException {
      // Load bundle test input
      String json =
          Files.readString(
              Path.of(
                  "src/test/resources/integrationtests/customValidators/input/strictCodingCheckExample-Parameters.json"));
      Parameters bundle = jsonParser.parseResource(Parameters.class, json);

      // Simulate available StructureDefinition(s) and ValueSet(s) relevant for Specimen.status
      Map<String, IBaseResource> structureDefinitions = new HashMap<>();
      Map<String, IBaseResource> valueSets = new HashMap<>();

      // Load Specimen profile (required for path/binding extraction)
      readStructureDefinitions(structureDefinitions);

      // Load ValueSets
      // get all files from
      // src/test/resources/integrationtests/customValidators/strictCodeValidation/6.1.8/Fhir/ValueSet
      readValueSets(valueSets);

      // Instantiate custom validator module
      StrictValueSetMembershipValidator customValidator =
          new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

      FhirValidator validator = ctx.newValidator();
      validator.registerValidatorModule(customValidator);

      ValidationResult result = validator.validateWithResult(bundle);

      List<SingleValidationMessage> messages = result.getMessages();

      assertThat(messages)
          .as("No ERROR messages expected for specimen-status membership")
          .noneMatch(m -> m.getSeverity() == ERROR);

      assertThat(messages).filteredOn(m -> m.getSeverity() == ERROR).isEmpty();
    }

    @Test
    void shouldNotDoAnythingOnContextWhenNoBundleOrParameters() throws IOException {
      // Load bundle test input
      String json =
          Files.readString(
              Path.of(
                  "src/test/resources/integrationtests/customValidators/input/strictCodingCheckExampleNoBundleOrParameters.json"));
      IBaseResource iBaseResource = jsonParser.parseResource(json);

      // Simulate available StructureDefinition(s) and ValueSet(s) relevant for Specimen.status
      Map<String, IBaseResource> structureDefinitions = new HashMap<>();
      Map<String, IBaseResource> valueSets = new HashMap<>();

      // Load Specimen profile (required for path/binding extraction)
      readStructureDefinitions(structureDefinitions);

      // Load ValueSets
      // get all files from
      // src/test/resources/integrationtests/customValidators/strictCodeValidation/6.1.8/Fhir/ValueSet
      readValueSets(valueSets);

      // Instantiate custom validator module
      StrictValueSetMembershipValidator customValidator =
          new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

      IValidationContext<IBaseResource> context = mock(IValidationContext.class);
      when(context.getResource()).thenReturn(iBaseResource);

      customValidator.validateResource(context);

      verify(context).getResource();
      verifyNoMoreInteractions(context);
    }

    @Test
    void shouldFailWithInvalidObservationCoding() throws IOException {
      // Load bundle test input
      String json =
          Files.readString(
              Path.of(
                  "src/test/resources/integrationtests/customValidators/input/strictCodingCheckExample-ObservationInvalid.json"));
      Bundle bundle = jsonParser.parseResource(Bundle.class, json);

      // Simulate available StructureDefinition(s) and ValueSet(s) relevant for Specimen.status
      Map<String, IBaseResource> structureDefinitions = new HashMap<>();
      Map<String, IBaseResource> valueSets = new HashMap<>();

      readValueSets(valueSets);

      readStructureDefinitions(structureDefinitions);

      // Instantiate custom validator module
      StrictValueSetMembershipValidator customValidator =
          new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

      FhirValidator validator = ctx.newValidator();
      validator.registerValidatorModule(customValidator);

      ValidationResult result = validator.validateWithResult(bundle);

      List<SingleValidationMessage> messages = result.getMessages();

      // Assert: Exactly one ERROR expected from custom validator
      assertThat(messages).filteredOn(m -> m.getSeverity() == ERROR).hasSize(1);
    }

    private static void readValueSets(Map<String, IBaseResource> valueSets) throws IOException {
      // Load ValueSets
      // get all files from
      // src/test/resources/integrationtests/customValidators/strictCodeValidation/6.1.8/Fhir/ValueSet
      Path pathValueSets =
          Path.of(
              "src/test/resources/integrationtests/customValidators/strictCodeValidation/fixedBinding/6.1.8/Fhir/ValueSet");
      readValueSetsFromPath(valueSets, pathValueSets);
    }

    private static void readStructureDefinitions(Map<String, IBaseResource> structureDefinitions)
        throws IOException {
      // Load Specimen profile (required for path/binding extraction)
      Path pathStructureDefinitions =
          Path.of(
              "src/test/resources/integrationtests/customValidators/strictCodeValidation/fixedBinding/6.1.8/Fhir/StructureDefinition");
      readStructureDefinitonsFromPath(structureDefinitions, pathStructureDefinitions);
    }
  }

  @Nested
  @DisplayName(
      "Tests with broken binding on observations.interpretation slices from snapshot 6.1.7")
  class BrokenBindings {

    @Test
    void shouldValidateBundleWithRequiredCodingPresent() throws IOException {
      // Load bundle test input
      String json =
          Files.readString(
              Path.of(
                  "src/test/resources/integrationtests/customValidators/input/strictCodingCheckExample.json"));
      Bundle bundle = jsonParser.parseResource(Bundle.class, json);

      // Simulate available StructureDefinition(s) and ValueSet(s) relevant for Specimen.status
      Map<String, IBaseResource> structureDefinitions = new HashMap<>();
      Map<String, IBaseResource> valueSets = new HashMap<>();

      // Load Specimen profile (required for path/binding extraction)
      readStructureDefinitions(structureDefinitions);

      // Load ValueSets
      // get all files from
      // src/test/resources/integrationtests/customValidators/strictCodeValidation/6.1.8/Fhir/ValueSet
      readValueSets(valueSets);

      // Instantiate custom validator module
      StrictValueSetMembershipValidator customValidator =
          new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

      FhirValidator validator = ctx.newValidator();
      validator.registerValidatorModule(customValidator);

      ValidationResult result = validator.validateWithResult(bundle);

      List<SingleValidationMessage> messages = result.getMessages();

      assertThat(messages)
          .as("No ERROR messages expected for specimen-status membership")
          .noneMatch(m -> m.getSeverity() == ERROR);

      assertThat(messages).filteredOn(m -> m.getSeverity() == ERROR).isEmpty();
    }

    @Test
    void shouldFailWithInvalidObservationCoding() throws IOException {
      // Load bundle test input
      String json =
          Files.readString(
              Path.of(
                  "src/test/resources/integrationtests/customValidators/input/strictCodingCheckExample-ObservationInvalid.json"));
      Bundle bundle = jsonParser.parseResource(Bundle.class, json);

      // Simulate available StructureDefinition(s) and ValueSet(s) relevant for Specimen.status
      Map<String, IBaseResource> structureDefinitions = new HashMap<>();
      Map<String, IBaseResource> valueSets = new HashMap<>();

      readValueSets(valueSets);

      readStructureDefinitions(structureDefinitions);

      // Instantiate custom validator module
      StrictValueSetMembershipValidator customValidator =
          new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

      FhirValidator validator = ctx.newValidator();
      validator.registerValidatorModule(customValidator);

      ValidationResult result = validator.validateWithResult(bundle);

      List<SingleValidationMessage> messages = result.getMessages();

      // Assert: Exactly one ERROR expected from custom validator
      assertThat(messages).filteredOn(m -> m.getSeverity() == ERROR).hasSize(1);
    }

    private static void readValueSets(Map<String, IBaseResource> valueSets) throws IOException {
      // Load ValueSets
      // get all files from
      // src/test/resources/integrationtests/customValidators/strictCodeValidation/6.1.8/Fhir/ValueSet
      Path pathValueSets =
          Path.of(
              "src/test/resources/integrationtests/customValidators/strictCodeValidation/brokenBinding/6.1.8/Fhir/ValueSet");
      readValueSetsFromPath(valueSets, pathValueSets);
    }

    private static void readStructureDefinitions(Map<String, IBaseResource> structureDefinitions)
        throws IOException {
      // Load Specimen profile (required for path/binding extraction)
      Path pathStructureDefinitions =
          Path.of(
              "src/test/resources/integrationtests/customValidators/strictCodeValidation/brokenBinding/6.1.8/Fhir/StructureDefinition");
      readStructureDefinitonsFromPath(structureDefinitions, pathStructureDefinitions);
    }
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

  private static void readValueSetsFromPath(
      Map<String, IBaseResource> valueSets, Path pathValueSets) throws IOException {
    Files.list(pathValueSets)
        .forEach(
            tmpPath -> {
              try {
                String jsonTmpString = Files.readString(tmpPath);
                ValueSet tmpValueSet = jsonParser.parseResource(ValueSet.class, jsonTmpString);
                valueSets.put(tmpValueSet.getUrl(), tmpValueSet);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Nested
  @DisplayName("Unit tests with mocked dependencies")
  class UnitTests {

    @Test
    @DisplayName("Should not validate when resource has no meta")
    void shouldNotValidateWhenResourceHasNoMeta() {
      // Arrange
      Map<String, IBaseResource> structureDefinitions = new HashMap<>();
      Map<String, IBaseResource> valueSets = new HashMap<>();

      StrictValueSetMembershipValidator validator =
          new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

      IValidationContext<IBaseResource> context = mock(IValidationContext.class);
      Bundle bundle = new Bundle();
      Bundle.BundleEntryComponent entry = bundle.addEntry();

      // Create a resource without meta
      Observation observation = new Observation();
      observation.setMeta(null); // explicitly no meta
      entry.setResource(observation);

      when(context.getResource()).thenReturn(bundle);

      // Act
      validator.validateResource(context);

      // Assert
      verify(context).getResource();
      verifyNoMoreInteractions(context);
    }

    @Test
    @DisplayName("Should not validate when resource meta has no profile")
    void shouldNotValidateWhenResourceMetaHasNoProfile() {
      // Arrange
      Map<String, IBaseResource> structureDefinitions = new HashMap<>();
      Map<String, IBaseResource> valueSets = new HashMap<>();

      StrictValueSetMembershipValidator validator =
          new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

      IValidationContext<IBaseResource> context = mock(IValidationContext.class);
      Bundle bundle = new Bundle();
      Bundle.BundleEntryComponent entry = bundle.addEntry();

      // Create a resource with meta but no profile
      Observation observation = new Observation();
      observation.setMeta(new org.hl7.fhir.r4.model.Meta()); // meta exists but no profile
      entry.setResource(observation);

      when(context.getResource()).thenReturn(bundle);

      // Act
      validator.validateResource(context);

      // Assert
      verify(context).getResource();
      verifyNoMoreInteractions(context);
    }

    @Test
    @DisplayName("Should not validate when resource profile list is empty")
    void shouldNotValidateWhenResourceProfileListIsEmpty() {
      // Arrange
      Map<String, IBaseResource> structureDefinitions = new HashMap<>();
      Map<String, IBaseResource> valueSets = new HashMap<>();

      StrictValueSetMembershipValidator validator =
          new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

      IValidationContext<IBaseResource> context = mock(IValidationContext.class);
      Bundle bundle = new Bundle();
      Bundle.BundleEntryComponent entry = bundle.addEntry();

      // Create a resource with empty profile list
      Observation observation = new Observation();
      org.hl7.fhir.r4.model.Meta meta = new org.hl7.fhir.r4.model.Meta();
      // Don't add any profiles - list is empty
      observation.setMeta(meta);
      entry.setResource(observation);

      when(context.getResource()).thenReturn(bundle);

      // Act
      validator.validateResource(context);

      // Assert
      verify(context).getResource();
      verifyNoMoreInteractions(context);
    }

    @Test
    @DisplayName("Should not validate when StructureDefinition not found in map")
    void shouldNotValidateWhenStructureDefinitionNotFoundInMap() {
      // Arrange
      Map<String, IBaseResource> structureDefinitions = new HashMap<>();
      Map<String, IBaseResource> valueSets = new HashMap<>();
      // structureDefinitions map is empty - profile won't be found

      StrictValueSetMembershipValidator validator =
          new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

      IValidationContext<IBaseResource> context = mock(IValidationContext.class);
      Bundle bundle = new Bundle();
      Bundle.BundleEntryComponent entry = bundle.addEntry();

      // Create a resource with a profile that won't be found
      Observation observation = new Observation();
      org.hl7.fhir.r4.model.Meta meta = new org.hl7.fhir.r4.model.Meta();
      meta.addProfile("http://example.com/StructureDefinition/UnknownProfile");
      observation.setMeta(meta);
      entry.setResource(observation);

      when(context.getResource()).thenReturn(bundle);

      // Act
      validator.validateResource(context);

      // Assert
      verify(context).getResource();
      verifyNoMoreInteractions(context);
    }

    @Nested
    @DisplayName("Tests for extractCodings method")
    class ExtractCodingsTests {

      @Test
      @DisplayName("Should extract codings from CodeableConcept")
      void shouldExtractCodingsFromCodeableConcept() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        CodeableConcept codeableConcept = new CodeableConcept();
        Coding coding1 = codeableConcept.addCoding();
        coding1.setSystem("http://snomed.info/sct");
        coding1.setCode("123456");
        Coding coding2 = codeableConcept.addCoding();
        coding2.setSystem("http://loinc.org");
        coding2.setCode("789");

        // Act - using reflection to access private method
        List<Coding> result = extractCodingsViaReflection(validator, codeableConcept);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSystem()).isEqualTo("http://snomed.info/sct");
        assertThat(result.get(0).getCode()).isEqualTo("123456");
        assertThat(result.get(1).getSystem()).isEqualTo("http://loinc.org");
        assertThat(result.get(1).getCode()).isEqualTo("789");
      }

      @Test
      @DisplayName("Should extract single coding from Coding")
      void shouldExtractSingleCodingFromCoding() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        Coding coding = new Coding();
        coding.setSystem("http://snomed.info/sct");
        coding.setCode("123456");

        // Act
        List<Coding> result = extractCodingsViaReflection(validator, coding);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSystem()).isEqualTo("http://snomed.info/sct");
        assertThat(result.get(0).getCode()).isEqualTo("123456");
      }

      @Test
      @DisplayName("Should extract coding from CodeType with code only, no system")
      void shouldExtractCodingFromCodeTypeWithoutSystem() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        CodeType codeType = new CodeType("some-code");

        // Act
        List<Coding> result = extractCodingsViaReflection(validator, codeType);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("some-code");
        assertThat(result.get(0).getSystem()).isNullOrEmpty();
      }

      @Test
      @DisplayName("Should return empty list for unsupported type")
      void shouldReturnEmptyListForUnsupportedType() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        org.hl7.fhir.r4.model.StringType stringType = new org.hl7.fhir.r4.model.StringType("test");

        // Act
        List<Coding> result = extractCodingsViaReflection(validator, stringType);

        // Assert
        assertThat(result).isEmpty();
      }

      private List<Coding> extractCodingsViaReflection(
          StrictValueSetMembershipValidator validator, IBase value) {
        try {
          java.lang.reflect.Method method =
              StrictValueSetMembershipValidator.class.getDeclaredMethod(
                  "extractCodings", IBase.class);
          method.setAccessible(true);
          return (List<Coding>) method.invoke(validator, value);
        } catch (Exception e) {
          throw new RuntimeException("Failed to invoke extractCodings via reflection", e);
        }
      }
    }

    @Nested
    @DisplayName("Tests for isCodedElement method")
    class IsCodedElementTests {

      @Test
      @DisplayName("Should return false when element has no type")
      void shouldReturnFalseWhenElementHasNoType() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ElementDefinition ed = new ElementDefinition();
        // no type set

        // Act
        boolean result = isCodedElementViaReflection(validator, ed);

        // Assert
        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("Should return false for non-coded type like string")
      void shouldReturnFalseForNonCodedType() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ElementDefinition ed = new ElementDefinition();
        ed.addType().setCode("string");

        // Act
        boolean result = isCodedElementViaReflection(validator, ed);

        // Assert
        assertThat(result).isFalse();
      }

      @ParameterizedTest
      @ValueSource(strings = {"CodeableConcept", "Coding", "code"})
      @DisplayName("Should return true for coded types")
      void shouldReturnTrueForCodedTypes(String typeCode) {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ElementDefinition ed = new ElementDefinition();
        ed.addType().setCode(typeCode);

        // Act
        boolean result = isCodedElementViaReflection(validator, ed);

        // Assert
        assertThat(result).isTrue();
      }

      @Test
      @DisplayName("Should return false for Identifier type without .type path")
      void shouldReturnFalseForIdentifierWithoutTypePath() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ElementDefinition ed = new ElementDefinition();
        ed.setPath("Patient.identifier");
        ed.addType().setCode("Identifier");

        // Act
        boolean result = isCodedElementViaReflection(validator, ed);

        // Assert
        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("Should return true for Identifier.type path")
      void shouldReturnTrueForIdentifierTypePath() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ElementDefinition ed = new ElementDefinition();
        ed.setPath("Patient.identifier.type");
        ed.addType().setCode("Identifier");

        // Act
        boolean result = isCodedElementViaReflection(validator, ed);

        // Assert
        assertThat(result).isTrue();
      }

      @Test
      @DisplayName("Should return false for Quantity type without .code path")
      void shouldReturnFalseForQuantityWithoutCodePath() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ElementDefinition ed = new ElementDefinition();
        ed.setPath("Observation.value");
        ed.addType().setCode("Quantity");

        // Act
        boolean result = isCodedElementViaReflection(validator, ed);

        // Assert
        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("Should return true for Quantity.code path")
      void shouldReturnTrueForQuantityCodePath() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ElementDefinition ed = new ElementDefinition();
        ed.setPath("Observation.value.code");
        ed.addType().setCode("Quantity");

        // Act
        boolean result = isCodedElementViaReflection(validator, ed);

        // Assert
        assertThat(result).isTrue();
      }

      private boolean isCodedElementViaReflection(
          StrictValueSetMembershipValidator validator, ElementDefinition ed) {
        try {
          java.lang.reflect.Method method =
              StrictValueSetMembershipValidator.class.getDeclaredMethod(
                  "isCodedElement", ElementDefinition.class);
          method.setAccessible(true);
          return (boolean) method.invoke(validator, ed);
        } catch (Exception e) {
          throw new RuntimeException("Failed to invoke isCodedElement via reflection", e);
        }
      }
    }

    @Nested
    @DisplayName("Tests for shouldStrictlyValidate method")
    class ShouldStrictlyValidateTests {

      @Test
      @DisplayName("Should return false when canonical is null")
      void shouldReturnFalseWhenCanonicalIsNull() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        // Act
        boolean result = shouldStrictlyValidateViaReflection(validator, null);

        // Assert
        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("Should return true when canonical starts with DEMIS prefix")
      void shouldReturnTrueWhenCanonicalStartsWithDemisPrefix() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        // Act
        boolean result =
            shouldStrictlyValidateViaReflection(
                validator, "https://demis.rki.de/fhir/ValueSet/myValueSet");

        // Assert
        assertThat(result).isTrue();
      }

      @Test
      @DisplayName("Should return false for non-DEMIS ValueSet without expansion")
      void shouldReturnFalseForNonDemisValueSetWithoutExpansion() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        // no expansion
        valueSets.put(vs.getUrl(), vs);

        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        // Act
        boolean result =
            shouldStrictlyValidateViaReflection(validator, "http://example.com/ValueSet/test");

        // Assert
        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("Should return false for non-DEMIS ValueSet with empty expansion")
      void shouldReturnFalseForNonDemisValueSetWithEmptyExpansion() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        vs.setExpansion(new ValueSet.ValueSetExpansionComponent());
        // expansion exists but no contains
        valueSets.put(vs.getUrl(), vs);

        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        // Act
        boolean result =
            shouldStrictlyValidateViaReflection(validator, "http://example.com/ValueSet/test");

        // Assert
        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("Should return true for non-DEMIS ValueSet with expansion and contains")
      void shouldReturnTrueForNonDemisValueSetWithExpansionAndContains() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
        expansion.addContains().setCode("test-code").setSystem("http://example.com");
        vs.setExpansion(expansion);
        valueSets.put(vs.getUrl(), vs);

        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        // Act
        boolean result =
            shouldStrictlyValidateViaReflection(validator, "http://example.com/ValueSet/test");

        // Assert
        assertThat(result).isTrue();
      }

      @Test
      @DisplayName("Should return false when ValueSet not found in map")
      void shouldReturnFalseWhenValueSetNotFoundInMap() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        // empty valueSets map

        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        // Act
        boolean result =
            shouldStrictlyValidateViaReflection(validator, "http://example.com/ValueSet/unknown");

        // Assert
        assertThat(result).isFalse();
      }

      private boolean shouldStrictlyValidateViaReflection(
          StrictValueSetMembershipValidator validator, String canonical) {
        try {
          java.lang.reflect.Method method =
              StrictValueSetMembershipValidator.class.getDeclaredMethod(
                  "shouldStrictlyValidate", String.class);
          method.setAccessible(true);
          return (boolean) method.invoke(validator, canonical);
        } catch (Exception e) {
          throw new RuntimeException("Failed to invoke shouldStrictlyValidate via reflection", e);
        }
      }
    }

    @Nested
    @DisplayName("Tests for resolveValueSet method")
    class ResolveValueSetTests {

      @Test
      @DisplayName("Should return null when canonical is null")
      void shouldReturnNullWhenCanonicalIsNull() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        // Act
        ValueSet result = resolveValueSetViaReflection(validator, null);

        // Assert
        assertThat(result).isNull();
      }

      @Test
      @DisplayName("Should resolve ValueSet by canonical URL from map")
      void shouldResolveValueSetByCanonicalFromMap() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        vs.setId("test-id");
        valueSets.put(vs.getUrl(), vs);

        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        // Act
        ValueSet result =
            resolveValueSetViaReflection(validator, "http://example.com/ValueSet/test");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUrl()).isEqualTo("http://example.com/ValueSet/test");
      }

      @Test
      @DisplayName("Should resolve ValueSet by URL when not found by canonical")
      void shouldResolveValueSetByUrlWhenNotFoundByCanonical() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        vs.setId("test-id");
        valueSets.put("different-key", vs); // stored with different key

        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        // Act
        ValueSet result =
            resolveValueSetViaReflection(validator, "http://example.com/ValueSet/test");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUrl()).isEqualTo("http://example.com/ValueSet/test");
      }

      @Test
      @DisplayName("Should resolve ValueSet by ID when not found by canonical or URL")
      void shouldResolveValueSetByIdWhenNotFoundByCanonicalOrUrl() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        vs.setId("test-id");
        valueSets.put("some-key", vs); // stored with different key

        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        // Act
        ValueSet result = resolveValueSetViaReflection(validator, "test-id");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("test-id");
      }

      @Test
      @DisplayName("Should return null when ValueSet not found")
      void shouldReturnNullWhenValueSetNotFound() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        // Act
        ValueSet result =
            resolveValueSetViaReflection(validator, "http://example.com/ValueSet/unknown");

        // Assert
        assertThat(result).isNull();
      }

      private ValueSet resolveValueSetViaReflection(
          StrictValueSetMembershipValidator validator, String canonicalOrUrl) {
        try {
          java.lang.reflect.Method method =
              StrictValueSetMembershipValidator.class.getDeclaredMethod(
                  "resolveValueSet", String.class);
          method.setAccessible(true);
          return (ValueSet) method.invoke(validator, canonicalOrUrl);
        } catch (Exception e) {
          throw new RuntimeException("Failed to invoke resolveValueSet via reflection", e);
        }
      }
    }

    @Nested
    @DisplayName("Tests for isCodingInValueSetExpansion method")
    class IsCodingInValueSetExpansionTests {

      @Test
      @DisplayName("Should return false when ValueSet is null")
      void shouldReturnFalseWhenValueSetIsNull() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        Coding coding = new Coding();
        coding.setSystem("http://snomed.info/sct");
        coding.setCode("123456");

        // Act
        boolean result = isCodingInValueSetExpansionViaReflection(validator, coding, null);

        // Assert
        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("Should return false when ValueSet has no expansion")
      void shouldReturnFalseWhenValueSetHasNoExpansion() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        // no expansion

        Coding coding = new Coding();
        coding.setSystem("http://snomed.info/sct");
        coding.setCode("123456");

        // Act
        boolean result = isCodingInValueSetExpansionViaReflection(validator, coding, vs);

        // Assert
        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("Should return false when ValueSet expansion has no contains")
      void shouldReturnFalseWhenExpansionHasNoContains() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        vs.setExpansion(new ValueSet.ValueSetExpansionComponent());
        // expansion exists but no contains

        Coding coding = new Coding();
        coding.setSystem("http://snomed.info/sct");
        coding.setCode("123456");

        // Act
        boolean result = isCodingInValueSetExpansionViaReflection(validator, coding, vs);

        // Assert
        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("Should return true when coding with system matches expansion")
      void shouldReturnTrueWhenCodingWithSystemMatches() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
        expansion
            .addContains()
            .setCode("123456")
            .setSystem("http://snomed.info/sct")
            .setDisplay("Test Code");
        vs.setExpansion(expansion);

        Coding coding = new Coding();
        coding.setSystem("http://snomed.info/sct");
        coding.setCode("123456");

        // Act
        boolean result = isCodingInValueSetExpansionViaReflection(validator, coding, vs);

        // Assert
        assertThat(result).isTrue();
      }

      @Test
      @DisplayName("Should return false when code matches but system differs")
      void shouldReturnFalseWhenCodeMatchesButSystemDiffers() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
        expansion.addContains().setCode("123456").setSystem("http://snomed.info/sct");
        vs.setExpansion(expansion);

        Coding coding = new Coding();
        coding.setSystem("http://loinc.org"); // different system
        coding.setCode("123456");

        // Act
        boolean result = isCodingInValueSetExpansionViaReflection(validator, coding, vs);

        // Assert
        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("Should return false when system matches but code differs")
      void shouldReturnFalseWhenSystemMatchesButCodeDiffers() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
        expansion.addContains().setCode("123456").setSystem("http://snomed.info/sct");
        vs.setExpansion(expansion);

        Coding coding = new Coding();
        coding.setSystem("http://snomed.info/sct");
        coding.setCode("999999"); // different code

        // Act
        boolean result = isCodingInValueSetExpansionViaReflection(validator, coding, vs);

        // Assert
        assertThat(result).isFalse();
      }

      @Test
      @DisplayName("Should return true for primitive code when only code matches (no system check)")
      void shouldReturnTrueForPrimitiveCodeWhenCodeMatches() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
        expansion.addContains().setCode("active").setSystem("http://hl7.org/fhir/specimen-status");
        vs.setExpansion(expansion);

        // Primitive code - no system
        Coding coding = new Coding();
        coding.setCode("active");
        coding.setSystem(""); // empty system indicates primitive code

        // Act
        boolean result = isCodingInValueSetExpansionViaReflection(validator, coding, vs);

        // Assert
        assertThat(result).isTrue();
      }

      @Test
      @DisplayName("Should match code in list of multiple contains")
      void shouldMatchCodeInListOfMultipleContains() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        ValueSet vs = new ValueSet();
        vs.setUrl("http://example.com/ValueSet/test");
        ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
        expansion.addContains().setCode("111111").setSystem("http://snomed.info/sct");
        expansion.addContains().setCode("222222").setSystem("http://snomed.info/sct");
        expansion.addContains().setCode("333333").setSystem("http://snomed.info/sct");
        vs.setExpansion(expansion);

        Coding coding = new Coding();
        coding.setSystem("http://snomed.info/sct");
        coding.setCode("222222");

        // Act
        boolean result = isCodingInValueSetExpansionViaReflection(validator, coding, vs);

        // Assert
        assertThat(result).isTrue();
      }

      private boolean isCodingInValueSetExpansionViaReflection(
          StrictValueSetMembershipValidator validator, Coding coding, ValueSet vs) {
        try {
          java.lang.reflect.Method method =
              StrictValueSetMembershipValidator.class.getDeclaredMethod(
                  "isCodingInValueSetExpansion", Coding.class, ValueSet.class);
          method.setAccessible(true);
          return (boolean) method.invoke(validator, coding, vs);
        } catch (Exception e) {
          throw new RuntimeException(
              "Failed to invoke isCodingInValueSetExpansion via reflection", e);
        }
      }
    }

    @Nested
    @DisplayName("Tests for collectRequiredBindings method")
    class CollectRequiredBindingsTests {

      @Test
      @DisplayName("Should return empty map when StructureDefinition has no snapshot")
      void shouldReturnEmptyMapWhenNoSnapshot() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        StructureDefinition sd = new StructureDefinition();
        sd.setUrl("http://example.com/StructureDefinition/test");
        // no snapshot

        // Act
        Map<String, List<BindingInfo>> result = collectRequiredBindingsViaReflection(validator, sd);

        // Assert
        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("Should return empty map when snapshot has no elements")
      void shouldReturnEmptyMapWhenSnapshotHasNoElements() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        StructureDefinition sd = new StructureDefinition();
        sd.setUrl("http://example.com/StructureDefinition/test");
        sd.setSnapshot(new StructureDefinition.StructureDefinitionSnapshotComponent());
        // snapshot exists but no elements

        // Act
        Map<String, List<BindingInfo>> result = collectRequiredBindingsViaReflection(validator, sd);

        // Assert
        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("Should not include element without binding")
      void shouldNotIncludeElementWithoutBinding() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        StructureDefinition sd = new StructureDefinition();
        sd.setUrl("http://example.com/StructureDefinition/test");
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot =
            new StructureDefinition.StructureDefinitionSnapshotComponent();
        ElementDefinition ed = snapshot.addElement();
        ed.setPath("Observation.status");
        ed.addType().setCode("code");
        // no binding
        sd.setSnapshot(snapshot);

        // Act
        Map<String, List<BindingInfo>> result = collectRequiredBindingsViaReflection(validator, sd);

        // Assert
        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("Should not include element with binding but no strength")
      void shouldNotIncludeElementWithBindingButNoStrength() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        StructureDefinition sd = new StructureDefinition();
        sd.setUrl("http://example.com/StructureDefinition/test");
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot =
            new StructureDefinition.StructureDefinitionSnapshotComponent();
        ElementDefinition ed = snapshot.addElement();
        ed.setPath("Observation.status");
        ed.addType().setCode("code");
        ElementDefinition.ElementDefinitionBindingComponent binding =
            new ElementDefinition.ElementDefinitionBindingComponent();
        binding.setValueSet("http://hl7.org/fhir/ValueSet/specimen-status");
        // no strength
        ed.setBinding(binding);
        sd.setSnapshot(snapshot);

        // Act
        Map<String, List<BindingInfo>> result = collectRequiredBindingsViaReflection(validator, sd);

        // Assert
        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("Should not include element with EXTENSIBLE binding strength")
      void shouldNotIncludeElementWithExtensibleBinding() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        StructureDefinition sd = new StructureDefinition();
        sd.setUrl("http://example.com/StructureDefinition/test");
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot =
            new StructureDefinition.StructureDefinitionSnapshotComponent();
        ElementDefinition ed = snapshot.addElement();
        ed.setPath("Observation.category");
        ed.addType().setCode("CodeableConcept");
        ElementDefinition.ElementDefinitionBindingComponent binding =
            new ElementDefinition.ElementDefinitionBindingComponent();
        binding.setValueSet("http://hl7.org/fhir/ValueSet/observation-category");
        binding.setStrength(Enumerations.BindingStrength.EXTENSIBLE);
        ed.setBinding(binding);
        sd.setSnapshot(snapshot);

        // Act
        Map<String, List<BindingInfo>> result = collectRequiredBindingsViaReflection(validator, sd);

        // Assert
        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("Should not include element with REQUIRED binding but no ValueSet")
      void shouldNotIncludeElementWithRequiredBindingButNoValueSet() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        StructureDefinition sd = new StructureDefinition();
        sd.setUrl("http://example.com/StructureDefinition/test");
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot =
            new StructureDefinition.StructureDefinitionSnapshotComponent();
        ElementDefinition ed = snapshot.addElement();
        ed.setPath("Observation.status");
        ed.addType().setCode("code");
        ElementDefinition.ElementDefinitionBindingComponent binding =
            new ElementDefinition.ElementDefinitionBindingComponent();
        binding.setStrength(Enumerations.BindingStrength.REQUIRED);
        // no ValueSet
        ed.setBinding(binding);
        sd.setSnapshot(snapshot);

        // Act
        Map<String, List<BindingInfo>> result = collectRequiredBindingsViaReflection(validator, sd);

        // Assert
        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("Should include DEMIS ValueSet with REQUIRED binding")
      void shouldIncludeDemisValueSetWithRequiredBinding() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        StructureDefinition sd = new StructureDefinition();
        sd.setUrl("http://example.com/StructureDefinition/test");
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot =
            new StructureDefinition.StructureDefinitionSnapshotComponent();
        ElementDefinition ed = snapshot.addElement();
        ed.setPath("Observation.code");
        ed.addType().setCode("CodeableConcept");
        ElementDefinition.ElementDefinitionBindingComponent binding =
            new ElementDefinition.ElementDefinitionBindingComponent();
        binding.setValueSet("https://demis.rki.de/fhir/ValueSet/testValueSet");
        binding.setStrength(Enumerations.BindingStrength.REQUIRED);
        ed.setBinding(binding);
        sd.setSnapshot(snapshot);

        // Act
        Map<String, List<BindingInfo>> result = collectRequiredBindingsViaReflection(validator, sd);

        // Assert
        assertThat(result).hasSize(1).containsKey("Observation.code");
        assertThat(result.get("Observation.code"))
            .hasSize(1)
            .first()
            .extracting(BindingInfo::valueSetCanonical)
            .isEqualTo("https://demis.rki.de/fhir/ValueSet/testValueSet");
      }

      private Map<String, List<BindingInfo>> collectRequiredBindingsViaReflection(
          StrictValueSetMembershipValidator validator, StructureDefinition sd) {
        try {
          java.lang.reflect.Method method =
              StrictValueSetMembershipValidator.class.getDeclaredMethod(
                  "collectRequiredBindings", StructureDefinition.class);
          method.setAccessible(true);
          return (Map<String, List<BindingInfo>>) method.invoke(validator, sd);
        } catch (Exception e) {
          throw new RuntimeException("Failed to invoke collectRequiredBindings via reflection", e);
        }
      }
    }

    @Nested
    @DisplayName("Tests for safeGetValues method")
    class SafeGetValuesTests {

      @Test
      @DisplayName("Should return empty list when terser throws exception")
      void shouldReturnEmptyListWhenTerserThrowsException() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        FhirTerser terser = mock(FhirTerser.class);
        org.hl7.fhir.r4.model.Observation observation = new org.hl7.fhir.r4.model.Observation();

        when(terser.getValues(observation, "invalid.path"))
            .thenThrow(new RuntimeException("Invalid path"));

        // Act
        List<IBase> result =
            safeGetValuesViaReflection(validator, terser, observation, "invalid.path");

        // Assert
        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("Should return empty list when terser returns null")
      void shouldReturnEmptyListWhenTerserReturnsNull() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        FhirTerser terser = mock(FhirTerser.class);
        org.hl7.fhir.r4.model.Observation observation = new org.hl7.fhir.r4.model.Observation();

        when(terser.getValues(observation, "some.path")).thenReturn(null);

        // Act
        List<IBase> result =
            safeGetValuesViaReflection(validator, terser, observation, "some.path");

        // Assert
        assertThat(result).isEmpty();
      }

      @Test
      @DisplayName("Should return list when terser returns valid values")
      void shouldReturnListWhenTerserReturnsValidValues() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        FhirTerser terser = mock(FhirTerser.class);
        org.hl7.fhir.r4.model.Observation observation = new org.hl7.fhir.r4.model.Observation();

        CodeableConcept cc = new CodeableConcept();
        when(terser.getValues(observation, "Observation.code")).thenReturn(List.of(cc));

        // Act
        List<IBase> result =
            safeGetValuesViaReflection(validator, terser, observation, "Observation.code");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(cc);
      }

      private List<IBase> safeGetValuesViaReflection(
          StrictValueSetMembershipValidator validator,
          FhirTerser terser,
          IBaseResource resource,
          String path) {
        try {
          java.lang.reflect.Method method =
              StrictValueSetMembershipValidator.class.getDeclaredMethod(
                  "safeGetValues", FhirTerser.class, IBaseResource.class, String.class);
          method.setAccessible(true);
          return (List<IBase>) method.invoke(validator, terser, resource, path);
        } catch (Exception e) {
          throw new RuntimeException("Failed to invoke safeGetValues via reflection", e);
        }
      }
    }

    @Nested
    @DisplayName("Tests for findExpectedSystemForSlice method")
    class FindExpectedSystemForSliceTests {

      @Test
      @DisplayName("Should return null when element has no sliceName")
      void shouldReturnNullWhenElementHasNoSliceName() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        StructureDefinition sd = new StructureDefinition();
        sd.setUrl("http://example.com/StructureDefinition/test");
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot =
            new StructureDefinition.StructureDefinitionSnapshotComponent();

        ElementDefinition ed = snapshot.addElement();
        ed.setPath("Observation.code");
        ed.setId("Observation.code");
        // no sliceName

        sd.setSnapshot(snapshot);

        // Act
        String result = findExpectedSystemForSliceViaReflection(validator, sd, ed);

        // Assert
        assertThat(result).isNull();
      }

      @Test
      @DisplayName("Should return null when system element not found")
      void shouldReturnNullWhenSystemElementNotFound() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        StructureDefinition sd = new StructureDefinition();
        sd.setUrl("http://example.com/StructureDefinition/test");
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot =
            new StructureDefinition.StructureDefinitionSnapshotComponent();

        ElementDefinition sliceElement = snapshot.addElement();
        sliceElement.setPath("Observation.code.coding");
        sliceElement.setId("Observation.code.coding:snomedSlice");
        sliceElement.setSliceName("snomedSlice");
        // no .system element added

        sd.setSnapshot(snapshot);

        // Act
        String result = findExpectedSystemForSliceViaReflection(validator, sd, sliceElement);

        // Assert
        assertThat(result).isNull();
      }

      @Test
      @DisplayName("Should return patternUri when system element has pattern")
      void shouldReturnPatternUriWhenSystemElementHasPattern() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        StructureDefinition sd = new StructureDefinition();
        sd.setUrl("http://example.com/StructureDefinition/test");
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot =
            new StructureDefinition.StructureDefinitionSnapshotComponent();

        ElementDefinition sliceElement = snapshot.addElement();
        sliceElement.setPath("Observation.code.coding");
        sliceElement.setId("Observation.code.coding:snomedSlice");
        sliceElement.setSliceName("snomedSlice");

        ElementDefinition systemElement = snapshot.addElement();
        systemElement.setPath("Observation.code.coding.system");
        systemElement.setId("Observation.code.coding:snomedSlice.system");
        systemElement.setPattern(new org.hl7.fhir.r4.model.UriType("http://snomed.info/sct"));

        sd.setSnapshot(snapshot);

        // Act
        String result = findExpectedSystemForSliceViaReflection(validator, sd, sliceElement);

        // Assert
        assertThat(result).isEqualTo("http://snomed.info/sct");
      }

      @Test
      @DisplayName("Should return fixedUri when system element has fixed value")
      void shouldReturnFixedUriWhenSystemElementHasFixedValue() {
        // Arrange
        Map<String, IBaseResource> structureDefinitions = new HashMap<>();
        Map<String, IBaseResource> valueSets = new HashMap<>();
        StrictValueSetMembershipValidator validator =
            new StrictValueSetMembershipValidator(valueSets, structureDefinitions);

        StructureDefinition sd = new StructureDefinition();
        sd.setUrl("http://example.com/StructureDefinition/test");
        StructureDefinition.StructureDefinitionSnapshotComponent snapshot =
            new StructureDefinition.StructureDefinitionSnapshotComponent();

        ElementDefinition sliceElement = snapshot.addElement();
        sliceElement.setPath("Observation.code.coding");
        sliceElement.setId("Observation.code.coding:loincSlice");
        sliceElement.setSliceName("loincSlice");

        ElementDefinition systemElement = snapshot.addElement();
        systemElement.setPath("Observation.code.coding.system");
        systemElement.setId("Observation.code.coding:loincSlice.system");
        systemElement.setFixed(new org.hl7.fhir.r4.model.UriType("http://loinc.org"));

        sd.setSnapshot(snapshot);

        // Act
        String result = findExpectedSystemForSliceViaReflection(validator, sd, sliceElement);

        // Assert
        assertThat(result).isEqualTo("http://loinc.org");
      }

      private String findExpectedSystemForSliceViaReflection(
          StrictValueSetMembershipValidator validator,
          StructureDefinition sd,
          ElementDefinition sliceElement) {
        try {
          java.lang.reflect.Method method =
              StrictValueSetMembershipValidator.class.getDeclaredMethod(
                  "findExpectedSystemForSlice", StructureDefinition.class, ElementDefinition.class);
          method.setAccessible(true);
          return (String) method.invoke(validator, sd, sliceElement);
        } catch (Exception e) {
          throw new RuntimeException(
              "Failed to invoke findExpectedSystemForSlice via reflection", e);
        }
      }
    }
  }
}
