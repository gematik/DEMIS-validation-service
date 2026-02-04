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

import de.gematik.demis.validationservice.ValidationServiceApplication;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {ValidationServiceApplication.class},
    properties = {
      "feature.flag.validation.extension.check.enabled=true",
      "feature.flag.deny.modifier.extensions=false",
      "demis.validation-service.profiles.basepath=src/test/resources/integrationtests/customValidators/quantity",
      "demis.validation-service.profiles.versions=6.1.2"
    })
class ExtensionValidatorUrlCheckIntegrationTest extends ExtensionValidatorIntegrationTest {
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
}
