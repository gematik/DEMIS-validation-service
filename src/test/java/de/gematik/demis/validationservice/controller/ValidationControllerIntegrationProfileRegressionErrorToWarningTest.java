package de.gematik.demis.validationservice.controller;

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

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.gematik.demis.validationservice.ValidationServiceApplication;
import de.gematik.demis.validationservice.util.FileTestUtil;
import de.gematik.demis.validationservice.util.ResourceFileConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles(value = {"test"})
@SpringBootTest(
    classes = {ValidationServiceApplication.class},
    properties = {"feature.flag.filtered.errors.as.warnings.disabled=false"})
class ValidationControllerIntegrationProfileRegressionErrorToWarningTest {
  @Autowired private WebApplicationContext webApplicationContext;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Autowired Environment env;

  @Test
  void shouldReturn() throws Exception {
    final String validFileContent =
        FileTestUtil.readFileIntoString(
            "src/test/resources/RegressiontestForValueQuantityCorrect.xml");

    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_XML_VALUE).content(validFileContent))
        .andExpect(status().isOk());
  }

  @Test
  void passValidReportAnCheckItIsSuccessful() throws Exception {
    final String validFileContent =
        FileTestUtil.readFileIntoString(
            ResourceFileConstants.VALID_REPORT_BED_OCCUPANCY_JSON_EXAMPLE);
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(validFileContent))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
        .andExpect(jsonPath("$..issue[?(@.severity == 'error')]").doesNotExist())
        .andExpect(jsonPath("$..issue[?(@.severity == 'fatal')]").doesNotExist());
  }

  @DisplayName("check different json content types statements")
  @ParameterizedTest
  @ValueSource(
      strings = {
        APPLICATION_JSON_VALUE,
        "application/json+fhir",
        "application/fhir+json",
      })
  void passValidReportAnCheckItIsSuccessfulWithDifferentJsonContentTypes(String contentType)
      throws Exception {
    final String validFileContent =
        FileTestUtil.readFileIntoString(
            ResourceFileConstants.VALID_REPORT_BED_OCCUPANCY_JSON_EXAMPLE);
    mockMvc
        .perform(post("/$validate").contentType(contentType).content(validFileContent))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
        .andExpect(jsonPath("$..issue[?(@.severity == 'error')]").doesNotExist())
        .andExpect(jsonPath("$..issue[?(@.severity == 'fatal')]").doesNotExist());
  }

  @DisplayName("check different xml content types statements")
  @ParameterizedTest
  @ValueSource(
      strings = {
        APPLICATION_XML_VALUE,
        "application/xml+fhir",
        "application/fhir+xml",
      })
  void passValidReportAnCheckItIsSuccessfulWithDifferentXMLContentTypes(String contentType)
      throws Exception {
    final String validFileContent =
        FileTestUtil.readFileIntoString(
            ResourceFileConstants.VALID_REPORT_BED_OCCUPANCY_XML_EXAMPLE);
    mockMvc
        .perform(post("/$validate").contentType(contentType).content(validFileContent))
        .andExpect(status().isOk());
  }

  @Test
  void passInvalidReportAndCheckItHasErrors() throws Exception {
    final String validFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_REPORT_BED_OCCUPANCY_EXAMPLE);
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(validFileContent))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
        .andExpect(jsonPath("$..issue[?(@.severity == 'error')]").exists());
  }

  @Test
  void passInvalidDv2MessageAndCheckMatchingProfileIssuesAreNotFiltered() throws Exception {
    final String invalidFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_TEST_NOTIFICATION_DV_2);
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(invalidFileContent))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
        .andExpect(
            jsonPath(
                "$..issue[*].diagnostics",
                hasItems(startsWith("Unable to find a match for profile"))));
  }

  @Test
  void passInvalidJsonAnCheckItHasErrors() throws Exception {
    final String invalidJson = "{ \"key\" : \"value\"";
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(invalidJson))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
        .andExpect(jsonPath("$..issue[?(@.severity == 'fatal')]").exists());
  }

  @Test
  void passXmlContentTypeAnCheckItIsNotSupported() throws Exception {
    final String xmlContent =
        FileTestUtil.readFileIntoString(
            ResourceFileConstants.VALID_REPORT_BED_OCCUPANCY_XML_EXAMPLE);
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_XML_VALUE).content(xmlContent))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  void passXmlAsJsonAnCheckItIsNotSupported() throws Exception {
    final String xmlContent = "<test></test>";
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(xmlContent))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void passInvalidXmlCheckItIsNotSupported() throws Exception {
    final String xmlContent = "<test></test";
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_XML_VALUE).content(xmlContent))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void passNotParsableReportAnCheckItHasErrors() throws Exception {
    final String validFileContent =
        FileTestUtil.readFileIntoString(
            ResourceFileConstants.NOT_PARSEABLE_REPORT_BED_OCCUPANCY_EXAMPLE);
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(validFileContent))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
        .andExpect(jsonPath("$..issue[?(@.severity == 'fatal')]").exists());
  }
}
