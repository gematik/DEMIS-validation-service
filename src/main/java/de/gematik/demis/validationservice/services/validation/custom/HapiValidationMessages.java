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

import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;

@RequiredArgsConstructor
final class HapiValidationMessages implements ValidationMessages {

  private final IValidationContext<IBaseResource> validationContext;
  private final String resourceType;
  private final String actualRoot;

  @Override
  public void addError(String message, String location) {
    addMessage(ResultSeverityEnum.ERROR, message, location);
  }

  @Override
  public void addWarning(String message, String location) {
    addMessage(ResultSeverityEnum.WARNING, message, location);
  }

  @Override
  public void addInfo(String message, String location) {
    addMessage(ResultSeverityEnum.INFORMATION, message, location);
  }

  private void addMessage(
      final ResultSeverityEnum severity, final String message, final String location) {
    final SingleValidationMessage validationMessage = new SingleValidationMessage();
    validationMessage.setSeverity(severity);
    validationMessage.setMessage(message);
    validationMessage.setLocationString(toActualLocation(location));
    validationContext.addValidationMessage(validationMessage);
  }

  private String toActualLocation(final String location) {
    return actualRoot + location.substring(resourceType.length());
  }
}
