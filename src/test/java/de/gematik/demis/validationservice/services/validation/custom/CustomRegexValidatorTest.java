package de.gematik.demis.validationservice.services.validation.custom;

/*-
 * #%L
 * validation-service
 * %%
 * Copyright (C) 2025 gematik GmbH
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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CustomRegexValidatorTest {

  private FhirContext ctx;
  private FhirValidator validator;

  @Nested
  class StringTests {
    @BeforeEach
    void setup() throws IOException {
      ctx = FhirContext.forR4();

      String questionnaireJson =
          Files.readString(Path.of("src/test/resources/regex/QuestionnaireString.json"));
      Questionnaire questionnaire =
          ctx.newJsonParser().parseResource(Questionnaire.class, questionnaireJson);

      Map<String, org.hl7.fhir.instance.model.api.IBaseResource> questionnaireMap =
          Map.of(questionnaire.getUrl(), questionnaire);

      validator = ctx.newValidator();
      validator.registerValidatorModule(new CustomRegexValidator(questionnaireMap));
    }

    @Test
    void testValidRegex() throws IOException {
      String responseJson =
          Files.readString(
              Path.of("src/test/resources/regex/QuestionnaireStringResponseValid.json"));
      QuestionnaireResponse response =
          ctx.newJsonParser().parseResource(QuestionnaireResponse.class, responseJson);

      ValidationResult result = validator.validateWithResult(response);
      assertThat(result.isSuccessful()).as("Validation should succeed for valid regex").isTrue();
    }

    @Test
    void testInvalidRegex() throws IOException {
      String responseJson =
          Files.readString(
              Path.of("src/test/resources/regex/QuestionnaireStringResponseInvalid.json"));
      QuestionnaireResponse response =
          ctx.newJsonParser().parseResource(QuestionnaireResponse.class, responseJson);

      ValidationResult result = validator.validateWithResult(response);
      assertThat(result.isSuccessful())
          .as("Validation should fail for QuestionnaireStringResponseInvalid.json regex")
          .isFalse();
      assertThat(result.getMessages()).hasSize(1);
      assertThat(result.getMessages().getFirst().getMessage())
          .contains("does not match the regex:");
    }
  }

  @Nested
  class DateTests {

    @BeforeEach
    void setup() throws IOException {
      ctx = FhirContext.forR4();

      String questionnaireJson =
          Files.readString(Path.of("src/test/resources/regex/QuestionnaireDate.json"));
      Questionnaire questionnaire =
          ctx.newJsonParser().parseResource(Questionnaire.class, questionnaireJson);

      Map<String, org.hl7.fhir.instance.model.api.IBaseResource> questionnaireMap =
          Map.of(questionnaire.getUrl(), questionnaire);

      validator = ctx.newValidator();
      validator.registerValidatorModule(new CustomRegexValidator(questionnaireMap));
    }

    @Test
    void testValidDateRegex() throws IOException {
      String responseJson =
          Files.readString(Path.of("src/test/resources/regex/QuestionnaireDateResponseValid.json"));
      QuestionnaireResponse response =
          ctx.newJsonParser().parseResource(QuestionnaireResponse.class, responseJson);

      ValidationResult result = validator.validateWithResult(response);
      assertThat(result.isSuccessful())
          .as("Validation should succeed for valid date regex")
          .isTrue();
    }

    @Test
    void testInvalidDateRegex() throws IOException {
      String responseJson =
          Files.readString(
              Path.of("src/test/resources/regex/QuestionnaireDateResponseInvalid.json"));
      QuestionnaireResponse response =
          ctx.newJsonParser().parseResource(QuestionnaireResponse.class, responseJson);

      ValidationResult result = validator.validateWithResult(response);
      assertThat(result.isSuccessful())
          .as("Validation should fail for invalid date regex")
          .isFalse();
      assertThat(result.getMessages()).hasSize(1);
      assertThat(result.getMessages().getFirst().getMessage()).contains("does not match the regex");
    }
  }
}
