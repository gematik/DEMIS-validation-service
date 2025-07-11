package de.gematik.demis.validationservice.services.terminology.remote;

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

import static org.apache.commons.lang3.StringUtils.isBlank;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.ParametersUtil;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.common.hapi.validation.support.RemoteTerminologyServiceValidationSupport;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;

/** Supports severity parameter in validateCode operation result */
class CustomizedRemoteTerminologyServiceValidationSupport
    extends RemoteTerminologyServiceValidationSupport {

  private final Method provideClientMethod;

  @SuppressWarnings("java:S3011") // HACK: We have to call the private method
  public CustomizedRemoteTerminologyServiceValidationSupport(
      final FhirContext theFhirContext, final String theBaseUrl) {
    super(theFhirContext, theBaseUrl);

    // HACK
    try {
      provideClientMethod =
          RemoteTerminologyServiceValidationSupport.class.getDeclaredMethod("provideClient");
      provideClientMethod.setAccessible(true);
    } catch (final NoSuchMethodException e) {
      throw new IllegalStateException(
          "RemoteTerminologyServiceValidationSupport has changed. Private Method provideClient does not exist anymore. You have to adapt this hack implementation",
          e);
    }
  }

  // Note: copy paste from
  // org.hl7.fhir.common.hapi.validation.support.RemoteTerminologyServiceValidationSupport.invokeRemoteValidateCode
  @Override
  protected CodeValidationResult invokeRemoteValidateCode(
      final String theCodeSystem,
      final String theCode,
      final String theDisplay,
      final String theValueSetUrl,
      final IBaseResource theValueSet) {
    if (isBlank(theCode)) {
      return null;
    }

    IGenericClient client = callProvideClientFromSuperClass();

    IBaseParameters input =
        buildValidateCodeInputParameters(
            theCodeSystem, theCode, theDisplay, theValueSetUrl, theValueSet);

    String resourceType = "ValueSet";
    if (theValueSet == null && theValueSetUrl == null) {
      resourceType = "CodeSystem";
    }

    IBaseParameters output =
        client
            .operation()
            .onType(resourceType)
            .named("validate-code")
            .withParameters(input)
            .execute();

    List<String> resultValues =
        ParametersUtil.getNamedParameterValuesAsString(getFhirContext(), output, "result");
    if (resultValues.isEmpty() || isBlank(resultValues.get(0))) {
      return null;
    }
    Validate.isTrue(
        resultValues.size() == 1, "Response contained %d 'result' values", resultValues.size());

    boolean success = "true".equalsIgnoreCase(resultValues.get(0));

    CodeValidationResult retVal = new CodeValidationResult();
    if (success) {
      retVal.setCode(theCode);
      List<String> displayValues =
          ParametersUtil.getNamedParameterValuesAsString(getFhirContext(), output, "display");
      if (!displayValues.isEmpty()) {
        retVal.setDisplay(displayValues.get(0));
      }

    } else {
      // ------
      // This part differs from original. We take here the severity from terminology server
      // if not provided then the default is ERROR
      final IssueSeverity severity =
          ParametersUtil.getNamedParameterValueAsString(getFhirContext(), output, "severity")
              .map(code -> IssueSeverity.valueOf(code.toUpperCase()))
              .orElse(IssueSeverity.ERROR);
      // ------
      retVal.setSeverity(severity);
      List<String> messageValues =
          ParametersUtil.getNamedParameterValuesAsString(getFhirContext(), output, "message");
      if (!messageValues.isEmpty()) {
        retVal.setMessage(messageValues.get(0));
      }
    }
    return retVal;
  }

  private IGenericClient callProvideClientFromSuperClass() {
    try {
      return (IGenericClient) provideClientMethod.invoke(this);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("error while calling provideClient", e);
    }
  }
}
