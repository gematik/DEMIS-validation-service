package de.gematik.demis.validationservice.services.validation;

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
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import com.google.common.base.Strings;
import de.gematik.demis.validationservice.config.ValidationConfigProperties;
import de.gematik.demis.validationservice.services.ValidationMetrics;
import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Service holds a Fhir validator with the given profile. */
@Service
@Slf4j
public class ValidationService {
  private static final SeverityComparator SEVERITY_COMPARATOR = new SeverityComparator();
  public static final String
      THIS_WARNING_WILL_BE_TREATED_AS_ERROR_BY_08_31_2025_PLEASE_CHECK_YOUR_NOTIFICATION_COMPOSITION =
          "This warning will be treated as error by 01/01/2026. Please check your notification composition";

  private final FhirContext fhirContext;
  private final FhirValidatorManager validatorManager;
  private final ValidationMetrics validationMetrics;
  private final ResultSeverityEnum minSeverityOutcome;
  private final Set<String> filteredMessagePrefixes;
  private final boolean isFilteredErrorsToWarningsEnabled;

  ValidationService(
      final FhirContext fhirContext,
      final FhirValidatorManager validatorManager,
      final ValidationMetrics validationMetrics,
      final ValidationConfigProperties configProperties,
      final FilteredMessagePrefixesFactory filteredMessagePrefixesFactory,
      @Value("${feature.flag.filtered.errors.as.warnings.disabled}")
          final boolean isFilteredErrorsAsWarwningsDisabled) {
    this.fhirContext = fhirContext;
    this.validatorManager = validatorManager;
    this.validationMetrics = validationMetrics;
    this.minSeverityOutcome = configProperties.minSeverityOutcome();
    this.filteredMessagePrefixes = filteredMessagePrefixesFactory.get();
    this.isFilteredErrorsToWarningsEnabled = !isFilteredErrorsAsWarwningsDisabled;
  }

  private static void reduceIssuesSeverityToWarn(final OperationOutcome operationOutcome) {
    operationOutcome.getIssue().stream()
        .filter(
            issue ->
                issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL
                    || issue.getSeverity() == OperationOutcome.IssueSeverity.ERROR)
        .forEach(issue -> issue.setSeverity(OperationOutcome.IssueSeverity.WARNING));
  }

  @Observed(
      name = "validate",
      contextualName = "validate",
      lowCardinalityKeyValues = {"notification", "fhir"})
  public OperationOutcome validate(final String content) {
    return this.validate(content, null);
  }

  @Observed(
      name = "validate",
      contextualName = "validate",
      lowCardinalityKeyValues = {"notification", "fhir"})
  public OperationOutcome validate(final String content, @CheckForNull final String principalId) {
    final var versions = validatorManager.getVersions().iterator();

    String version;
    ValidationResult validationResult;
    ValidationResult firstValidationResult = null;

    do {
      version = versions.next();
      try {
        validationResult = validateContentWithProfileVersion(content, version, principalId);
      } catch (final Exception e) {
        return handleException(e);
      }

      if (firstValidationResult == null) {
        firstValidationResult = validationResult;
      }

    } while (!validationResult.isSuccessful() && versions.hasNext());

    // TODO muss noch mit RKI geklärt werden
    // we always return the validation messages of the newest profiles
    final OperationOutcome operationOutcome = toOperationOutcome(firstValidationResult);
    // In case the content is valid just with older profiles, we reduce the severity of the error
    // issues to warn
    if (validationResult.isSuccessful() && validationResult != firstValidationResult) {
      reduceIssuesSeverityToWarn(operationOutcome);
    }
    // TODO wollen wir noch die Version zurückgeben oder ins OperationOutcome schreiben?

    return operationOutcome;
  }

  private ValidationResult validateContentWithProfileVersion(
      final String content, final String version, @CheckForNull final String senderId) {
    final long startTime = System.currentTimeMillis();

    final FhirValidator validator =
        validatorManager
            .getFhirValidator(version)
            .orElseThrow(
                () -> new IllegalStateException("No validator found for version " + version));

    final ValidationResult result =
        filterValidationResult(validator.validateWithResult(content), version, senderId);

    log.info(
        "Validation with version {} successful: {} time: {}ms",
        version,
        result.isSuccessful(),
        System.currentTimeMillis() - startTime);

    validationMetrics.incValidationCount(version, result.isSuccessful());

    return result;
  }

  private ValidationResult filterValidationResult(
      final ValidationResult validationResult,
      @Nonnull final String profileVersion,
      @CheckForNull final String principalId) {
    List<SingleValidationMessage> resultList = validationResult.getMessages();
    if (isFilteredErrorsToWarningsEnabled) {
      // Mutate resultList and keep the mutated errors around to log metrics
      final List<SingleValidationMessage> ignoredErrors =
          resultList.stream()
              .filter(this::isSuppressedMessage)
              .filter(m -> m.getSeverity().equals(ResultSeverityEnum.ERROR))
              .toList();
      for (SingleValidationMessage error : ignoredErrors) {
        error.setSeverity(ResultSeverityEnum.WARNING);
        error.setMessage(
            error.getMessage()
                + " "
                + THIS_WARNING_WILL_BE_TREATED_AS_ERROR_BY_08_31_2025_PLEASE_CHECK_YOUR_NOTIFICATION_COMPOSITION);
      }

      final List<String> actualLoggable =
          ignoredErrors.stream()
              .map(SingleValidationMessage::getMessageId)
              .filter(messageId -> !Strings.isNullOrEmpty(messageId))
              .toList();
      final String finalPrincipalId = Objects.requireNonNullElse(principalId, "<unknown>");
      validationMetrics.saveValidationFindings(finalPrincipalId, profileVersion, actualLoggable);
    }

    final List<SingleValidationMessage> result =
        resultList.stream().filter(this::checkSeverity).toList();
    return new ValidationResult(fhirContext, result);
  }

  private OperationOutcome toOperationOutcome(final ValidationResult validationResult) {
    final OperationOutcome outcome = new OperationOutcome();
    validationResult.populateOperationOutcome(outcome);
    return outcome;
  }

  private boolean isSuppressedMessage(SingleValidationMessage message) {
    return filteredMessagePrefixes.stream().anyMatch(message.getMessage()::startsWith);
  }

  private boolean checkSeverity(SingleValidationMessage message) {
    return SEVERITY_COMPARATOR.compare(message.getSeverity(), minSeverityOutcome) >= 0;
  }

  private OperationOutcome handleException(final Exception e) {
    final String errorId = UUID.randomUUID().toString();
    log.error("{} - exception in validation", errorId, e);

    final OperationOutcome operationOutcome = new OperationOutcome();
    operationOutcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.FATAL)
        .setCode(OperationOutcome.IssueType.EXCEPTION)
        .setDiagnostics("exception in validation")
        .addLocation(errorId);
    return operationOutcome;
  }
}
