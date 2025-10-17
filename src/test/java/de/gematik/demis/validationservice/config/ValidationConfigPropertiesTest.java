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

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.validation.ResultSeverityEnum;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@Slf4j
class ValidationConfigPropertiesTest {

  private static final String[] VALID_PROPERTIES = {
    "demis.validation-service.profiles.basepath=myprofiles",
    "demis.validation-service.profiles.fhirpath=Fhir",
    "demis.validation-service.profiles.versions=6.1.7,5.3.5",
    "demis.validation-service.locale=en_US",
    "demis.validation-service.minSeverityOutcome=error",
    "demis.validation-service.cache.expireAfterAccessMins=120"
  };

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(ValidationConfigPropertiesTestConfiguration.class);

  @Test
  void validProperties() {
    contextRunner
        .withPropertyValues(VALID_PROPERTIES)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              final ValidationConfigProperties properties =
                  context.getBean(ValidationConfigProperties.class);

              assertThat(properties).isNotNull();
              assertThat(properties.minSeverityOutcome()).isEqualTo(ResultSeverityEnum.ERROR);
              assertThat(properties.cacheExpireAfterAccessTimeoutMins()).isEqualTo(120);
              assertThat(properties.locale()).isEqualTo(Locale.US);
              final ValidationConfigProperties.ProfilesProperties profilesProperties =
                  properties.profiles();
              assertThat(profilesProperties)
                  .isNotNull()
                  .isSameAs(context.getBean(ValidationConfigProperties.ProfilesProperties.class));
              assertThat(profilesProperties.basepath()).isEqualTo("myprofiles");
              assertThat(profilesProperties.fhirpath()).isEqualTo("Fhir");
              assertThat(profilesProperties.versions()).isEqualTo(List.of("6.1.7", "5.3.5"));
            });
  }

  @ValueSource(
      strings = {
        "demis.validation-service.profiles.basepath=",
        "demis.validation-service.profiles.versions=",
        "demis.validation-service.profiles.versions=,,",
        "demis.validation-service.locale=",
        "demis.validation-service.minSeverityOutcome=",
        "demis.validation-service.minSeverityOutcome=errors",
        "demis.validation-service.cache.expireAfterAccessMins=",
        "demis.validation-service.cache.expireAfterAccessMins=0"
      })
  @ParameterizedTest
  void invalidProperties(final String invalidProperty) {
    contextRunner
        .withPropertyValues(VALID_PROPERTIES)
        .withPropertyValues(invalidProperty)
        .run(
            context -> {
              assertThat(context)
                  .hasFailed()
                  .getFailure()
                  .isInstanceOf(ConfigurationPropertiesBindException.class);
            });
  }

  @TestConfiguration
  @EnableConfigurationProperties(ValidationConfigProperties.class)
  static class ValidationConfigPropertiesTestConfiguration {}
}
