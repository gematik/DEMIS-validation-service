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

package de.gematik.demis.validationservice.services.validation.severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import org.junit.jupiter.api.Test;

class SeverityComparatorTest {

  @Test
  void compareErrorAndInformationAndCheckErrorIsGreater() {
    SeverityComparator comparator = new SeverityComparator();

    int result = comparator.compare(ResultSeverityEnum.INFORMATION, ResultSeverityEnum.ERROR);

    assertTrue(result < 0);
  }

  @Test
  void compareErrorAndWARNINGAndCheckErrorIsGreater() {
    SeverityComparator comparator = new SeverityComparator();

    int result = comparator.compare(ResultSeverityEnum.WARNING, ResultSeverityEnum.ERROR);

    assertTrue(result < 0);
  }

  @Test
  void compareErrorAndFatalAndCheckFatalIsGreater() {
    SeverityComparator comparator = new SeverityComparator();

    int result = comparator.compare(ResultSeverityEnum.FATAL, ResultSeverityEnum.ERROR);

    assertTrue(result > 0);
  }

  @Test
  void compareErrorAndErrorAndCheckItIsTheSame() {
    SeverityComparator comparator = new SeverityComparator();

    int result = comparator.compare(ResultSeverityEnum.ERROR, ResultSeverityEnum.ERROR);

    assertEquals(0, result);
  }
}
