package de.gematik.demis.validationservice.services.validation;

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
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import org.junit.jupiter.api.Test;

class SeverityParserTest {

  @Test
  void testPassingNullAndCheckItIsInformationEnum() {
    ResultSeverityEnum severity = SeverityParser.parse(null);

    assertEquals(ResultSeverityEnum.INFORMATION, severity);
  }

  @Test
  void testPassingBlankAndCheckItIsInformationEnum() {
    ResultSeverityEnum severity = SeverityParser.parse(" ");

    assertEquals(ResultSeverityEnum.INFORMATION, severity);
  }

  @Test
  void testPassingErrorAndCheckItIsErrorEnum() {
    ResultSeverityEnum severity = SeverityParser.parse("error");

    assertEquals(ResultSeverityEnum.ERROR, severity);
  }

  @Test
  void testPassingWarningAndCheckItIsWarningEnum() {
    ResultSeverityEnum severity = SeverityParser.parse("warning");

    assertEquals(ResultSeverityEnum.WARNING, severity);
  }

  @Test
  void testPassingFatalAndCheckItIsFatalEnum() {
    ResultSeverityEnum severity = SeverityParser.parse("fatal");

    assertEquals(ResultSeverityEnum.FATAL, severity);
  }
}
