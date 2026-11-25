package de.gematik.demis.validationservice.services.validation;

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
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import de.gematik.demis.validationservice.config.FeatureFlags;
import de.gematik.demis.validationservice.config.ValidationConfigProperties;
import de.gematik.demis.validationservice.services.ValidationMetrics;
import jakarta.annotation.PostConstruct;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

/**
 * Changes validation results of HAPI FHIR validation. Reduces severity of configured issues or even
 * removes findings. This way we can find workarounds to make due with incompatibilities between
 * FHIR interpretation of HAPI FHIR and RKI FHIR profiles.
 */
@RequiredArgsConstructor
@Service
@Slf4j
class ValidationResultPostProcessing {

  static final String THIS_WARNING_WILL_BE_TREATED_AS_ERROR_CHECK_YOUR_NOTIFICATION_COMPOSITION =
      "This warning will be treated as error by 16.06.2026. Please check your notification composition";
  static final String REDUCE_REF_CANT_MATCH_CHOICE = "Reference_REF_CantMatchChoice";
  /*
   * Downgrade ERROR → WARNING
   * Background: This error occurs when an element has multiple type profiles and
   * none of them match. Since HL7 FHIR 6.7.x (HAPI FHIR 8.8) it no longer appears when the actual error is in
   * the parent profile (not in the type profiles). The mechanism remains active for
   * scenarios with genuine type profile violations (e.g., IGs with multiple HumanName profiles).
   */
  static final String REDUCE_PROFILE_NO_MATCH = "Validation_VAL_Profile_NoMatch";
  static final String REDUCE_NOT_ANY_KNOWN_SLICE = "This_element_does_not_match_any_known_slice_";
  static final String REDUCE_MULTIPLE_PROFILES =
      "BUNDLE_BUNDLE_ENTRY_MULTIPLE_PROFILES_MULTIPLE_MATCHES";
  static final String REDUCE_PROFILE_NOT_SLICE = "Validation_VAL_Profile_NotSlice";

  static final String DELETE_QR_QUANTITY_MIN = "QUESTIONNAIRE_QR_ITEM_QUANTITY_MIN";

  private static final String MESSAGES_RESOURCE_BUNDLE_NAME = "Messages";

  private static final Set<String> REDUCE_MESSAGES_KEYS =
      Set.of(
          REDUCE_REF_CANT_MATCH_CHOICE,
          REDUCE_MULTIPLE_PROFILES,
          REDUCE_PROFILE_NO_MATCH,
          REDUCE_NOT_ANY_KNOWN_SLICE,
          REDUCE_PROFILE_NOT_SLICE);
  private static final SeverityComparator SEVERITY_COMPARATOR = new SeverityComparator();
  private static final String CODE_SYSTEM_ISO3166 = "urn:iso:std:iso:3166";

  private final FhirContext fhirContext;
  private final ValidationConfigProperties configProperties;
  private final ValidationMetrics validationMetrics;
  private final FeatureFlags featureFlags;

  /**
   * This is file Messages.properties of HAPI FHIR library <b>org.hl7.fhir.utilities</b>. HAPI
   * changes keys and messages between major versions as fit. We have to keep our keys and messages
   * updated.
   */
  private ResourceBundle resourceBundle;

  private MessagesMarkers reducedMessagesMarkers;
  private MessagesMarkers deletedMessagesMarkers;

  private boolean isFilteredErrorsAsWarningsEnabled() {
    return !featureFlags.isFilteredErrorsAsWarningsDisabled();
  }

  @PostConstruct
  void createReducedMessagesMarkers() {
    final Set<String> messageKeys = new HashSet<>();
    if (isFilteredErrorsAsWarningsEnabled()) {
      messageKeys.addAll(REDUCE_MESSAGES_KEYS);
    }
    this.reducedMessagesMarkers = createMessagesMarkers(messageKeys);
    log.info(
        "Initialized log-level reduced validation result messages. Expected: {} Actual: {}",
        messageKeys.size(),
        reducedMessagesMarkers.size());
  }

  @PostConstruct
  void createDeletedMessagesMarkers() {
    final Set<String> messageKeys = new HashSet<>();
    if (!featureFlags.isVsHapiQrQminVerificationEnabled()) {
      messageKeys.add(DELETE_QR_QUANTITY_MIN);
    }
    this.deletedMessagesMarkers = createMessagesMarkers(messageKeys);
    log.info(
        "Initialized deleted messages. Expected: {} Actual: {}",
        messageKeys.size(),
        deletedMessagesMarkers.size());
  }

  /**
   * Trim findings of HAPI FHIR validator validation result.
   *
   * @param rawValidationResult raw validation result of HAPI FHIR validator
   * @param profileVersion version of domain specific FHIR profile
   * @param principalId principal ID, sender ID
   * @return processed validation result to present to client
   */
  ValidationResult processValidationResult(
      final ValidationResult rawValidationResult,
      @Nonnull final String profileVersion,
      @CheckForNull final String principalId) {
    final List<SingleValidationMessage> messages = clone(rawValidationResult.getMessages());
    deleteFilteredI18nMessages(messages);
    reduceErrorsToWarningsOnFilteredI18nMessages(messages, profileVersion, principalId);
    reduceErrorsToWarningsOnAddressCountryCodeIso3166Errors(messages);
    reduceErrorsToWarningsOnAddressPostalCodeErrors(messages);
    reduceErrorsToWarningsOnAddressStateCodeIso3166Errors(messages);
    deleteLowSeverityMessages(messages);
    return createValidationResult(messages, profileVersion);
  }

  private void deleteLowSeverityMessages(List<SingleValidationMessage> messages) {
    messages.removeIf(this::isLowerThanMinSeverity);
  }

  private boolean isLowerThanMinSeverity(final SingleValidationMessage message) {
    return SEVERITY_COMPARATOR.compare(message.getSeverity(), configProperties.minSeverityOutcome())
        < 0;
  }

  private void reduceErrorsToWarningsOnAddressCountryCodeIso3166Errors(
      List<SingleValidationMessage> messages) {
    if (!featureFlags.isAddressCountryErrorsAsWarningsDisabled()) {
      reduceAddressUnknownCodeErrorsToWarnings(messages, "country", CODE_SYSTEM_ISO3166);
    }
  }

  private void reduceErrorsToWarningsOnAddressStateCodeIso3166Errors(
      List<SingleValidationMessage> messages) {
    if (!featureFlags.isAddressStateErrorsAsWarningsDisabled()) {
      reduceAddressUnknownCodeErrorsToWarnings(messages, "state", CODE_SYSTEM_ISO3166);
    }
  }

  private void reduceErrorsToWarningsOnAddressPostalCodeErrors(
      List<SingleValidationMessage> messages) {
    if (!featureFlags.isAddressZipErrorsAsWarningsDisabled()) {
      reduceAddressUnknownCodeErrorsToWarnings(
          messages, "postalCode", "https://demis.rki.de/fhir/CodeSystem/postalCode");
    }
  }

  private void reduceAddressUnknownCodeErrorsToWarnings(
      List<SingleValidationMessage> messages, String addressParameter, String codeSystem) {
    messages.stream()
        .filter(this::isError)
        .filter(this::hasMessageWithUnknownCode)
        .filter(message -> hasLocationWithAddressParameter(message, addressParameter))
        .filter(message -> hasMessageWithCodeSystem(message, codeSystem))
        .forEach(message -> message.setSeverity(ResultSeverityEnum.WARNING));
  }

  private boolean hasLocationWithAddressParameter(
      SingleValidationMessage message, String addressParameter) {
    final String location = message.getLocationString();
    return Strings.CI.contains(location, "address")
        && Strings.CI.endsWith(location, "." + addressParameter);
  }

  private boolean hasMessageWithUnknownCode(SingleValidationMessage singleValidationMessage) {
    final String message = singleValidationMessage.getMessage();
    return Strings.CS.contains(message, "Unknown code")
        || Strings.CS.contains(message, "Unbekannter Code");
  }

  private boolean hasMessageWithCodeSystem(SingleValidationMessage message, String codeSystem) {
    return Strings.CS.contains(message.getMessage(), codeSystem);
  }

  private @NonNull List<SingleValidationMessage> clone(List<SingleValidationMessage> rawMessages) {
    final List<SingleValidationMessage> messages = new ArrayList<>(rawMessages.size());
    rawMessages.stream().map(this::clone).forEach(messages::add);
    return messages;
  }

  private SingleValidationMessage clone(SingleValidationMessage message) {
    final SingleValidationMessage copy = new SingleValidationMessage();
    copy.setLocationCol(message.getLocationCol());
    copy.setLocationLine(message.getLocationLine());
    copy.setLocationString(message.getLocationString());
    copy.setMessage(message.getMessage());
    copy.setMessageId(message.getMessageId());
    copy.setSeverity(message.getSeverity());
    final List<String> sliceMessages = message.getSliceMessages();
    if (sliceMessages != null) {
      copy.setSliceMessages(new ArrayList<>(sliceMessages));
    }
    return copy;
  }

  private MessagesMarkers createMessagesMarkers(Set<String> messageKeys) {
    return new MessagesMarkers(
        messageKeys.stream()
            .map(this::getMessageOrNull)
            .filter(Objects::nonNull)
            .map(this::createMessageMarkers)
            .collect(Collectors.toUnmodifiableSet()));
  }

  private String getMessageOrNull(String key) {
    loadConfiguredOrRootResourceBundle();
    try {
      return resourceBundle.getString(key);
    } catch (MissingResourceException _) {
      log.warn("Missing resource bundle key: {}. Skipping this filter.", key);
      return null;
    }
  }

  private void loadConfiguredOrRootResourceBundle() {
    if (this.resourceBundle == null) {
      final Locale configuredLocale = this.configProperties.locale();
      final var noFallbackControl =
          ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);
      ResourceBundle bundle;
      try {
        bundle =
            ResourceBundle.getBundle(
                MESSAGES_RESOURCE_BUNDLE_NAME, configuredLocale, noFallbackControl);
      } catch (MissingResourceException _) {
        log.debug(
            "No resource bundle found for configuredLocale '{}', falling back to root bundle.",
            configuredLocale);
        bundle =
            ResourceBundle.getBundle(MESSAGES_RESOURCE_BUNDLE_NAME, Locale.ROOT, noFallbackControl);
      }
      this.resourceBundle = bundle;
      log.info(
          "Loaded validation result messages resource bundle. ConfiguredLocale: {} ActualLocale: {} ",
          print(configuredLocale),
          print(this.resourceBundle.getLocale()));
    }
  }

  private String print(Locale locale) {
    String text = locale == null ? null : locale.toString();
    if (StringUtils.isBlank(text)) {
      text = "root";
    }
    return text;
  }

  private Set<String> createMessageMarkers(String message) {
    final Set<String> markers = new HashSet<>();
    String remaining = message;
    while (remaining.contains("{")) {
      addMarker(remaining.substring(0, remaining.indexOf("{")), markers);
      remaining = remaining.substring(remaining.indexOf("}") + 1);
    }
    addMarker(remaining, markers);
    return Set.copyOf(markers);
  }

  private void addMarker(String marker, Set<String> markers) {
    final String text = StringUtils.trimToNull(MessageFormat.format(marker, (Object) null));
    if (text != null) {
      markers.add(text);
    }
  }

  private void deleteFilteredI18nMessages(List<SingleValidationMessage> messages) {
    final int countBefore = messages.size();
    final Set<SingleValidationMessage> toDelete =
        messages.stream().filter(this.deletedMessagesMarkers).collect(Collectors.toSet());
    messages.removeAll(toDelete);
    final int countAfter = messages.size();
    if (countAfter == countBefore) {
      log.info("No validation result messages deleted. Total messages: {}", countAfter);
    } else {
      log.info(
          "Deleted {} validation result messages based on message markers. Remaining messages: {}",
          countBefore - countAfter,
          countAfter);
    }
  }

  private void reduceErrorsToWarningsOnFilteredI18nMessages(
      List<SingleValidationMessage> messages, String profileVersion, String principalId) {
    if (isFilteredErrorsAsWarningsEnabled()) {

      // Mutate resultList and keep the mutated errors around to log metrics
      final List<SingleValidationMessage> ignoredErrors =
          messages.stream().filter(this::isError).filter(this.reducedMessagesMarkers).toList();
      for (SingleValidationMessage error : ignoredErrors) {
        error.setSeverity(ResultSeverityEnum.WARNING);
        error.setMessage(
            error.getMessage()
                + " "
                + THIS_WARNING_WILL_BE_TREATED_AS_ERROR_CHECK_YOUR_NOTIFICATION_COMPOSITION);
      }

      // We track suppressed errors to support software manufacturers in making their
      // software compliant with new profile versions. We are testing multiple profile
      // versions per request. We add a version tag to our metric, in order to
      // differentiate what errors occur in what profile version.
      final List<String> actualLoggable =
          ignoredErrors.stream()
              .map(SingleValidationMessage::getMessageId)
              .filter(StringUtils::isNotBlank)
              .toList();
      final String finalPrincipalId = Objects.requireNonNullElse(principalId, "<unknown>");
      validationMetrics.saveValidationFindings(finalPrincipalId, profileVersion, actualLoggable);
    }
  }

  private boolean isError(SingleValidationMessage message) {
    return message.getSeverity() == ResultSeverityEnum.ERROR;
  }

  private ValidationResult createValidationResult(
      List<SingleValidationMessage> messages, String profileVersion) {
    final var validationResult = new ValidationResult(fhirContext, messages);
    validationMetrics.incValidationCount(profileVersion, validationResult.isSuccessful());
    return validationResult;
  }

  private record MessagesMarkers(Set<Set<String>> messagesMarkers)
      implements Predicate<SingleValidationMessage> {

    @Override
    public boolean test(SingleValidationMessage singleValidationMessage) {
      final String text = singleValidationMessage.getMessage();
      for (Set<String> messageMarkers : messagesMarkers) {
        if (containsAll(text, messageMarkers)) {
          return true;
        }
      }
      return false;
    }

    private boolean containsAll(String text, Set<String> messageMarkers) {
      return messageMarkers.stream().map(text::contains).reduce(Boolean::logicalAnd).orElse(false);
    }

    int size() {
      return this.messagesMarkers.size();
    }
  }
}
