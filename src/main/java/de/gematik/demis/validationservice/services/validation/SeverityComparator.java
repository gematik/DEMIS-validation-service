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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import ca.uhn.fhir.validation.ResultSeverityEnum;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

final class SeverityComparator implements Comparator<ResultSeverityEnum> {
  private static final Map<ResultSeverityEnum, Integer> SEVERITY_ORDER =
      Map.of(
          ResultSeverityEnum.INFORMATION, 0,
          ResultSeverityEnum.WARNING, 1,
          ResultSeverityEnum.ERROR, 2,
          ResultSeverityEnum.FATAL, 3);

  @Override
  public int compare(ResultSeverityEnum o1, ResultSeverityEnum o2) {
    if (Objects.isNull(o1)) {
      o1 = ResultSeverityEnum.INFORMATION;
    }
    if (Objects.isNull(o2)) {
      o2 = ResultSeverityEnum.INFORMATION;
    }
    Integer order1 = SEVERITY_ORDER.get(o1);
    Integer order2 = SEVERITY_ORDER.get(o2);

    return order1 - order2;
  }
}
