package de.gematik.demis.validationservice.integration;

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

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.demis.validationservice.ValidationServiceApplication;
import de.gematik.demis.validationservice.util.FileTestUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(
    classes = {ValidationServiceApplication.class},
    properties = {
      "feature.flag.custom.regex.validator.enabled=true",
      "feature.flag.custom.quantity.validator.enabled=true",
      "demis.validation-service.profiles.basepath=src/test/resources/integrationtests/customValidators",
      "demis.validation-service.profiles.versions=6.1.2"
    })
class CustomValidatorIntegrationTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void shouldReturnSuccessForValidNotification() throws Exception {
    final String validFileContent =
        FileTestUtil.readFileIntoString(
            "src/test/resources/integrationtests/customValidators/input/customValidatorInput.json");

    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(validFileContent))
        .andExpect(status().isOk());
  }

  @Test
  void shouldReturnUnprocessableEntityForInvalidNotification() throws Exception {
    final String validFileContent =
        FileTestUtil.readFileIntoString(
            "src/test/resources/integrationtests/customValidators/input/invalidCustomValidatorInput.json");

    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(validFileContent))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            content()
                .string(
                    allOf(
                        containsString("Quantity in item newbornWeight violates constraint"),
                        containsString(
                            "Answer for item timeOfInfection does not match the regex"))));
  }
}
