package de.gematik.demis.validationservice.services;

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

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationMetrics {

  private static final String COUNTER_VALIDATION = "DEMIS_COUNTER_VALIDATION";

  private static final String TAG_VERSION = "profiles_version";
  private static final String TAG_SUCCESS = "success";

  private final MeterRegistry meterRegistry;

  @PostConstruct
  public void log() {
    log.info("Metrics: {}", COUNTER_VALIDATION);
  }

  public void incValidationCount(final String version, final boolean success) {
    try {
      meterRegistry
          .counter(COUNTER_VALIDATION, TAG_VERSION, version, TAG_SUCCESS, String.valueOf(success))
          .increment();
    } catch (final RuntimeException e) {
      log.error("error incrementing counter", e);
    }
  }
}
