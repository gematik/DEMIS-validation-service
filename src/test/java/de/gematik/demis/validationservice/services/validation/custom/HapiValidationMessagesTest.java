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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HapiValidationMessagesTest {

  private static final String RESOURCE_TYPE = "Bundle";
  private static final String ACTUAL_ROOT = "Bundle.entry[0].resource";
  private static final String LOCATION = "Bundle.id";

  @Mock private IValidationContext<IBaseResource> validationContext;
  @Captor private ArgumentCaptor<SingleValidationMessage> messageCaptor;

  @Test
  void addErrorAddsAnErrorMessageAtTheActualLocation() {
    HapiValidationMessages messages =
        new HapiValidationMessages(validationContext, RESOURCE_TYPE, ACTUAL_ROOT);

    messages.addError("Invalid bundle", LOCATION);

    SingleValidationMessage message = capturedMessage();
    assertEquals(ResultSeverityEnum.ERROR, message.getSeverity());
    assertEquals("Invalid bundle", message.getMessage());
    assertEquals(ACTUAL_ROOT + ".id", message.getLocationString());
  }

  @Test
  void addWarningAddsAWarningMessageAtTheActualLocation() {
    HapiValidationMessages messages =
        new HapiValidationMessages(validationContext, RESOURCE_TYPE, ACTUAL_ROOT);

    messages.addWarning("Deprecated element", LOCATION);

    SingleValidationMessage message = capturedMessage();
    assertEquals(ResultSeverityEnum.WARNING, message.getSeverity());
    assertEquals("Deprecated element", message.getMessage());
    assertEquals(ACTUAL_ROOT + ".id", message.getLocationString());
  }

  @Test
  void addInfoAddsAnInformationMessageAtTheActualLocation() {
    HapiValidationMessages messages =
        new HapiValidationMessages(validationContext, RESOURCE_TYPE, ACTUAL_ROOT);

    messages.addInfo("Informational message", LOCATION);

    SingleValidationMessage message = capturedMessage();
    assertEquals(ResultSeverityEnum.INFORMATION, message.getSeverity());
    assertEquals("Informational message", message.getMessage());
    assertEquals(ACTUAL_ROOT + ".id", message.getLocationString());
  }

  private SingleValidationMessage capturedMessage() {
    verify(validationContext).addValidationMessage(messageCaptor.capture());
    return messageCaptor.getValue();
  }
}
