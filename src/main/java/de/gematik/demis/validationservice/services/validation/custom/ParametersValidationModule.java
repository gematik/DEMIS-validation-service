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
import ca.uhn.fhir.validation.IValidatorModule;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;

/// Base class for custom validators that can process resources passed either directly or wrapped
/// in a FHIR [Parameters] resource.
///
/// When the validation input is a [Parameters] resource, each directly contained
/// `Parameters.parameter.resource` is passed individually to
/// [#validateIndividualResource(IBaseResource, ValidationMessages)]. Parameters without a direct
/// resource, including parameters containing only `value[x]` or `part`, are ignored.
///
/// Any input other than [Parameters] is passed to the validator unchanged. This class does not
/// decide whether a resource type is supported or should be validated; that responsibility remains
/// with the concrete validator.
///
/// A dedicated [ValidationMessages] instance is created for every resource. It maps
/// resource-local message locations to their location in the original validation input. This means
/// that messages for resources wrapped in [Parameters] retain the full path through the containing
/// parameter.
///
/// @see ValidationMessages
/// @see IValidatorModule
public abstract class ParametersValidationModule implements IValidatorModule {

  @Override
  public void validateResource(IValidationContext<IBaseResource> theCtx) {
    final IBaseResource resource = theCtx.getResource();

    if (resource instanceof Parameters parameters) {
      validateParameters(theCtx, parameters);
    } else {
      final ValidationMessages messages =
          new HapiValidationMessages(theCtx, resource.fhirType(), resource.fhirType());
      validateIndividualResource(resource, messages);
    }
  }

  /// Applies the custom validation rules to one individual resource.
  ///
  /// Implementations decide whether the supplied resource type is relevant. Unsupported resource
  /// types should be left unchanged by returning without adding a validation message.
  ///
  /// Message locations must be expressed relative to the supplied resource and start with its FHIR
  /// type. For example, a validator receiving a [Bundle] reports
  /// `Bundle.entry[0].resource`. The provided [ValidationMessages] maps this local location to the
  /// corresponding location in the original input, such as
  /// `Parameters.parameter[1].resource/*Bundle/example*/.entry[0].resource`.
  ///
  /// @param resource the resource to inspect. Either the original validation input or a resource
  ///     directly contained in `Parameters.parameter.resource`
  /// @param messages destination for errors, warnings, and informational validation messages
  protected abstract void validateIndividualResource(
      IBaseResource resource, ValidationMessages messages);

  private void validateParameters(IValidationContext<IBaseResource> theCtx, Parameters parameters) {
    final List<Parameters.ParametersParameterComponent> components = parameters.getParameter();

    for (int index = 0; index < components.size(); index++) {
      final Resource resource = components.get(index).getResource();

      if (resource == null) {
        continue;
      }

      final String actualRoot =
          "Parameters.parameter[%d].resource/*%s/%s*/"
              .formatted(index, resource.fhirType(), resource.getIdPart());

      final ValidationMessages messages =
          new HapiValidationMessages(theCtx, resource.fhirType(), actualRoot);
      validateIndividualResource(resource, messages);
    }
  }
}
