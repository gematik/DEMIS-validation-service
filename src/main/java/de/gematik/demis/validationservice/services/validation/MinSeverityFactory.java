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

import ca.uhn.fhir.validation.ResultSeverityEnum;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public final class MinSeverityFactory implements Supplier<ResultSeverityEnum> {

  private final String minSeverityOutcome;

  @Override
  public ResultSeverityEnum get() {
    var severity = SeverityParser.parse(this.minSeverityOutcome);
    if (severity == null) {
      final String errorMessage =
          "Configured minSeverityOutcome has an illegal value: %s"
              .formatted(this.minSeverityOutcome);
      log.error(errorMessage);
      throw new NoSuchElementException(
          errorMessage); // Shutdown service -> No silent config error treatment
    }
    log.info("Minimum severity of the outcome: %s".formatted(severity));
    return severity;
  }
}
