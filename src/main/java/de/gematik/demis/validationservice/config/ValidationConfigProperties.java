package de.gematik.demis.validationservice.config;

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
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Locale;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "demis.validation-service")
@Validated
@Builder
@Slf4j
public record ValidationConfigProperties(
    @Bean @NotNull @Valid ValidationConfigProperties.ProfilesProperties profiles,
    @NotNull Locale locale,
    @NotNull ResultSeverityEnum minSeverityOutcome,
    @Name("cache.expireAfterAccessMins") @Positive long cacheExpireAfterAccessTimeoutMins) {

  @PostConstruct
  void log() {
    log.info("Validation Configuration: {}", this);
  }

  public record ProfilesProperties(
      @NotEmpty String basepath, String fhirpath, @NotEmpty List<@NotEmpty String> versions) {}
}
