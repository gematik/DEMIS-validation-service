package de.gematik.demis.validationservice.services.terminology.local;

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

import static org.apache.commons.lang3.StringUtils.isAnyEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;

/**
 * This component checks for valid codes in ValueSets, which have an expansion (so the ValueSets do
 * not have to be expanded, as done in {@link InMemoryTerminologyServerValidationSupport}. The
 * implementation <em>only</em> supports R4 resources for now.
 *
 * <p>This component does not test for codes not matching the preconditions, so other components in
 * a chain can handle the cases not covered by this component.
 *
 * <p>Informational match messages (produced by successful code-in-ValueSet lookups) are only
 * included in the {@link IValidationSupport.CodeValidationResult} when the configured {@code
 * minSeverityOutcome} is {@link ResultSeverityEnum#INFORMATION}. This prevents the OperationOutcome
 * from being flooded with noise when validating resources with many coded values.
 */
@Slf4j
@RequiredArgsConstructor
final class ExpandedValueSetsCodeValidationSupport implements IValidationSupport {

  private static final String VALUE_NOT_PRESENT = "not-present";

  private final FhirContext ctx;

  /**
   * Minimum severity threshold for OperationOutcome entries. Used to suppress low-severity
   * informational messages produced by {@link #createMatchResult} when they would be filtered by
   * the {@link de.gematik.demis.validationservice.services.validation.ValidationService} anyway.
   */
  private final ResultSeverityEnum minSeverityOutcome;

  @Override
  public FhirContext getFhirContext() {
    return ctx;
  }

  @Override
  public boolean isCodeSystemSupported(
      ValidationSupportContext theValidationSupportContext, String theCodeSystem) {
    if (isBlank(theCodeSystem)) {
      log.debug("Code system is not supported! System name empty.");
      return false;
    }
    IBaseResource codeSystem =
        theValidationSupportContext.getRootValidationSupport().fetchCodeSystem(theCodeSystem);
    if (codeSystem == null) {
      log.debug("Code system is not supported! System unknown. CodeSystem: {}", theCodeSystem);
      return false;
    }
    return isCodeSystemSupported(theCodeSystem, codeSystem);
  }

  private boolean isCodeSystemSupported(String theCodeSystem, IBaseResource codeSystem) {
    IPrimitiveType<?> content = getContentField(codeSystem);
    if (VALUE_NOT_PRESENT.equals(content.getValueAsString())) {
      log.debug(
          "Code system is not supported! System content value not present. CodeSystem: {}",
          theCodeSystem);
      return false;
    }
    log.debug("Code system is supported: {}", theCodeSystem);
    return true;
  }

  private IPrimitiveType<?> getContentField(IBaseResource cs) {
    return getFhirContext().newTerser().getSingleValueOrNull(cs, "content", IPrimitiveType.class);
  }

  @Override
  public boolean isValueSetSupported(
      ValidationSupportContext theValidationSupportContext, String theValueSetUrl) {
    if (isBlank(theValueSetUrl)) {
      log.debug("Value set is not supported! Set is empty.");
      return false;
    }
    IValidationSupport rootValidationSupport =
        theValidationSupportContext.getRootValidationSupport();
    ValueSet valueSet = (ValueSet) rootValidationSupport.fetchValueSet(theValueSetUrl);
    if (hasNoExpansion(valueSet)) {
      log.debug("Value set is not supported! Set has no expansion. ValueSet: {}", theValueSetUrl);
      return false;
    }
    log.debug("Value set is supported! ValueSet: {}", theValueSetUrl);
    return true;
  }

  @Override
  public CodeValidationResult validateCodeInValueSet(
      ValidationSupportContext theValidationSupportContext,
      ConceptValidationOptions theOptions,
      String theCodeSystem,
      String theCode,
      String theDisplay,
      IBaseResource theValueSet) {
    IValidationSupport rootValidationSupport =
        theValidationSupportContext.getRootValidationSupport();
    if (theValueSet instanceof ValueSet valueSet) {
      return validateCodeInValueSet(
          valueSet, rootValidationSupport, theOptions, theCodeSystem, theCode, theDisplay);
    }
    log.debug(
        "Failed to validate code of value set! Unknown type. CodeSystem: {} Code: {} Display: {} Type: {}",
        theCodeSystem,
        theCode,
        theDisplay,
        theValueSet == null ? "" : theValueSet.getClass().getName());
    return null;
  }

  @Override
  public CodeValidationResult validateCode(
      ValidationSupportContext theValidationSupportContext,
      ConceptValidationOptions theOptions,
      String theCodeSystem,
      String theCode,
      String theDisplay,
      String theValueSetUrl) {
    if (isEmpty(theValueSetUrl)) {
      return null;
    }
    IValidationSupport rootValidationSupport =
        theValidationSupportContext.getRootValidationSupport();
    ValueSet valueSet = (ValueSet) rootValidationSupport.fetchValueSet(theValueSetUrl);
    if (valueSet == null) {
      return null;
    }
    return validateCodeInValueSet(
        valueSet, rootValidationSupport, theOptions, theCodeSystem, theCode, theDisplay);
  }

  private CodeValidationResult validateCodeInValueSet(
      ValueSet vs,
      IValidationSupport rootValidationSupport,
      ConceptValidationOptions theOptions,
      String theCodeSystem,
      String theCode,
      String theDisplay) {
    if (hasNoExpansion(vs) || isAnyEmpty(theCode, theCodeSystem)) {
      log.debug(
          "Failed to validate code of value set! Empty or no expansion found. CodeSystem: {} Code: {} Display: {}",
          theCodeSystem,
          theCode,
          theDisplay);
      return null;
    }
    Map<String, CodeSystem> codeSystems = new HashMap<>();
    Function<String, CodeSystem> codeSystemFinder = s -> findCodeSystem(rootValidationSupport, s);
    List<ValueSetExpansionContainsComponent> contains = vs.getExpansion().getContains();
    Deque<ValueSetExpansionContainsComponent> containsDeque = new ArrayDeque<>(contains);
    while (!containsDeque.isEmpty()) {
      ValueSetExpansionContainsComponent current = containsDeque.pollLast();

      // Add further child codes to the processing deque
      if (current.hasContains()) {
        containsDeque.addAll(current.getContains());
      }
      String currentSystem = current.getSystem();
      // code does not match if CodeSystem URL does not match
      if (!theCodeSystem.equals(currentSystem)) {
        continue;
      }
      CodeSystem codeSystem = codeSystems.computeIfAbsent(currentSystem, codeSystemFinder);
      if (checkCodeMatches(current.getCode(), codeSystem, theCode)) {
        return createMatchResult(vs, theOptions, theCodeSystem, current, codeSystem, theDisplay);
      }
    }
    return createMismatchResult(theCodeSystem, theCode, codeSystems, codeSystemFinder);
  }

  private CodeValidationResult createMatchResult(
      ValueSet vs,
      ConceptValidationOptions theOptions,
      String theCodeSystem,
      ValueSetExpansionContainsComponent current,
      CodeSystem codeSystem,
      String theDisplay) {

    boolean infoLevelAllowed = isSeverityAtOrAboveMinimum(ResultSeverityEnum.INFORMATION);
    // A display mismatch produces a WARNING; only populate the full result when that WARNING
    // would also pass the severity threshold.
    boolean displayWarnAllowed =
        theOptions.isValidateDisplay() && isSeverityAtOrAboveMinimum(ResultSeverityEnum.WARNING);

    if (!infoLevelAllowed && !displayWarnAllowed) {
      // Neither an INFO match message nor a display-mismatch WARNING would be included in the
      // OperationOutcome. Skip the full result creation and return only the minimal signal
      // that tells HAPI FHIR the code is valid, avoiding unnecessary object allocation.
      return new CodeValidationResult().setCode(current.getCode());
    }

    String codeSystemVersion = null;
    String codeSystemName = null;
    if (codeSystem != null) {
      codeSystemVersion = codeSystem.getVersion();
      codeSystemName = codeSystem.getName();
    } else {
      log.warn(
          "CodeSystem '{}' not found, even though used in ValueSet '{}'",
          theCodeSystem,
          vs.getUrl());
    }
    CodeValidationResult result = new CodeValidationResult();
    setDisplay(vs, theOptions, theDisplay, current, result);
    result
        .setCode(current.getCode())
        .setCodeSystemName(codeSystemName)
        .setCodeSystemVersion(codeSystemVersion);
    if (infoLevelAllowed) {
      result.setMessage(
          "Code was validated against existing expansion of ValueSet: " + vs.getUrl());
    }
    return result;
  }

  /**
   * Returns {@code true} when {@code severity} meets or exceeds the configured {@link
   * #minSeverityOutcome} threshold, using the ordering INFORMATION(0) &lt; WARNING(1) &lt; ERROR(2)
   * &lt; FATAL(3). A {@code null} {@code minSeverityOutcome} is treated as {@link
   * ResultSeverityEnum#INFORMATION} (lowest), so every severity passes.
   */
  private boolean isSeverityAtOrAboveMinimum(ResultSeverityEnum severity) {
    if (minSeverityOutcome == null) {
      return true;
    }
    return severityOrdinal(severity) >= severityOrdinal(minSeverityOutcome);
  }

  private static int severityOrdinal(ResultSeverityEnum s) {
    return switch (s) {
      case INFORMATION -> 0;
      case WARNING -> 1;
      case ERROR -> 2;
      case FATAL -> 3;
    };
  }

  /**
   * Validation of the display value.
   *
   * <p>If validation is requested and the display values do not match we issue a warning and send
   * the correct display value in the validation result.
   *
   * <p>If validation is not requested, and we receive a display value we insert the given display
   * value into the validation result.
   *
   * @param vs value set
   * @param theOptions validation options
   * @param theDisplay received display value
   * @param current matched schema details
   * @param result validation result
   */
  private static void setDisplay(
      ValueSet vs,
      ConceptValidationOptions theOptions,
      String theDisplay,
      ValueSetExpansionContainsComponent current,
      CodeValidationResult result) {
    final String expected = current.getDisplay();
    if (theOptions.isValidateDisplay()) {
      result.setDisplay(expected);
      if (!Strings.CS.equals(expected, theDisplay)) {
        result.setSeverity(IssueSeverity.WARNING);
        log.warn(
            "Validated display value '{}' did not match ValueSet '{}' expected value '{}'",
            theDisplay,
            vs.getUrl(),
            expected);
      }
    } else {
      if (StringUtils.isBlank(theDisplay)) {
        result.setDisplay(expected);
      } else {
        result.setDisplay(theDisplay);
      }
    }
  }

  private static CodeValidationResult createMismatchResult(
      String theCodeSystem,
      String theCode,
      Map<String, CodeSystem> codeSystems,
      Function<String, CodeSystem> codeSystemFinder) {
    // no match found, populate result accordingly

    // detect content, if fragment, only warning
    CodeSystem codeSystem = codeSystems.computeIfAbsent(theCodeSystem, codeSystemFinder);
    CodeSystemContentMode content = CodeSystemContentMode.COMPLETE;
    if (codeSystem != null) {
      content = codeSystem.getContent();
    }

    IssueSeverity severity;
    String message;
    // TODO: look for way to construct the message with HAPI I18N facilities
    if (content == CodeSystemContentMode.FRAGMENT) {
      severity = IssueSeverity.WARNING;
      message = "Unknown code in fragment CodeSystem '" + theCodeSystem + "#" + theCode + "'";
    } else {
      severity = IssueSeverity.ERROR;
      message = "Unknown code '" + theCodeSystem + "#" + theCode + "'";
    }
    CodeValidationResult result = new CodeValidationResult();
    result.setSeverity(severity).setMessage(message);
    return result;
  }

  private boolean checkCodeMatches(
      String actualCode, CodeSystem expectedSystem, String expectedCode) {
    // reading the CodeSystem spec, default should be not case sensitive
    boolean isCaseSensitive = false;
    if ((expectedSystem != null) && expectedSystem.hasCaseSensitive()) {
      isCaseSensitive = expectedSystem.getCaseSensitive();
    }
    return isCaseSensitive
        ? expectedCode.equals(actualCode)
        : expectedCode.equalsIgnoreCase(actualCode);
  }

  private boolean hasNoExpansion(ValueSet vs) {
    return (vs == null) || !vs.hasExpansion() || !vs.getExpansion().hasContains();
  }

  private CodeSystem findCodeSystem(IValidationSupport rootValidationSupport, String url) {
    return (CodeSystem) rootValidationSupport.fetchCodeSystem(url);
  }
}
