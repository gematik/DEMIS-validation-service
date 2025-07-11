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

import static ca.uhn.fhir.validation.ResultSeverityEnum.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.FATAL;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import de.gematik.demis.validationservice.config.ValidationConfigProperties;
import de.gematik.demis.validationservice.services.ValidationMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ValidationServiceRegressionTest {

  private static final String CONTENT = "Some Fhir bundle";
  private static final String IGNORED_VALIDATION_ERROR = "IGNORE_THIS_ERROR";
  private static final String VALID_INFO_MESSAGE = "No issues detected during validation";
  private static final String EXCEPTION_MESSAGE = "exception in validation";

  private final FhirContext fhirContext = FhirContext.forR4Cached();
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private FhirValidatorManager fhirValidatorManagerMock;

  private ValidationService underTest;

  private static SingleValidationMessage createValidationMessage(
      final ResultSeverityEnum severity, final String message) {
    final SingleValidationMessage validationMessage = new SingleValidationMessage();
    validationMessage.setSeverity(severity);
    validationMessage.setMessage(message);
    return validationMessage;
  }

  private static Tags tags(final String version, final boolean success) {
    return Tags.of("profiles_version", version, "success", String.valueOf(success));
  }

  @BeforeEach
  void setUp() {
    fhirValidatorManagerMock = mock(FhirValidatorManager.class);

    meterRegistry.clear();
    final var validationMetrics = new ValidationMetrics(meterRegistry);

    final ValidationConfigProperties props =
        new ValidationConfigProperties(null, Locale.getDefault(), ResultSeverityEnum.WARNING, 1);

    final FilteredMessagePrefixesFactory filteredMessagePrefixesFactory =
        mock(FilteredMessagePrefixesFactory.class);
    when(filteredMessagePrefixesFactory.get()).thenReturn(Set.of(IGNORED_VALIDATION_ERROR));

    underTest =
        new ValidationService(
            fhirContext,
            fhirValidatorManagerMock,
            validationMetrics,
            props,
            filteredMessagePrefixesFactory,
            true,
            false);
  }

  private void assertOperationOutcome(
      final OperationOutcome result,
      final IssueSeverity expectedSeverity,
      final String expectedMessage) {
    assertThat(result).isNotNull();
    assertThat(result.getIssue()).hasSize(1);
    final var issue = result.getIssue().getFirst();
    assertThat(issue.getSeverity()).isEqualTo(expectedSeverity);
    assertThat(issue.getDiagnostics()).isEqualTo(expectedMessage);
  }

  private void assertMetrics(final Tags... expectedTags) {
    final Map<Tags, Double> expectedCounters =
        Stream.of(expectedTags).collect(Collectors.toMap(Function.identity(), t -> 1.0d));
    final Map<Tags, Double> actualCounters = getCounters();
    assertThat(actualCounters).isEqualTo(expectedCounters);
  }

  private Map<Tags, Double> getCounters() {
    return meterRegistry.find("DEMIS_COUNTER_VALIDATION").counters().stream()
        .collect(Collectors.toMap(c -> Tags.of(c.getId().getTags()), Counter::count));
  }

  @Nested
  class OneProfilesVersion {

    private static final String PROFILES_VERSION = "1.0.0";

    private FhirValidator fhirValidatorMock;

    @BeforeEach
    void setUp() {
      when(fhirValidatorManagerMock.getVersions()).thenReturn(List.of(PROFILES_VERSION));

      fhirValidatorMock = mock(FhirValidator.class);
      when(fhirValidatorManagerMock.getFhirValidator(PROFILES_VERSION))
          .thenReturn(Optional.of(fhirValidatorMock));
    }

    @Test
    void valid() {
      when(fhirValidatorMock.validateWithResult(CONTENT))
          .thenReturn(new ValidationResult(fhirContext, List.of()));

      final OperationOutcome result = underTest.validate(CONTENT);

      assertOperationOutcome(result, INFORMATION, VALID_INFO_MESSAGE);
      assertMetrics(tags(PROFILES_VERSION, true));
    }

    @Test
    void invalid() {
      final var errorMsg =
          createValidationMessage(ResultSeverityEnum.ERROR, "some validation error");
      when(fhirValidatorMock.validateWithResult(CONTENT))
          .thenReturn(new ValidationResult(fhirContext, List.of(errorMsg)));

      final OperationOutcome result = underTest.validate(CONTENT);

      assertOperationOutcome(result, ERROR, errorMsg.getMessage());
      assertMetrics(tags(PROFILES_VERSION, false));
    }

    @Test
    void validationErrorsAreChangedToWarningsWhenOnSpecifiedList() {
      final var errorMsg =
          createValidationMessage(ResultSeverityEnum.ERROR, IGNORED_VALIDATION_ERROR);
      when(fhirValidatorMock.validateWithResult(CONTENT))
          .thenReturn(new ValidationResult(fhirContext, List.of(errorMsg)));

      final OperationOutcome result = underTest.validate(CONTENT);

      assertOperationOutcome(
          result,
          IssueSeverity.WARNING,
          IGNORED_VALIDATION_ERROR
              + " This warning will be treated as error by 08/31/2025. Please check your notification composition");
      assertMetrics(tags(PROFILES_VERSION, true));
    }

    @Test
    void exceptionInHapiFhirValidator() {
      when(fhirValidatorMock.validateWithResult(CONTENT))
          .thenThrow(new NullPointerException("just for testing"));

      final OperationOutcome result = underTest.validate(CONTENT);

      assertOperationOutcome(result, FATAL, EXCEPTION_MESSAGE);
      assertMetrics();
    }
  }

  @Nested
  class TwoProfilesVersions {
    private static final String NEW_VERSION = "2.0.0";
    private static final String OLD_VERSION = "1.0.0";

    private FhirValidator newProfilesVersionFhirValidatorMock;
    private FhirValidator oldProfilesVersionFhirValidatorMock;

    @BeforeEach
    void setUp() {
      when(fhirValidatorManagerMock.getVersions()).thenReturn(List.of(NEW_VERSION, OLD_VERSION));

      newProfilesVersionFhirValidatorMock = mock(FhirValidator.class);
      when(fhirValidatorManagerMock.getFhirValidator(NEW_VERSION))
          .thenReturn(Optional.of(newProfilesVersionFhirValidatorMock));

      oldProfilesVersionFhirValidatorMock = mock(FhirValidator.class);
      when(fhirValidatorManagerMock.getFhirValidator(OLD_VERSION))
          .thenReturn(Optional.of(oldProfilesVersionFhirValidatorMock));
    }

    @Test
    void validWithNewestVersion() {
      when(newProfilesVersionFhirValidatorMock.validateWithResult(CONTENT))
          .thenReturn(new ValidationResult(fhirContext, List.of()));

      final OperationOutcome result = underTest.validate(CONTENT);

      assertOperationOutcome(result, INFORMATION, VALID_INFO_MESSAGE);
      assertMetrics(tags(NEW_VERSION, true));

      verifyNoInteractions(oldProfilesVersionFhirValidatorMock);
    }

    @Test
    void invalidWithNewestVersionButValidWithOlderVersion() {
      final SingleValidationMessage errorMsg =
          createValidationMessage(
              ResultSeverityEnum.ERROR, "some validation error from newest version");

      when(newProfilesVersionFhirValidatorMock.validateWithResult(CONTENT))
          .thenReturn(new ValidationResult(fhirContext, List.of(errorMsg)));
      when(oldProfilesVersionFhirValidatorMock.validateWithResult(CONTENT))
          .thenReturn(
              new ValidationResult(
                  fhirContext, List.of(createValidationMessage(WARNING, "is ignored"))));

      final OperationOutcome result = underTest.validate(CONTENT);

      assertOperationOutcome(result, IssueSeverity.WARNING, errorMsg.getMessage());
      assertMetrics(tags(NEW_VERSION, false), tags(OLD_VERSION, true));
    }

    @Test
    void invalidWithAllVersions() {
      final SingleValidationMessage errorMsg =
          createValidationMessage(
              ResultSeverityEnum.ERROR, "some validation error from newest version");

      when(newProfilesVersionFhirValidatorMock.validateWithResult(CONTENT))
          .thenReturn(new ValidationResult(fhirContext, List.of(errorMsg)));
      when(oldProfilesVersionFhirValidatorMock.validateWithResult(CONTENT))
          .thenReturn(
              new ValidationResult(
                  fhirContext,
                  List.of(createValidationMessage(ResultSeverityEnum.ERROR, "is ignored"))));

      final OperationOutcome result = underTest.validate(CONTENT);

      assertOperationOutcome(result, ERROR, errorMsg.getMessage());
      assertMetrics(tags(NEW_VERSION, false), tags(OLD_VERSION, false));
    }
  }
}
