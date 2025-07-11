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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ca.uhn.fhir.validation.FhirValidator;
import de.gematik.demis.validationservice.config.ValidationConfigProperties;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest(
    classes = {
      FhirValidatorManagerTest.FhirValidatorManagerTestContextConfiguration.class,
      FhirValidatorManager.class
    })
class FhirValidatorManagerTest {

  private static final String BASE_PATH = "myprofiles";
  private static final String FHIR_PATH = "fhir";
  private static final List<String> VERSIONS = List.of("6.1.0", "5.3.1");

  @Autowired private FhirValidatorManager underTest;

  @Autowired private FhirValidatorFactory mockFhirValidatorFactory;

  @Test
  void initializeWasSuccessful() {
    VERSIONS.forEach(
        version -> {
          final var profilePath = Path.of(BASE_PATH, version, FHIR_PATH);
          verify(mockFhirValidatorFactory, times(1)).createFhirValidator(profilePath);
        });
    Mockito.verifyNoMoreInteractions(mockFhirValidatorFactory);
  }

  @Test
  void getVersions() {
    final List<String> versions = underTest.getVersions();
    assertThat(versions).isEqualTo(VERSIONS);
  }

  @Test
  void getValidator() {
    VERSIONS.forEach(
        version -> {
          final Optional<FhirValidator> fhirValidator = underTest.getFhirValidator(version);
          assertThat(fhirValidator).isPresent();
        });
  }

  @Test
  void getValidatorForUnknownVersion() {
    final Optional<FhirValidator> result = underTest.getFhirValidator("unknown-version");
    assertThat(result).isEmpty();
  }

  @TestConfiguration
  static class FhirValidatorManagerTestContextConfiguration {
    @Bean
    public FhirValidatorFactory mockFhirValidatorFactory() {
      final FhirValidatorFactory mock = Mockito.mock(FhirValidatorFactory.class);
      Mockito.when(mock.createFhirValidator(Mockito.any()))
          .thenReturn(Mockito.mock(FhirValidator.class));
      return mock;
    }

    @Bean
    public ValidationConfigProperties.ProfilesProperties profilesProperties() {
      return new ValidationConfigProperties.ProfilesProperties(BASE_PATH, FHIR_PATH, VERSIONS);
    }
  }
}
