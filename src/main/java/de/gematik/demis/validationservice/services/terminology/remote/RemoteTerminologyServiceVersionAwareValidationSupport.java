package de.gematik.demis.validationservice.services.terminology.remote;

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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;

@Slf4j
class RemoteTerminologyServiceVersionAwareValidationSupport
    extends CustomizedRemoteTerminologyServiceValidationSupport {

  private final TerminologyVersions terminologyVersions;

  public RemoteTerminologyServiceVersionAwareValidationSupport(
      final FhirContext theFhirContext,
      final String theBaseUrl,
      final TerminologyVersions terminologyVersions) {
    super(theFhirContext, theBaseUrl);
    this.terminologyVersions = terminologyVersions;
  }

  @Override
  public IBaseResource fetchCodeSystem(final String theSystem) {
    return super.fetchCodeSystem(appendCodesystemVersion(theSystem));
  }

  @Override
  public IBaseResource fetchValueSet(final String theValueSetUrl) {
    return super.fetchValueSet(appendValueSetVersion(theValueSetUrl));
  }

  @Override
  protected IBaseParameters buildValidateCodeInputParameters(
      final String theCodeSystem,
      final String theCode,
      final String theDisplay,
      final String theValueSetUrl,
      final IBaseResource theValueSet) {
    return super.buildValidateCodeInputParameters(
        appendCodesystemVersion(theCodeSystem),
        theCode,
        theDisplay,
        appendValueSetVersion(theValueSetUrl),
        theValueSet);
  }

  private String appendCodesystemVersion(final String codeSystemUrl) {
    return appendVersionIfRequired(codeSystemUrl, terminologyVersions::getCodeSystemVersion);
  }

  private String appendValueSetVersion(final String valueSetUrl) {
    return appendVersionIfRequired(valueSetUrl, terminologyVersions::getValueSetVersion);
  }

  private String appendVersionIfRequired(
      final String url, final UnaryOperator<String> versionGetter) {
    if (url != null && !url.contains("|")) {
      final String version = versionGetter.apply(url);
      if (version != null) {
        return url + "|" + version;
      }
    }
    return url;
  }

  @Override
  public boolean isCodeSystemSupported(
      ValidationSupportContext theValidationSupportContext, String url) {
    return super.isCodeSystemSupported(theValidationSupportContext, appendCodesystemVersion(url));
  }

  @Override
  public boolean isValueSetSupported(
      ValidationSupportContext theValidationSupportContext, String url) {
    return super.isValueSetSupported(theValidationSupportContext, appendValueSetVersion(url));
  }
}
