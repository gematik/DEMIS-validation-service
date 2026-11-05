package de.gematik.demis.validationservice.integration;

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
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.validationservice.ValidationServiceApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@SpringBootTest(
    classes = {ValidationServiceApplication.class},
    properties = {
      "demis.validation-service.profiles.basepath=src/test/resources/integrationtests/customValidators/quantity",
      "demis.validation-service.profiles.versions=6.1.2"
    })
class ExtensionValidatorIntegrationTest {
  static final String MESSAGE_ID_UNEXPECTED_EXTENSION = "Unexpected_Extension";
  static final String MESSAGE_ID_MODIFIER_EXTENSION = "Modifier_Extension_not_allowed";

  static final String EXTENSION_INPUT_FILE =
      "src/test/resources/integrationtests/customValidators/input/ExtensionValidatorInput.json";
  static final String MODIFIER_EXTENSION_INPUT_FILE =
      "src/test/resources/integrationtests/customValidators/input/ModifierExtensionValidatorInput.json";

  @Autowired private MockMvc mockMvc;
  @Autowired private FhirContext fhirContext;

  private static List<String> filterOperationOutcomeByMessageIdAndMapToLocation(
      final OperationOutcome operationOutcome, final String messageId) {
    return operationOutcome.getIssue().stream()
        .filter(
            issue ->
                issue.getDetails().hasCoding("http://hl7.org/fhir/java-core-messageId", messageId))
        .map(OperationOutcome.OperationOutcomeIssueComponent::getLocation)
        .flatMap(List::stream)
        .map(StringType::getValue)
        .toList();
  }

  private OperationOutcome executeValidationAndExpectStatus(
      final String inputFile, final int expectedStatus) throws Exception {
    final String notification = Files.readString(Path.of(inputFile));

    final MvcResult mvcResult =
        mockMvc
            .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(notification))
            .andExpect(status().is(expectedStatus))
            .andReturn();
    final String body = mvcResult.getResponse().getContentAsString();
    return fhirContext.newJsonParser().parseResource(OperationOutcome.class, body);
  }

  @Test
  void notificationWithUnexpectedExtensionsIsValidAndHasWarnings() throws Exception {
    final OperationOutcome operationOutcome =
        executeValidationAndExpectStatus(EXTENSION_INPUT_FILE, 200);

    assertThat(
            filterOperationOutcomeByMessageIdAndMapToLocation(
                operationOutcome, MESSAGE_ID_UNEXPECTED_EXTENSION))
        .containsExactlyInAnyOrder(
            "Parameters.parameter[0].resource/*Bundle/dd7fac8c-592f-357d-aed3-a3c3268e2c81*/.entry[2].resource/*Patient/null*/.address[0].extension[1]",
            "Parameters.parameter[0].resource/*Bundle/dd7fac8c-592f-357d-aed3-a3c3268e2c81*/.entry[7].resource/*QuestionnaireResponse/null*/.item[0].answer[0].value[x].extension[0]");
  }

  @Test
  void notificationWithModifierExtensionsIsInvalidAndHasErrors() throws Exception {
    final OperationOutcome operationOutcome =
        executeValidationAndExpectStatus(MODIFIER_EXTENSION_INPUT_FILE, 422);

    assertThat(
            filterOperationOutcomeByMessageIdAndMapToLocation(
                operationOutcome, MESSAGE_ID_MODIFIER_EXTENSION))
        .containsExactlyInAnyOrder(
            "Parameters.parameter[0].resource/*Bundle/dd7fac8c-592f-357d-aed3-a3c3268e2c81*/.entry[0].resource/*Composition/null*/.modifierExtension[0]",
            "Parameters.parameter[0].resource/*Bundle/dd7fac8c-592f-357d-aed3-a3c3268e2c81*/.entry[0].resource/*Composition/null*/.modifierExtension[1]",
            "Parameters.parameter[0].resource/*Bundle/dd7fac8c-592f-357d-aed3-a3c3268e2c81*/.entry[2].resource/*Patient/null*/.modifierExtension[0]");
  }
}
