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

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExpandedValueSetsCodeValidationSupportTest {

  /** unit under test */
  private static final ExpandedValueSetsCodeValidationSupport VALIDATION =
      new ExpandedValueSetsCodeValidationSupport(FhirContext.forR4());

  private static final String VALUE_SET_URL = "https://demis.rki.de/fhir/ValueSet/materialEBCP";
  private static final String DISPLAY = "Specimen from abscess obtained by aspiration (specimen)";
  private static final String CODE = "446972001";
  private static final String SYSTEM = "http://snomed.info/sct";
  private static final String VERSION = "42";

  private static ValidationSupportContext context;
  private static ConceptValidationOptions options;

  @BeforeAll
  static void createContext() {
    IValidationSupport validation = Mockito.mock(IValidationSupport.class);
    prepareFetchValueSet(validation);
    prepareFetchCodeSystem(validation);
    context = Mockito.mock(ValidationSupportContext.class);
    Mockito.when(context.getRootValidationSupport()).thenReturn(validation);
  }

  private static void prepareFetchValueSet(IValidationSupport validation) {
    ValueSet valueSet = new ValueSet();
    valueSet.setUrl(VALUE_SET_URL);
    ValueSet.ValueSetExpansionComponent expansion = new ValueSet.ValueSetExpansionComponent();
    valueSet.setExpansion(expansion);
    ValueSet.ValueSetExpansionContainsComponent code =
        new ValueSet.ValueSetExpansionContainsComponent();
    expansion.addContains(code);
    code.setDisplay(DISPLAY);
    code.setCode(CODE);
    code.setSystem(SYSTEM);
    Mockito.when(validation.fetchValueSet(VALUE_SET_URL)).thenReturn(valueSet);
  }

  private static void prepareFetchCodeSystem(IValidationSupport validation) {
    CodeSystem system = new CodeSystem();
    system.setUrl(SYSTEM);
    system.setVersion(VERSION);
    system.setContent(CodeSystem.CodeSystemContentMode.FRAGMENT);
    Mockito.when(validation.fetchCodeSystem(SYSTEM)).thenReturn(system);
  }

  @BeforeAll
  static void createOptions() {
    options = Mockito.mock(ConceptValidationOptions.class);
    Mockito.when(options.isValidateDisplay()).thenReturn(false);
  }

  @Test
  void shouldDeclineEmptyTestObjects() {
    assertThat(VALIDATION.isCodeSystemSupported(null, null)).isFalse();
    assertThat(VALIDATION.isValueSetSupported(null, null)).isFalse();
    assertThat(
            VALIDATION.validateCode(
                Mockito.mock(ValidationSupportContext.class), null, null, null, null, null))
        .isNull();
    assertThat(
            VALIDATION.validateCodeInValueSet(
                Mockito.mock(ValidationSupportContext.class), null, null, null, null, null))
        .isNull();
  }

  @Test
  void shouldCreateMatchResult() {
    // support check
    assertThat(VALIDATION.isValueSetSupported(context, VALUE_SET_URL))
        .as("value set support check")
        .isTrue();
    // validation
    IValidationSupport.CodeValidationResult result =
        VALIDATION.validateCode(context, options, SYSTEM, CODE, DISPLAY, VALUE_SET_URL);
    // verify
    assertThat(result).as("validation result").isNotNull();
    assertThat(result.getCodeSystemVersion()).as("system version").isEqualTo(VERSION);
    assertThat(result.getCode()).as("result code").isEqualTo(CODE);
    assertThat(result.getMessage())
        .as("validation result message")
        .isEqualTo("Code was validated against existing expansion of ValueSet: " + VALUE_SET_URL);
  }

  @Test
  void shouldCreateMismatchResult() {
    // support check
    assertThat(VALIDATION.isValueSetSupported(context, VALUE_SET_URL))
        .as("value set support check")
        .isTrue();
    // validation
    String fooCode = "foo-code";
    IValidationSupport.CodeValidationResult result =
        VALIDATION.validateCode(context, options, SYSTEM, fooCode, DISPLAY, VALUE_SET_URL);
    // verify
    assertThat(result).as("validation result").isNotNull();
    String expectedMessage = "Unknown code in fragment CodeSystem '" + SYSTEM + "#" + fooCode + "'";
    assertThat(result.getMessage()).as("validation result message").isEqualTo(expectedMessage);
  }

  @Test
  void shouldEvaluateCodeSystems() {
    assertThat(VALIDATION.isCodeSystemSupported(context, SYSTEM))
        .as("supported code system")
        .isTrue();
    assertThat(VALIDATION.isCodeSystemSupported(context, "unsupported"))
        .as("unsupported code system")
        .isFalse();
  }
}
