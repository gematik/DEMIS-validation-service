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

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static java.lang.Boolean.parseBoolean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport.CodeValidationResult;
import ca.uhn.fhir.context.support.IValidationSupport.IssueSeverity;
import de.gematik.demis.validationservice.config.ValidationConfigProperties;
import de.gematik.demis.validationservice.config.ValidationServiceConfiguration;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = {
      ValidationServiceConfiguration.class,
      RemoteTerminologyProvider.class,
    },
    properties = {
      "demis.validation-service.terminology-server.enabled=true",
      "demis.validation-service.terminology-server.url=http://localhost:${wiremock.server.port}/fhir",
      "logging.level.ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor=TRACE"
    })
@ActiveProfiles("test")
@AutoConfigureWireMock(port = 0)
@EnableConfigurationProperties({
  ValidationConfigProperties.class,
  TerminologyServerConfigProperties.class
})
@Slf4j
class RemoteTerminologyServerIntegrationTest {

  private static final String META_RESPONSE =
"""
{"resourceType":"CapabilityStatement"}
""";

  @MockitoBean private TerminologyVersionsLoader terminologyVersionsLoader;
  @Autowired private RemoteTerminologyProvider remoteTerminologyProvider;

  private IValidationSupport underTest;

  private final ValidationSupportChain validationSupportChain =
      new ValidationSupportChain() {
        @Override
        public void addValidationSupport(final IValidationSupport theValidationSupport) {
          super.addValidationSupport(theValidationSupport);
          underTest = theValidationSupport;
        }
      };

  private static void stubValidateCodeCS(
      final String codesystem, final String code, final String response) {
    final String validateCodeRequestPattern =
"""
{"resourceType":"Parameters","parameter":[{"name":"url","valueUri":"%s"},{"name":"code","valueString":"%s"}]}
""";
    final String request = String.format(validateCodeRequestPattern, codesystem, code);
    stubFor(
        post("/fhir/CodeSystem/$validate-code")
            .withRequestBody(equalToJson(request))
            .willReturn(okJson(response)));
  }

  @BeforeEach
  void setUp() {
    stubFor(get("/fhir/metadata").willReturn(okJson(META_RESPONSE)));
    final Path path = Path.of("justATest");
    final TerminologyVersions versions = Mockito.mock(TerminologyVersions.class);
    when(terminologyVersionsLoader.loadTerminologyVersions(path)).thenReturn(versions);
    remoteTerminologyProvider.addTerminologyValidationSupport(
        validationSupportChain, CachingValidationSupport.CacheTimeouts.defaultValues(), path);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = ';',
      nullValues = "null",
      textBlock =
"""
{"resourceType":"Parameters","parameter":[{"name":"result","valueBoolean":false},{"name":"message","valueString":"Unknown code in fragment CodeSystem"},{"name":"severity","valueCode":"warning"}]};false;WARNING;Unknown code in fragment CodeSystem
{"resourceType":"Parameters","parameter":[{"name":"result","valueBoolean":false},{"name":"message","valueString":"Big Error"},{"name":"severity","valueCode":"error"}]};false;ERROR;Big Error
{"resourceType":"Parameters","parameter":[{"name":"result","valueBoolean":false}]};false;ERROR;null
{"resourceType":"Parameters","parameter":[{"name":"result","valueBoolean":true}]};true;null;null
""")
  void validateCode(
      final String tsResponse,
      final String expectedOk,
      final String expectedSeverity,
      final String expectedMessage) {
    final ConceptValidationOptions conceptValidationOptions = new ConceptValidationOptions();
    final String codeSystem = "http://example.com/codeSystem";
    final String code = "myCode";

    stubValidateCodeCS(codeSystem, code, tsResponse);

    final CodeValidationResult result =
        underTest.validateCode(null, conceptValidationOptions, codeSystem, code, null, null);

    assertThat(result).isNotNull();
    assertThat(result.isOk()).isEqualTo(parseBoolean(expectedOk));
    assertThat(result.getSeverity())
        .isEqualTo(expectedSeverity == null ? null : IssueSeverity.valueOf(expectedSeverity));
    assertThat(result.getMessage()).isEqualTo(expectedMessage);
  }
}
