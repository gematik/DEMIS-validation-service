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

package de.gematik.demis.validationservice.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JsonValidatorTest {

  private static JsonValidator jsonValidator;

  @BeforeAll
  static void setupJsonValidator() {
    jsonValidator = new JsonValidator();
  }

  @Test
  void passValidJsonAndCheckItReturnsTrue() {
    String jsonAsString = "{ \"key\" : \"value\"}";

    boolean isValidJson = jsonValidator.isValidJson(jsonAsString);

    assertTrue(isValidJson);
  }

  @Test
  void passXmlJsonAndCheckItReturnsFalse() {
    String xmlAsString = "<test></test>";

    boolean isValidJson = jsonValidator.isValidJson(xmlAsString);

    assertFalse(isValidJson);
  }

  @Test
  void passInvalidJsonAndCheckItReturnsFalse() {
    String invalidJsonAsString = "{ \"key\" : \"value\"";

    boolean isValidJson = jsonValidator.isValidJson(invalidJsonAsString);

    assertFalse(isValidJson);
  }
}
