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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import de.gematik.demis.validationservice.services.terminology.TerminologyValidationProvider;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport.CacheTimeouts;
import org.hl7.fhir.common.hapi.validation.support.RemoteTerminologyServiceValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@ConditionalOnTerminologyServerEnabled
class RemoteTerminologyProvider implements TerminologyValidationProvider {

  private static final Logger TERMINOLOGY_SERVER_NETWORK_LOGGER =
      LoggerFactory.getLogger("ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor");

  private final FhirContext fhirContext;
  private final TerminologyServerConfigProperties terminologyServerProps;
  private final TerminologyVersionsLoader terminologyVersionsLoader;

  @Override
  public void addTerminologyValidationSupport(
      final ValidationSupportChain chain,
      final CacheTimeouts cacheTimeouts,
      final Path profilesPath) {
    final var terminologyVersions = terminologyVersionsLoader.loadTerminologyVersions(profilesPath);
    final var remoteTerminologyValidationSupport =
        new RemoteTerminologyServiceVersionAwareValidationSupport(
            fhirContext, terminologyServerProps.url(), terminologyVersions);

    addLoggingInterceptor(remoteTerminologyValidationSupport);

    chain.addValidationSupport(
        new SpecialCachingValidationSupport(remoteTerminologyValidationSupport, cacheTimeouts));
  }

  private void addLoggingInterceptor(
      final RemoteTerminologyServiceValidationSupport remoteTerminologyValidationSupport) {
    if (TERMINOLOGY_SERVER_NETWORK_LOGGER.isDebugEnabled()) {
      final var loggingInterceptor =
          new LoggingInterceptor(TERMINOLOGY_SERVER_NETWORK_LOGGER.isTraceEnabled());
      loggingInterceptor.setLogRequestHeaders(false);
      loggingInterceptor.setLogResponseHeaders(false);
      remoteTerminologyValidationSupport.addClientInterceptor(loggingInterceptor);
    }
  }
}
