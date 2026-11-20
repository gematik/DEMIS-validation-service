package de.gematik.demis.validationservice.services.validation.custom;

/*-
 * #%L
 * validation-service
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public final class TestValidationMessages implements ValidationMessages {

  private final List<ValidationMessage> errors = new ArrayList<>();
  private final List<ValidationMessage> warnings = new ArrayList<>();
  private final List<ValidationMessage> infos = new ArrayList<>();

  @Override
  public void addError(String message, String location) {
    this.errors.add(new ValidationMessage(message, location));
  }

  @Override
  public void addWarning(String message, String location) {
    this.warnings.add(new ValidationMessage(message, location));
  }

  @Override
  public void addInfo(String message, String location) {
    this.infos.add(new ValidationMessage(message, location));
  }

  public void assertNoMessages() {
    assertThat(errors).as("validation errors").isEmpty();
    assertThat(warnings).as("validation warnings").isEmpty();
    assertThat(infos).as("validation infos").isEmpty();
  }

  public void assertSingleError(String message, String location) {
    assertThat(errors)
        .as("validation errors")
        .containsExactly(new ValidationMessage(message, location));
    assertThat(warnings).as("validation warnings").isEmpty();
    assertThat(infos).as("validation infos").isEmpty();
  }

  public record ValidationMessage(String message, String location) {}
}
