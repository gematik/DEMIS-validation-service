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

import static de.gematik.demis.validationservice.services.validation.extension.StructureDefinitionExtensionExtractor.NO_URLS_ALLOWED;
import static java.time.temporal.ChronoUnit.HOURS;

import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.sl.cache.Cache;
import ca.uhn.fhir.sl.cache.CacheFactory;
import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.IValidatorModule;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.demis.validationservice.services.validation.extension.ResourceWalker.ElementCtx;
import de.gematik.demis.validationservice.services.validation.extension.StructureDefinitionExtensionExtractor.AllowedExtensionUrls;
import java.time.Duration;
import java.util.Set;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseHasModifierExtensions;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@RequiredArgsConstructor
class ExtensionAllowedValidator implements IValidatorModule {

  private static final Logger UNEXPECTED_EXTENSION_LOGGER =
      LoggerFactory.getLogger("unexpected_extension");
  private static final Logger MODIFIER_EXTENSION_LOGGER =
      LoggerFactory.getLogger("modifier_extension");

  private static final long CACHE_TIMEOUT = Duration.of(24, HOURS).toMillis();
  private static final long CACHE_MAX_SIZE = 1000;

  private static final String MESSAGE_ID_UNEXPECTED_EXTENSION = "Unexpected_Extension";
  private static final String MESSAGE_ID_MODIFIER_EXTENSION = "Modifier_Extension_not_allowed";

  private final IValidationSupport validationSupport;
  private final ResultSeverityEnum unexpectedExtensionSeverity;

  private final ResourceWalker resourceWalker;
  private final StructureDefinitionExtensionExtractor structureDefinitionExtensionExtractor;

  private final boolean featureFlagValidationExtensionCheckEnabled;
  private final boolean featureFlagDenyModifierExtensions;

  private final Cache<String, AllowedExtensionUrls> cacheAllowedExtensionUrlsByProfileMap =
      CacheFactory.build(CACHE_TIMEOUT, CACHE_MAX_SIZE);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void validateResource(final IValidationContext<IBaseResource> validationContext) {
    resourceWalker.forEachElementDepthFirst(
        (Resource) validationContext.getResource(),
        elementCtx -> {
          if (featureFlagDenyModifierExtensions
              && elementCtx.element()
                  instanceof IBaseHasModifierExtensions modifierExtensionContainer) {
            validateModifierExtensions(modifierExtensionContainer, elementCtx, validationContext);
          }
          if (featureFlagValidationExtensionCheckEnabled
              && elementCtx.element() instanceof Extension extension) {
            validateExtension(extension, elementCtx, validationContext);
          }
        });
  }

  private void validateModifierExtensions(
      final IBaseHasModifierExtensions modifierExtensionContainer,
      final ElementCtx elementCtx,
      final IValidationContext<IBaseResource> validationContext) {
    final var modifierExtensions = modifierExtensionContainer.getModifierExtension();
    if (!modifierExtensions.isEmpty()) {
      for (int i = 0; i < modifierExtensions.size(); i++) {
        final UnexpectedExtensionInfo info =
            UnexpectedExtensionInfo.builder()
                .url(modifierExtensions.get(i).getUrl())
                .location(elementCtx.locationPath() + ".modifierExtension[" + i + "]")
                .profile(elementCtx.getCurrentResourceProfile())
                .extensionPath(elementCtx.resourcePath() + ".modifierExtension")
                .bundleType(elementCtx.bundleProfile())
                .build();
        addValidationMessageForModifierExtension(validationContext, info);
        logExtension(MODIFIER_EXTENSION_LOGGER, info);
      }
    }
  }

  private void validateExtension(
      final Extension extension,
      final ElementCtx elementCtx,
      final IValidationContext<IBaseResource> validationContext) {
    final String url = extension.getUrl();
    final String resourceProfile = elementCtx.getCurrentResourceProfile();

    final var allowedExtensionUrls =
        getAllowedExtensionUrls(elementCtx.currentResource().fhirType(), resourceProfile);
    final Set<String> allowedUrls =
        allowedExtensionUrls.urlsByExtensionPath(elementCtx.resourcePath());
    if (allowedUrls.contains(url)) {
      log.debug(
          "Check passed. Profile {} allowed extension {} at {}.",
          resourceProfile,
          url,
          elementCtx.locationPath());
    } else {
      final var unexpectedExtensionInfo =
          UnexpectedExtensionInfo.builder()
              .url(url)
              .location(elementCtx.locationPath())
              .profile(resourceProfile)
              .extensionPath(elementCtx.resourcePath())
              .bundleType(elementCtx.bundleProfile())
              .build();
      addValidationMessageForUnexpectedExtension(
          validationContext, unexpectedExtensionInfo, allowedUrls);
      logExtension(UNEXPECTED_EXTENSION_LOGGER, unexpectedExtensionInfo);
    }
  }

  private void logExtension(
      final Logger logger, final UnexpectedExtensionInfo unexpectedExtensionInfo) {
    if (logger.isWarnEnabled()) {
      try {
        logger.warn(objectMapper.writeValueAsString(unexpectedExtensionInfo));
      } catch (final JsonProcessingException e) {
        // ignore
        log.error("error creating json for log entry", e);
      }
    }
  }

  private AllowedExtensionUrls getAllowedExtensionUrls(
      final String resourceType, final String resourceProfile) {
    final AllowedExtensionUrls cached =
        cacheAllowedExtensionUrlsByProfileMap.getIfPresent(resourceProfile);
    if (cached != null) {
      return cached;
    }
    final StructureDefinition structureDefinition =
        validationSupport.fetchResource(StructureDefinition.class, resourceProfile);
    if (structureDefinition == null) {
      log.warn(
          "Structure Definition {} not found (Resource Type is {})", resourceProfile, resourceType);
      return NO_URLS_ALLOWED;
    }
    final AllowedExtensionUrls result =
        structureDefinitionExtensionExtractor.buildAllowedExtensionUrlsMap(structureDefinition);
    cacheAllowedExtensionUrlsByProfileMap.put(resourceProfile, result);
    return result;
  }

  private void addValidationMessageForUnexpectedExtension(
      final IValidationContext<IBaseResource> validationContext,
      final UnexpectedExtensionInfo info,
      final Set<String> allowedUrls) {
    final String allowedUrlsString =
        allowedUrls.isEmpty() ? "keine Extensions" : "nur die folgenden Extensions: " + allowedUrls;
    final String message =
        String.format(
            "Es wurde eine unerwartete Extension %s gefunden. Das Profil %s erlaubt für %s %s",
            info.url(), info.profile(), info.extensionPath(), allowedUrlsString);
    addValidationMessage(
        validationContext,
        unexpectedExtensionSeverity,
        MESSAGE_ID_UNEXPECTED_EXTENSION,
        message,
        info);
  }

  private void addValidationMessageForModifierExtension(
      final IValidationContext<IBaseResource> validationContext,
      final UnexpectedExtensionInfo info) {
    final String message = "Modifier Extensions sind nicht erlaubt.";
    addValidationMessage(
        validationContext, ResultSeverityEnum.ERROR, MESSAGE_ID_MODIFIER_EXTENSION, message, info);
  }

  private void addValidationMessage(
      final IValidationContext<IBaseResource> validationContext,
      final ResultSeverityEnum severity,
      final String messageId,
      final String message,
      final UnexpectedExtensionInfo info) {
    final SingleValidationMessage msg = new SingleValidationMessage();
    msg.setSeverity(severity);
    msg.setMessageId(messageId);
    msg.setMessage(message);
    msg.setLocationString(info.location());
    validationContext.addValidationMessage(msg);
  }

  @Builder
  private record UnexpectedExtensionInfo(
      String url, String location, String profile, String extensionPath, String bundleType) {}
}
