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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.*;

import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.SingleValidationMessage;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParametersValidationModuleTest {
  private TestModule module;
  @Mock private IValidationContext<IBaseResource> context;

  @BeforeEach
  void setUp() {
    module = new TestModule();
  }

  @Test
  void shouldValidateDirectResource() {
    final Patient patient = patient("patient-1");
    when(context.getResource()).thenReturn(patient);

    module.validateResource(context);

    assertThat(module.validatedResources).containsExactly(patient);
    assertThat(module.validationMessages).hasSize(1);
  }

  @Test
  void shouldValidateEachResourceContainedInParameters() {
    final Patient firstPatient = patient("patient-1");
    final Patient secondPatient = patient("patient-2");

    final Parameters parameters = new Parameters();
    parameters.addParameter().setResource(firstPatient);
    parameters.addParameter().setResource(secondPatient);

    module = new TestModule();

    when(context.getResource()).thenReturn(parameters);

    module.validateResource(context);

    assertThat(module.validatedResources).containsExactly(firstPatient, secondPatient);
    assertThat(module.validationMessages).hasSize(2);
  }

  @Test
  void shouldIgnoreParametersWithoutResource() {
    final Parameters parameters = new Parameters();

    parameters.addParameter().setName("value").setValue(new StringType("ignored"));

    parameters.addParameter().setName("parts").addPart().setValue(new StringType("also ignored"));

    when(context.getResource()).thenReturn(parameters);
    module.validateResource(context);

    assertThat(module.validatedResources).isEmpty();
    assertThat(module.validationMessages).isEmpty();
  }

  @Test
  void shouldMapMessageLocationForDirectResource() {
    final Patient patient = patient("patient-1");
    when(context.getResource()).thenReturn(patient);

    module.validateResource(context);

    module.validationMessages.getFirst().addError("Invalid patient", "Patient.name");

    verify(context)
        .addValidationMessage(
            argThat(
                message ->
                    message instanceof SingleValidationMessage singleMessage
                        && "Invalid patient".equals(singleMessage.getMessage())
                        && "Patient.name".equals(singleMessage.getLocationString())));
  }

  private Patient patient(final String id) {
    final Patient patient = new Patient();
    patient.setId(id);
    return patient;
  }

  private final class TestModule extends ParametersValidationModule {
    private final List<IBaseResource> validatedResources = new ArrayList<>();
    private final List<ValidationMessages> validationMessages = new ArrayList<>();

    @Override
    protected void validateIndividualResource(IBaseResource resource, ValidationMessages messages) {
      validatedResources.add(resource);
      validationMessages.add(messages);
    }
  }
}
