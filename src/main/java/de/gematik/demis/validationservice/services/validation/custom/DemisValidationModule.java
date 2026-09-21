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
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;

/// Abstract template for implementing custom DEMIS FHIR resource validators.
///
/// This class eliminates boilerplate by handling the common pattern of extracting a Bundle
/// from incoming resources, allowing subclasses to focus exclusively on Bundle-specific validation
/// logic.
///
/// DEMIS requires exactly one of the following input formats
/// - A FHIR [Bundle] resource (passed directly)
/// - A FHIR [Parameters] resource containing exactly one Bundle
///
/// This module enforces both constraints and extracts the Bundle for subclass validation.
///
/// ## What This Does
///
/// Subclasses receive a validated Bundle and implement only the domain-specific checks they need.
/// Parameter wrapping, Bundle extraction, and resource type validation are handled here.
///
/// ## Input Handling
///
/// - **Bundle:** Passed directly to [validateBundle(IValidationContext, Bundle)]
/// - **Parameters:** Must contain exactly one Bundle; that Bundle is extracted and passed
///   to [validateBundle(IValidationContext, Bundle)]
/// - **Other types:** Rejected with validation error
///
/// ## Usage Example
///
/// ```java
/// public class NewValidator extends DemisValidationModule {
///     @Override
///     protected void validateBundle(IValidationContext<IBaseResource> ctx, Bundle bundle) {
///         // Your custom Bundle validation logic here
///         // Bundle is guaranteed to exist and be well-formed
///     }
/// }```
///
/// ## Error Reporting
///
/// Validation errors at the envelope level (invalid resource type, malformed Parameters) are
/// automatically reported with ERROR severity. Subclasses should report their own findings
/// via the provided [IValidationContext](IValidationContext).
///
/// @see IValidatorModule
/// @see Bundle
/// @see Parameters
@Slf4j
public abstract class DemisValidationModule implements IValidatorModule {
  private final boolean strictSingleBundleValidation;

  protected DemisValidationModule(final boolean strictSingleBundleValidation) {
    this.strictSingleBundleValidation = strictSingleBundleValidation;
  }

  @Override
  public final void validateResource(IValidationContext<IBaseResource> theCtx) {
    if (strictSingleBundleValidation) {
      strictResourceValidation(theCtx);
    } else {
      relaxedResourceValidation(theCtx);
    }
  }

  private void relaxedResourceValidation(IValidationContext<IBaseResource> theCtx) {
    IBaseResource resource = theCtx.getResource();
    switch (resource) {
      case Parameters parameters -> relaxedValidateParameters(theCtx, parameters);
      case Bundle bundle -> validateBundle(theCtx, bundle);
      case null ->
          addValidationError(theCtx, "Invalid resource type, has to be Bundle or Parameters", "/");
      default ->
          log.warn(
              "Unsupported resource type received for validation: fhirType={}, javaType={}, id={}, profiles={}, validator={}",
              resource.fhirType(),
              resource.getClass().getSimpleName(),
              resource.getIdElement().getIdPart(),
              resource.getMeta().getProfile().stream()
                  .filter(Objects::nonNull)
                  .map(IPrimitiveType::getValueAsString)
                  .toList(),
              getClass().getSimpleName());
    }
  }

  private void relaxedValidateParameters(
      IValidationContext<IBaseResource> theCtx, Parameters parameters) {
    log.debug("Relaxed validation");
    parameters.getParameter().stream()
        .filter(entry -> entry.getResource() instanceof Bundle)
        .map(entry -> (Bundle) entry.getResource())
        .forEach(bundle -> validateBundle(theCtx, bundle));
  }

  private void strictResourceValidation(IValidationContext<IBaseResource> theCtx) {
    log.debug("Strict validation");
    IBaseResource resource = theCtx.getResource();
    switch (resource) {
      case Parameters parameters -> validateParameters(theCtx, parameters);
      case Bundle bundle -> validateBundle(theCtx, bundle);
      case null, default ->
          addValidationError(
              theCtx,
              "Invalid resource type, has to be Bundle or Parameters containing exactly one Bundle",
              "/");
    }
  }

  private void validateParameters(IValidationContext<IBaseResource> theCtx, Parameters parameters) {
    List<Parameters.ParametersParameterComponent> components = parameters.getParameter();
    if (components.isEmpty()) {
      addValidationError(
          theCtx, "Parameters resource is empty, it must contain exactly one Bundle", "/");
    } else if (components.size() > 1) {
      addValidationError(
          theCtx, "Parameter contains multiple bundles, it must contain exactly one Bundle", "/");
    } else {
      validateSingleParameterComponent(theCtx, components);
    }
  }

  private void validateSingleParameterComponent(
      IValidationContext<IBaseResource> theCtx,
      List<Parameters.ParametersParameterComponent> components) {
    Parameters.ParametersParameterComponent component = components.getFirst();
    if (component.hasResource()) {
      Resource resource = component.getResource();
      if (resource instanceof Bundle bundle) {
        validateBundle(theCtx, bundle);
      } else {
        addValidationError(theCtx, "Parameter resource is not a Bundle", "/");
      }
    } else {
      addValidationError(
          theCtx,
          "Parameter resource is missing, it must contain exactly one Bundle or be a Bundle",
          "/");
    }
  }

  private void addValidationMessage(
      final IValidationContext<IBaseResource> validationContext,
      final ResultSeverityEnum severity,
      final String message,
      final String location) {
    final SingleValidationMessage validationMessage = new SingleValidationMessage();
    validationMessage.setSeverity(severity);
    validationMessage.setMessage(message);
    validationContext.addValidationMessage(validationMessage);
    validationMessage.setLocationString(location);
  }

  protected final void addValidationError(
      final IValidationContext<IBaseResource> validationContext,
      final String message,
      final String location) {
    addValidationMessage(validationContext, ResultSeverityEnum.ERROR, message, location);
  }

  protected final void addValidationWarning(
      final IValidationContext<IBaseResource> validationContext,
      final String message,
      final String location) {
    addValidationMessage(validationContext, ResultSeverityEnum.WARNING, message, location);
  }

  protected final void addValidationInfo(
      final IValidationContext<IBaseResource> validationContext,
      final String message,
      final String location) {
    addValidationMessage(validationContext, ResultSeverityEnum.INFORMATION, message, location);
  }

  /// Implements DEMIS-specific validation logic for the extracted Bundle.
  ///
  /// Subclasses override this method to apply their custom validation rules.
  /// The [Bundle] is guaranteed to exist and be well-formed at this point.
  ///
  /// @param theCtx the HAPI validation context for reporting validation messages
  /// @param bundle the [Bundle] to validate; guaranteed non-null and extracted from the input
  /// resource
  protected abstract void validateBundle(IValidationContext<IBaseResource> theCtx, Bundle bundle);
}
