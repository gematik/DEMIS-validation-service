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

import static java.util.Collections.unmodifiableList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import ca.uhn.fhir.validation.FhirValidator;
import de.gematik.demis.validationservice.config.ValidationConfigProperties;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FhirValidatorManager {
  private final FhirValidatorFactory fhirValidatorFactory;
  private final ValidationConfigProperties.ProfilesProperties profilesProperties;

  @Getter private List<String> versions;
  private Map<String, FhirValidator> versionValidatorMap;

  @PostConstruct
  void createValidators() {
    versions = unmodifiableList(profilesProperties.versions());

    log.info(
        "Validation service profiles paths: base: {}, fhir: {}",
        profilesProperties.basepath(),
        profilesProperties.fhirpath());
    log.info("Validation service profiles versions: {}", versions);

    versionValidatorMap = versions.stream().collect(toMap(identity(), this::createFhirValidator));
  }

  public Optional<FhirValidator> getFhirValidator(final String version) {
    return Optional.ofNullable(versionValidatorMap.get(version));
  }

  private FhirValidator createFhirValidator(final String version) {
    return fhirValidatorFactory.createFhirValidator(getProfilePathForVersion(version));
  }

  private Path getProfilePathForVersion(final String version) {
    return Path.of(profilesProperties.basepath(), version, profilesProperties.fhirpath());
  }
}
