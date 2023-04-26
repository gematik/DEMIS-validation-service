/*
 * Copyright [2023], gematik GmbH
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either expressed or implied.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 */

package de.gematik.demis.validationservice.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@ActiveProfiles(value = {"test"})
@SpringBootTest(classes = {ValidationServiceApplication.class})
class ValidationControllerIT {
  @Autowired private WebApplicationContext webApplicationContext;
  private MockMvc mockMvc;

  @BeforeEach
  public void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void passValidReportAnCheckItIsSuccessful() throws Exception {
    final String validFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.VALID_REPORT_BED_OCCUPANCY_EXAMPLE);
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(validFileContent))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
        .andExpect(jsonPath("$..issue[?(@.severity == 'error')]").doesNotExist())
        .andExpect(jsonPath("$..issue[?(@.severity == 'fatal')]").doesNotExist());
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
  void passInvalidDv2MessageAndCheckMatchingProfileIssuesAreFiltered() throws Exception {
    final String validFileContent =
        FileTestUtil.readFileIntoString(ResourceFileConstants.INVALID_TEST_NOTIFICATION_DV_2);
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(validFileContent))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
        .andExpect(
            jsonPath(
                "$..issue[*].diagnostics",
                everyItem(not(startsWith("Unable to find a match for profile")))))
        .andExpect(
            jsonPath(
                "$..issue[*].diagnostics",
                everyItem(
                    not(
                        startsWith(
                            "Unable to find a match for the specified profile among choices")))))
        .andExpect(
            jsonPath(
                "$..issue[*].diagnostics",
                everyItem(
                    not(
                        startsWith(
                            "Multiple profiles found for entry resource. This is not supported at this time")))))
        .andExpect(
            jsonPath(
                "$..issue[*].diagnostics",
                everyItem(
                    not(
                        startsWith(
                            "This element does not match any known slice defined in the profile")))));
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
    final String xmlContent = "<test></test>";
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_XML_VALUE).content(xmlContent))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void passXmlAsJsonAnCheckItIsNotSupported() throws Exception {
    final String xmlContent = "<test></test>";
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(xmlContent))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
        .andExpect(jsonPath("$..issue[?(@.severity == 'fatal')]").exists());
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
