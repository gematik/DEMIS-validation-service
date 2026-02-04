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

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import de.gematik.demis.validationservice.services.validation.custom.questionnaire.responses.CustomQuantityComparatorQuestionnaireResponseValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomQuantityComparatorValidatorTest {

  private FhirContext ctx;
  private FhirValidator validator;

  @BeforeEach
  void setup() throws IOException {
    ctx = FhirContext.forR4();

    String questionnaireJson =
        Files.readString(Path.of("src/test/resources/quantity/Questionnaire.json"));
    Questionnaire questionnaire =
        ctx.newJsonParser().parseResource(Questionnaire.class, questionnaireJson);

    Map<String, org.hl7.fhir.instance.model.api.IBaseResource> questionnaireMap =
        Map.of(questionnaire.getUrl(), questionnaire);

    validator = ctx.newValidator();
    validator.registerValidatorModule(
        new CustomQuantityComparatorQuestionnaireResponseValidator(questionnaireMap));
  }

  private void assertValidQuantityResponse(String responsePath) throws IOException {
    String responseJson = Files.readString(Path.of(responsePath));
    QuestionnaireResponse response =
        ctx.newJsonParser().parseResource(QuestionnaireResponse.class, responseJson);

    ValidationResult result = validator.validateWithResult(response);
    assertThat(result.isSuccessful()).as("Validation should succeed for valid quantity").isTrue();
  }

  private void assertInvalidQuantityResponse(String responsePath) throws IOException {
    String responseJson = Files.readString(Path.of(responsePath));
    QuestionnaireResponse response =
        ctx.newJsonParser().parseResource(QuestionnaireResponse.class, responseJson);
    ValidationResult result = validator.validateWithResult(response);
    assertThat(result.isSuccessful())
        .as("Validation should fail for " + responsePath + " quantity")
        .isFalse();
    assertThat(result.getMessages()).hasSize(1);
    assertThat(result.getMessages().getFirst().getMessage()).contains("violates constraint");
  }

  @Test
  void testValidQuantity() throws IOException {
    assertValidQuantityResponse(
        "src/test/resources/quantity/QuestionnaireResponseValidGlucose.json");
  }

  @Test
  void testValidQuantityEquals() throws IOException {
    assertValidQuantityResponse(
        "src/test/resources/quantity/QuestionnaireResponseValidLargerEqualsGlucose.json");
  }

  @Test
  void testValidQuantityEqualsWeight() throws IOException {
    assertValidQuantityResponse(
        "src/test/resources/quantity/QuestionnaireResponseValidWeight.json");
  }

  @Test
  void testInvalidQuantity() throws IOException {
    assertInvalidQuantityResponse(
        "src/test/resources/quantity/QuestionnaireResponseInvalidGlucose.json");
  }

  @Test
  void testInvalidQuantityInvalidLargerEqualsGlucose() throws IOException {
    assertInvalidQuantityResponse(
        "src/test/resources/quantity/QuestionnaireResponseInvalidLargerEqualsGlucose.json");
  }

  @Test
  void testInvalidQuantityInvalidSmallerWeight() throws IOException {
    assertInvalidQuantityResponse(
        "src/test/resources/quantity/QuestionnaireResponseInvalidSmallerWeight.json");
  }

  @Test
  void testInvalidQuantityInvalidLargerWeight() throws IOException {
    assertInvalidQuantityResponse(
        "src/test/resources/quantity/QuestionnaireResponseInvalidLargerWeight.json");
  }

  @Test
  void testUnitMismatch() throws IOException {
    String responseJson =
        Files.readString(
            Path.of("src/test/resources/quantity/QuestionnaireResponseInvalidUnit.json"));

    QuestionnaireResponse response =
        ctx.newJsonParser().parseResource(QuestionnaireResponse.class, responseJson);

    ValidationResult result = validator.validateWithResult(response);

    assertThat(result.isSuccessful()).as("Validation should fail for unit mismatch").isFalse();
    assertThat(result.getMessages()).hasSize(1);
    assertThat(result.getMessages().getFirst().getMessage()).contains("Unit mismatch");
  }

  @Test
  void testMissingValueInQuestionnaire() throws IOException {
    FhirContext singleCtx = FhirContext.forR4();

    String questionnaireJson =
        Files.readString(Path.of("src/test/resources/quantity/QuestionnaireMissingRefValue.json"));
    Questionnaire questionnaire =
        singleCtx.newJsonParser().parseResource(Questionnaire.class, questionnaireJson);

    Map<String, org.hl7.fhir.instance.model.api.IBaseResource> questionnaireMap =
        Map.of(questionnaire.getUrl(), questionnaire);

    validator = singleCtx.newValidator();
    validator.registerValidatorModule(
        new CustomQuantityComparatorQuestionnaireResponseValidator(questionnaireMap));

    String responseJson =
        Files.readString(
            Path.of("src/test/resources/quantity/QuestionnaireResponseValidGlucose2.json"));

    QuestionnaireResponse response =
        ctx.newJsonParser().parseResource(QuestionnaireResponse.class, responseJson);

    ValidationResult result = validator.validateWithResult(response);

    assertThat(result.isSuccessful())
        .as("Missing value in Questionnaire should lead to true")
        .isTrue();
  }

  @Test
  void testMissingValueInQuestionnaireResponse() throws IOException {
    FhirContext singleCtx = FhirContext.forR4();

    String questionnaireJson =
        Files.readString(Path.of("src/test/resources/quantity/Questionnaire.json"));
    Questionnaire questionnaire =
        singleCtx.newJsonParser().parseResource(Questionnaire.class, questionnaireJson);

    Map<String, org.hl7.fhir.instance.model.api.IBaseResource> questionnaireMap =
        Map.of(questionnaire.getUrl(), questionnaire);

    validator = singleCtx.newValidator();
    validator.registerValidatorModule(
        new CustomQuantityComparatorQuestionnaireResponseValidator(questionnaireMap));

    String responseJson =
        Files.readString(
            Path.of("src/test/resources/quantity/QuestionnaireResponseInvalidGlucose2.json"));

    QuestionnaireResponse response =
        ctx.newJsonParser().parseResource(QuestionnaireResponse.class, responseJson);

    ValidationResult result = validator.validateWithResult(response);

    assertThat(result.isSuccessful())
        .as("Missing value in QuestionnaireResponse should lead to false")
        .isFalse();
  }
}
