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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static de.gematik.demis.validationservice.util.ResourceFileConstants.TERMINOLOGY_PROFILES_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.support.IValidationSupport.IssueSeverity;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.gematik.demis.validationservice.config.ValidationConfigProperties;
import de.gematik.demis.validationservice.config.ValidationServiceConfiguration;
import de.gematik.demis.validationservice.services.ProfileParserService;
import de.gematik.demis.validationservice.services.validation.FhirValidatorFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = {
      FhirValidatorFactory.class,
      ValidationServiceConfiguration.class,
      ProfileParserService.class,
      TerminologyVersionsLoader.class,
      RemoteTerminologyProvider.class,
    },
    properties = {
      "demis.validation-service.terminology-server.enabled=true",
      "demis.validation-service.terminology-server.url=http://localhost:${wiremock.server.port}/fhir",
      "logging.level.ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor=TRACE",
      "logging.level.ca.uhn.fhir.log.terminology_troubleshooting=DEBUG"
    })
@ActiveProfiles("test")
@EnableConfigurationProperties({
  ValidationConfigProperties.class,
  TerminologyServerConfigProperties.class
})
@AutoConfigureWireMock(port = 0)
@Slf4j
class RemoteTerminologyValidationIntegrationTest {

  private static final String META_RESPONSE =
"""
{"resourceType":"CapabilityStatement","status":"active","kind":"instance","fhirVersion":"4.0.1","format":["json"],"rest":[{"mode":"server","resource":[{"type":"CodeSystem","profile":"http://hl7.org/fhir/StructureDefinition/CodeSystem","interaction":[{"code":"read"},{"code":"search-type"}],"operation":[{"name":"validate-code","definition":"http://hl7.org/fhir/OperationDefinition/CodeSystem-validate-code"},{"name":"lookup","definition":"http://hl7.org/fhir/OperationDefinition/CodeSystem-lookup"},{"name":"find-matches","definition":"http://hl7.org/fhir/OperationDefinition/CodeSystem-find-matches"}]},{"type":"ConceptMap","profile":"http://hl7.org/fhir/StructureDefinition/ConceptMap","interaction":[{"code":"read"},{"code":"search-type"}]},{"type":"ValueSet","profile":"http://hl7.org/fhir/StructureDefinition/ValueSet","interaction":[{"code":"read"},{"code":"search-type"}],"operation":[{"name":"expand","definition":"http://hl7.org/fhir/OperationDefinition/ValueSet-expand"},{"name":"validate-code","definition":"http://hl7.org/fhir/OperationDefinition/ValueSet-validate-code"}]}]}]}
""";

  private static final String NOT_FOUND_RESPONSE =
"""
{"resourceType":"Bundle","type":"searchset","total":0,"entry":[]}
""";

  private static final String VALIDATE_CODE_TRUE_RESPONSE =
"""
{"resourceType":"Parameters","parameter":[{"name":"result","valueBoolean":true}]}
""";
  private static final String VALIDATE_CODE_FALSE_RESPONSE =
"""
{"resourceType":"Parameters","parameter":[{"name":"result","valueBoolean":false}]}
""";

  @Autowired FhirValidatorFactory fhirValidatorFactory;

  private FhirValidator fhirValidator;

  private static void stubGetValueSet(
      final String url, final boolean summary, final boolean found) {
    stubGetResource("ValueSet", url, summary, found);
  }

  private static void stubGetCodeSystem(
      final String url, final boolean summary, final boolean found) {
    stubGetResource("CodeSystem", url, summary, found);
  }

  private static void stubGetResource(
      final String resource, final String url, final boolean summary, final boolean found) {
    final String getResource =
        "/fhir/"
            + resource
            + "?url="
            + URLEncoder.encode(url.replace("|", "\\|"), StandardCharsets.UTF_8)
            + (summary ? "" : "&_summary=false");

    final String response;
    if (found) {
      final String RESOURCE_FOUND_RESPONSE_PATTERN =
"""
            {"resourceType":"Bundle","type":"searchset","total":1,"entry":[{"resource":{"resourceType":"%s","id":"does-not-matter","url":"%s"}}]}
""";
      response = String.format(RESOURCE_FOUND_RESPONSE_PATTERN, resource, url.split("\\|")[0]);
    } else {
      response = NOT_FOUND_RESPONSE;
    }

    stubFor(get(getResource).willReturn(okJson(response)));
  }

  private static void stubValidateCodeVS(
      final String valueSetUrl, final String codesystem, final String code, final boolean valid) {
    final String validateCodeRequestPattern =
"""
{"resourceType":"Parameters","parameter":[{"name":"url","valueUri":"%s"},{"name":"code","valueString":"%s"},{"name":"system","valueUri":"%s"}]}
""";
    final String request = String.format(validateCodeRequestPattern, valueSetUrl, code, codesystem);
    stubFor(
        post("/fhir/ValueSet/$validate-code")
            .withRequestBody(equalToJson(request))
            .willReturn(
                okJson(valid ? VALIDATE_CODE_TRUE_RESPONSE : VALIDATE_CODE_FALSE_RESPONSE)));
  }

  private static void stubValidateCodeCS(
      final String codesystem,
      final String code,
      final boolean valid,
      final IssueSeverity severity,
      final String message) {
    final String validateCodeRequestPattern =
"""
{"resourceType":"Parameters","parameter":[{"name":"url","valueUri":"%s"},{"name":"code","valueString":"%s"}]}
""";
    final String request = String.format(validateCodeRequestPattern, codesystem, code);

    final String response = getParameterJsonString(valid, severity, message);

    stubFor(
        post("/fhir/CodeSystem/$validate-code")
            .withRequestBody(equalToJson(request))
            .willReturn(okJson(response)));
  }

  private static String getParameterJsonString(
      final boolean valid, final IssueSeverity severity, final String message) {
    final String paramPattern =
        """
            {"name":"%s","%s":"%s"}
            """;
    return String.format(
"""
{"resourceType":"Parameters","parameter":[%s%s%s]}
""",
        String.format(paramPattern, "result", "valueBoolean", valid),
        severity == null
            ? ""
            : ", " + String.format(paramPattern, "severity", "valueCode", severity),
        message == null
            ? ""
            : ", " + String.format(paramPattern, "message", "valueString", message));
  }

  private static Encounter createFhirResource() {
    final Encounter encounter = new Encounter();
    encounter
        .getMeta()
        .getProfile()
        .add(new CanonicalType("https://demis.rki.de/fhir/StructureDefinition/MyTest"));
    encounter.setId("JustForTest");
    return encounter;
  }

  @BeforeEach
  void setupValidator() {
    // These stubs are required for initialization
    stubFor(get("/fhir/metadata").willReturn(okJson(META_RESPONSE)));
    stubFor(get(urlPathMatching("/fhir/ValueSet")).willReturn(okJson(NOT_FOUND_RESPONSE)));
    stubFor(get(urlPathMatching("/fhir/CodeSystem")).willReturn(okJson(NOT_FOUND_RESPONSE)));

    fhirValidator = fhirValidatorFactory.createFhirValidator(TERMINOLOGY_PROFILES_PATH);

    assertThat(fhirValidator).isNotNull();

    WireMock.reset();
  }

  @Test
  void validCodes() {
    final var encounter = createFhirResource();

    // --- testcase: type=code
    encounter.setStatus(Encounter.EncounterStatus.FINISHED);

    // --- testcase: type=codeableConcept with one coding
    final Coding reasonCoding =
        new Coding(
            "https://demis.rki.de/fhir/CodeSystem/hospitalizationReason", "becauseOfDisease", null);
    encounter.addReasonCode().addCoding(reasonCoding);

    stubEncounterStatusValidationCalls();
    stubEncounterReasonValidationCalls(reasonCoding.getSystem(), reasonCoding.getCode(), false);

    final ValidationResult validationResult = fhirValidator.validateWithResult(encounter);

    assertThat(validationResult).isNotNull();
    assertThat(validationResult.getMessages()).isEmpty();
    assertThat(validationResult.isSuccessful()).isTrue();
  }

  @Test
  void cache() {
    // valid resource
    final Encounter encounter = createFhirResource();
    encounter.setStatus(Encounter.EncounterStatus.FINISHED);
    final Coding reasonCoding =
        new Coding(
            "https://demis.rki.de/fhir/CodeSystem/hospitalizationReason", "becauseOfDisease", null);
    encounter.addReasonCode().addCoding(reasonCoding);

    stubEncounterStatusValidationCalls();
    stubEncounterReasonValidationCalls(reasonCoding.getSystem(), reasonCoding.getCode(), false);

    fhirValidator.validateWithResult(encounter);

    // now we validate the same resource again
    // no remote call should be done, thus we clear all stubs
    WireMock.reset();

    log.info("Validate same resource again");
    final ValidationResult validationResult = fhirValidator.validateWithResult(encounter);
    assertThat(validationResult).isNotNull();
    assertThat(validationResult.getMessages()).isEmpty();
    assertThat(validationResult.isSuccessful()).isTrue();

    final List<LoggedRequest> unmatchedRequests = WireMock.findUnmatchedRequests();
    assertThat(unmatchedRequests).isEmpty();
  }

  @Test
  void unknownCodeButJustAWarning() {
    final var encounter = createFhirResource();

    final Coding reasonCoding =
        new Coding(
            "https://demis.rki.de/fhir/CodeSystem/hospitalizationReason",
            "notContainedInTheCodeSystemFragment",
            null);
    encounter.addReasonCode().addCoding(reasonCoding);

    stubEncounterReasonValidationCalls(reasonCoding.getSystem(), reasonCoding.getCode(), true);

    final ValidationResult validationResult = fhirValidator.validateWithResult(encounter);

    assertThat(validationResult).isNotNull();
    assertThat(validationResult.getMessages())
        .singleElement()
        .extracting(SingleValidationMessage::getMessage)
        .asString()
        .contains("Unknown code in fragment CodeSystem");
    assertThat(validationResult.isSuccessful()).isTrue();
  }

  @Test
  void invalidCode() {
    final Encounter encounter = createFhirResource();

    final Coding reasonCoding =
        new Coding("https://demis.rki.de/fhir/CodeSystem/hospitalizationReason", "xxx", null);
    encounter.addReasonCode().addCoding(reasonCoding);

    stubEncounterReasonValidationCallsForInvalidCode(
        reasonCoding.getSystem(), reasonCoding.getCode());

    final ValidationResult validationResult = fhirValidator.validateWithResult(encounter);
    assertThat(validationResult).isNotNull();
    assertThat(validationResult.getMessages())
        .singleElement()
        .extracting(SingleValidationMessage::getMessage)
        .asString()
        .contains("'https://demis.rki.de/fhir/CodeSystem/hospitalizationReason#xxx'");
    assertThat(validationResult.isSuccessful()).isFalse();
  }

  private void stubEncounterStatusValidationCalls() {
    // 1. Request: Exists ValueSet?
    stubGetValueSet("http://hl7.org/fhir/ValueSet/encounter-status", true, true);
    // 2. Request: Is Code Valid?
    stubValidateCodeVS(
        "http://hl7.org/fhir/ValueSet/encounter-status",
        "http://hl7.org/fhir/encounter-status",
        "finished",
        true);
  }

  private void stubEncounterReasonValidationCalls(
      final String system, final String code, boolean codeIsUnknownInSystem) {
    final String versionedSystem = system + "|2.3.0";
    final String valueSet = "https://demis.rki.de/fhir/ValueSet/answerSetHospitalizationReason";
    final String versionedValueSet = valueSet + "|2.3.5";
    // 1. request codesystem from coding
    stubGetCodeSystem(versionedSystem, true, true);
    // 2. request ValueSet from codeableConcept Binding (defined in fhir encounter resource)
    stubGetValueSet("http://hl7.org/fhir/ValueSet/encounter-reason", true, true);
    // 3. validate Code against ValueSet from CodeableConcept (-> false)
    stubValidateCodeVS(
        "http://hl7.org/fhir/ValueSet/encounter-reason", versionedSystem, code, false);
    // 4. request valueSet with coding system (-> not found)
    // Just for validation rule Terminology_TX_System_ValueSet2=Die Codierung bezieht sich auf ein
    // ValueSet, nicht auf ein Codesystem
    stubGetValueSet(system, false, false);
    // 5. validate code against coding system (-> true)
    if (codeIsUnknownInSystem) {
      stubValidateCodeCS(
          versionedSystem,
          code,
          false,
          IssueSeverity.WARNING,
          "Unknown code in fragment CodeSystem");
    } else {
      stubValidateCodeCS(versionedSystem, code, true, null, null);
    }
    // 6. get ValueSet of coding binding (2 requests: with and without summary)
    stubGetValueSet(versionedValueSet, false, true);
    stubGetValueSet(versionedValueSet, true, true);
    // 7. validate code against valueSet of coding binding
    stubValidateCodeVS(versionedValueSet, versionedSystem, code, true);
  }

  private void stubEncounterReasonValidationCallsForInvalidCode(
      final String system, final String code) {
    final String versionedSystem = system + "|2.3.0";
    // 1. request codesystem from coding
    stubGetCodeSystem(versionedSystem, true, true);
    // 2. request ValueSet from codeableConcept Binding (defined in fhir encounter resource)
    stubGetValueSet("http://hl7.org/fhir/ValueSet/encounter-reason", true, true);
    // 3. validate Code against ValueSet from CodeableConcept (-> false)
    stubValidateCodeVS(
        "http://hl7.org/fhir/ValueSet/encounter-reason", versionedSystem, code, false);
    // 4. request valueSet with coding system (-> not found)
    // Just for validation rule Terminology_TX_System_ValueSet2=Die Codierung bezieht sich auf ein
    // ValueSet, nicht auf ein Codesystem
    stubGetValueSet(system, false, false);
    // 5. validate code against coding system (-> true)
    stubValidateCodeCS(versionedSystem, code, false, null, null);
    // Note: Since the code is not contained in the system, there is no check against the ValueSet
  }
}
