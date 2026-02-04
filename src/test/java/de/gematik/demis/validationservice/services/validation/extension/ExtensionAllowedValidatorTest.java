package de.gematik.demis.validationservice.services.validation.extension;

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

import static de.gematik.demis.validationservice.test.LogListener.listenToLog;
import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.demis.validationservice.services.validation.extension.ResourceWalker.ElementCtx;
import de.gematik.demis.validationservice.services.validation.extension.StructureDefinitionExtensionExtractor.AllowedExtensionUrls;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtensionAllowedValidatorTest {
  private static final String NOTIFIED_PERSON_PROFILE =
      "https://demis.rki.de/fhir/StructureDefinition/NotifiedPerson";
  private static final String BUNDLE_PROFILE =
      "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory";

  private static final String MESSAGE_ID_UNEXPECTED_EXTENSION = "Unexpected_Extension";
  private static final String MESSAGE_ID_MODIFIER_EXTENSION = "Modifier_Extension_not_allowed";

  private static final String LOGGER_NAME_UNEXPECTED_EXTENSION = "unexpected_extension";
  private static final String LOGGER_NAME_MODIFIER_EXTENSION = "modifier_extension";

  private static final String ELEMENT_PATH = "locationPath";
  private static final ResultSeverityEnum SEVERITY = ResultSeverityEnum.ERROR;

  @Mock private FhirContext fhirContext;
  @Mock private IValidationSupport validationSupport;
  @Mock private ResourceWalker resourceWalker;
  @Mock private StructureDefinitionExtensionExtractor structureDefinitionExtensionExtractor;

  private ExtensionAllowedValidator underTest;

  private String loggerName;
  private ListAppender<ILoggingEvent> logEntries;

  private static Extension createExtension(final String url) {
    return new Extension().setUrl(url).setValue(new StringType().setValue("test"));
  }

  private static Patient createResourceWithProfile(final String profile) {
    final Patient resource = new Patient();
    resource.getMeta().addProfile(profile);
    return resource;
  }

  private static ElementCtx createElementCtx(
      final Resource resource, final Base element, final String path) {
    return new ElementCtx(ELEMENT_PATH, path, resource, element, BUNDLE_PROFILE);
  }

  private static String expectedLogMessage(
      final String url, String location, String profile, String extensionPath, String bundleType) {
    final String template =
"""
{"url":"%s","location":"%s","profile":"%s","extensionPath":"%s","bundleType":"%s"}
""";
    return String.format(template, url, location, profile, extensionPath, bundleType).trim();
  }

  @BeforeEach
  void setUp() {
    underTest =
        new ExtensionAllowedValidator(
            validationSupport,
            SEVERITY,
            resourceWalker,
            structureDefinitionExtensionExtractor,
            true,
            true);
  }

  private void mockResourceWalker(final ElementCtx... elements) {
    doAnswer(
            invocation -> {
              final Consumer<ElementCtx> action = invocation.getArgument(1);
              stream(elements).forEach(action);
              return null; // void-Method
            })
        .when(resourceWalker)
        .forEachElementDepthFirst(any(), any());
  }

  private List<SingleValidationMessage> executeValidation() {
    final Bundle resource = new Bundle();
    final IValidationContext<IBaseResource> validationContext =
        ValidationContext.forResource(fhirContext, resource, null);
    logEntries = listenToLog(loggerName);
    underTest.validateResource(validationContext);
    verify(resourceWalker).forEachElementDepthFirst(eq(resource), any());
    return validationContext.getMessages();
  }

  @Nested
  class ModifierExtensions {
    @BeforeEach
    void setupModifierExtension() {
      loggerName = LOGGER_NAME_MODIFIER_EXTENSION;
    }

    @Test
    void denyModifierExtensions() {
      final String path = "Patient";
      final String extensionUrl = "http://demis.de/test/my-extension";
      final Patient modifierExtensionsContainer =
          createResourceWithProfile(NOTIFIED_PERSON_PROFILE);
      modifierExtensionsContainer.addModifierExtension(createExtension(extensionUrl));
      mockResourceWalker(
          createElementCtx(modifierExtensionsContainer, modifierExtensionsContainer, path));

      final List<SingleValidationMessage> messages = executeValidation();

      final String locationPath = ELEMENT_PATH + ".modifierExtension[0]";
      assertThat(messages)
          .extracting(
              SingleValidationMessage::getSeverity,
              SingleValidationMessage::getMessageId,
              SingleValidationMessage::getMessage,
              SingleValidationMessage::getLocationString)
          .singleElement()
          .isEqualTo(
              tuple(
                  ResultSeverityEnum.ERROR,
                  MESSAGE_ID_MODIFIER_EXTENSION,
                  "Modifier Extensions sind nicht erlaubt.",
                  locationPath));

      assertThat(logEntries.list)
          .singleElement()
          .satisfies(
              logEntry -> assertThat(logEntry.getLevel()).isEqualTo(Level.WARN),
              logEntry ->
                  assertThat(logEntry.getMessage())
                      .isEqualTo(
                          expectedLogMessage(
                              extensionUrl,
                              locationPath,
                              NOTIFIED_PERSON_PROFILE,
                              path + ".modifierExtension",
                              BUNDLE_PROFILE)));
    }

    @Test
    void allowNormalExtensions() {
      final String path = "Patient";
      final String extensionUrl = "http://demis.de/test/my-extension";
      final Patient modifierExtensionsContainer =
          createResourceWithProfile(NOTIFIED_PERSON_PROFILE);
      modifierExtensionsContainer.addExtension(createExtension(extensionUrl));
      mockResourceWalker(
          createElementCtx(modifierExtensionsContainer, modifierExtensionsContainer, path));

      final List<SingleValidationMessage> messages = executeValidation();

      assertThat(messages).isEmpty();
      assertThat(logEntries.list).isEmpty();
    }
  }

  @Nested
  class ExtensionUrlCheck {

    private static String getMessage(
        final String extensionUrl,
        final String profile,
        final String extensionPath,
        final String allowedUrlsInfo) {
      return String.format(
          "Es wurde eine unerwartete Extension %s gefunden. Das Profil %s erlaubt für %s %s",
          extensionUrl, profile, extensionPath, allowedUrlsInfo);
    }

    private static SingleValidationMessage expectedValidationMessage(
        final String messageId, final String messageString, final String path) {
      final SingleValidationMessage expected = new SingleValidationMessage();
      expected.setSeverity(SEVERITY);
      expected.setMessageId(messageId);
      expected.setLocationString(path);
      expected.setMessage(messageString);
      return expected;
    }

    private static ElementCtx createElementCtx(final String profile, String path, String url) {
      return ExtensionAllowedValidatorTest.createElementCtx(
          createResourceWithProfile(profile), createExtension(url), path);
    }

    @BeforeEach
    void setupExtensionUrlCheck() {
      loggerName = LOGGER_NAME_UNEXPECTED_EXTENSION;
    }

    @Test
    void extensionDefinedAndUrlAllowed() {
      final String extensionPath = "Patient.address.extension";
      final String extensionUrl = "http://demis.de/test/my-extension";
      mockResourceWalker(createElementCtx(NOTIFIED_PERSON_PROFILE, extensionPath, extensionUrl));

      setupAllowedExtensions(
          NOTIFIED_PERSON_PROFILE, Map.of(extensionPath, Set.of(extensionUrl, "some-other-url")));

      final List<SingleValidationMessage> messages = executeValidation();
      assertThat(messages).isEmpty();
      assertThat(logEntries.list).isEmpty();
    }

    @Test
    void extensionDefinedInStructureDefinitionButUrlDoesNotMatch() {
      final String extensionPath = "Patient.address.extension";
      final String extensionUrl = "http://demis.de/test/my-extension";
      mockResourceWalker(createElementCtx(NOTIFIED_PERSON_PROFILE, extensionPath, extensionUrl));

      setupAllowedExtensions(
          NOTIFIED_PERSON_PROFILE, Map.of(extensionPath, Set.of("some-other-url")));

      final List<SingleValidationMessage> messages = executeValidation();

      final String expectedMessage =
          getMessage(
              extensionUrl,
              NOTIFIED_PERSON_PROFILE,
              extensionPath,
              "nur die folgenden Extensions: [some-other-url]");
      assertMessage(messages, expectedMessage);
      assertThat(logEntries.list)
          .singleElement()
          .satisfies(
              logEntry -> assertThat(logEntry.getLevel()).isEqualTo(Level.WARN),
              logEntry ->
                  assertThat(logEntry.getMessage())
                      .isEqualTo(
                          expectedLogMessage(
                              extensionUrl,
                              "locationPath",
                              NOTIFIED_PERSON_PROFILE,
                              extensionPath,
                              BUNDLE_PROFILE)));
    }

    @Test
    void extensionNotDefinedInStructureDefinition() {
      final String extensionPath = "Patient.address.extension";
      final String extensionUrl = "http://demis.de/test/my-extension";
      mockResourceWalker(createElementCtx(NOTIFIED_PERSON_PROFILE, extensionPath, extensionUrl));

      setupAllowedExtensions(NOTIFIED_PERSON_PROFILE, Map.of("other-path", Set.of(extensionPath)));

      final List<SingleValidationMessage> messages = executeValidation();

      final var expectedMessage =
          getMessage(extensionUrl, NOTIFIED_PERSON_PROFILE, extensionPath, "keine Extensions");
      assertMessage(messages, expectedMessage);
    }

    @Test
    void structureDefinitionForProfileNotFound() {
      final String extensionPath = "Patient.address.extension";
      final String extensionUrl = "http://demis.de/test/my-extension";
      mockResourceWalker(createElementCtx(NOTIFIED_PERSON_PROFILE, extensionPath, extensionUrl));

      final List<SingleValidationMessage> messages = executeValidation();

      verifyNoInteractions(structureDefinitionExtensionExtractor);

      final var expectedMessage =
          getMessage(extensionUrl, NOTIFIED_PERSON_PROFILE, extensionPath, "keine Extensions");
      assertMessage(messages, expectedMessage);
    }

    @Test
    void allowedExtensionUrlsForProfileAreCached() {
      final String extensionPath = "Patient.address.extension";
      final String extensionUrl = "http://demis.de/test/my-extension";
      mockResourceWalker(createElementCtx(NOTIFIED_PERSON_PROFILE, extensionPath, extensionUrl));

      setupAllowedExtensions(
          NOTIFIED_PERSON_PROFILE, Map.of(extensionPath, Set.of("some-other-url")));

      final List<SingleValidationMessage> result1 = executeValidation();

      final var result2 = executeValidation();
      assertThat(result2).isEqualTo(result1);

      verify(validationSupport, times(1)).fetchResource(any(), any());
      verify(structureDefinitionExtensionExtractor, times(1)).buildAllowedExtensionUrlsMap(any());
    }

    private void setupAllowedExtensions(
        final String profile, Map<String, Set<String>> allowedExtensions) {
      final StructureDefinition sd = new StructureDefinition();
      when(validationSupport.fetchResource(StructureDefinition.class, profile)).thenReturn(sd);
      when(structureDefinitionExtensionExtractor.buildAllowedExtensionUrlsMap(sd))
          .thenReturn(new AllowedExtensionUrls(allowedExtensions));
    }

    private void assertMessage(
        final List<SingleValidationMessage> actualMessages, final String expectedMessage) {
      final SingleValidationMessage expected =
          expectedValidationMessage(MESSAGE_ID_UNEXPECTED_EXTENSION, expectedMessage, ELEMENT_PATH);
      assertThat(actualMessages).singleElement().usingRecursiveComparison().isEqualTo(expected);
    }
  }
}
