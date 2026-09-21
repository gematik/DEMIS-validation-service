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

import ch.qos.logback.classic.Level;
import de.gematik.demis.validationservice.ValidationServiceApplication;
import de.gematik.demis.validationservice.services.validation.custom.DemisValidationModule;
import de.gematik.demis.validationservice.test.LogListener;
import de.gematik.demis.validationservice.util.FileTestUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(
    classes = {ValidationServiceApplication.class},
    properties = {
      "feature.flag.vs.reference.validation=true",
      "feature.flag.strict.single.bundle.validation=false",
      "demis.validation-service.profiles.basepath=src/test/resources/integrationtests/customValidators/strictCodeValidation/fixedBinding",
      "demis.validation-service.profiles.versions=6.1.8"
    })
class ResourceReferencesValidationRelaxedIntegrationTest {

  private static final String VALID_PATIENT =
      "src/test/resources/integrationtests/customValidators/input/customValidatorInput_PatientOnly.json";

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldSkipNonBundleResourceInRelaxedMode() throws Exception {
    var appender = LogListener.listenToLog(DemisValidationModule.class.getName());
    String patient = FileTestUtil.readFileIntoString(VALID_PATIENT);
    mockMvc
        .perform(post("/$validate").contentType(APPLICATION_JSON_VALUE).content(patient))
        .andExpect(status().isOk());
    assertThat(appender.list)
        .anySatisfy(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.WARN);
              assertThat(e.getFormattedMessage())
                  .contains("Patient")
                  .contains("https://demis.rki.de/fhir/StructureDefinition/NotifiedPerson")
                  .doesNotContain("Müller")
                  .doesNotContain("test@test.de");
            });
  }
}
