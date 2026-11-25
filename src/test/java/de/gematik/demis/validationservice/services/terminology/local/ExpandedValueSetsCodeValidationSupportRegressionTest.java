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
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import de.gematik.demis.validationservice.config.FeatureFlags;
import de.gematik.demis.validationservice.config.ValidationConfigProperties;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpandedValueSetsCodeValidationSupportRegressionTest {

  private static final String VALUE_SET_URL = "https://demis.rki.de/fhir/ValueSet/materialEBCP";
  private static final String DISPLAY = "Specimen from abscess obtained by aspiration (specimen)";
  private static final String CODE = "446972001";
  private static final String SYSTEM = "http://snomed.info/sct";
  private static final String VERSION = "42";

  private static ValidationSupportContext context;
  private static ConceptValidationOptions options;

  @Mock private ValidationConfigProperties validationConfigProperties;
  @Mock private FeatureFlags featureFlags;
  private ExpandedValueSetsCodeValidationSupport expandedValueSetsCodeValidationSupport;

  @BeforeAll
  static void createContext() {
    IValidationSupport validation = Mockito.mock(IValidationSupport.class);
    prepareFetchValueSet(validation);
    prepareFetchCodeSystem(validation);
    context = Mockito.mock(ValidationSupportContext.class);
    when(context.getRootValidationSupport()).thenReturn(validation);
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
    code.setVersion(VERSION);
    when(validation.fetchValueSet(VALUE_SET_URL)).thenReturn(valueSet);
  }

  private static void prepareFetchCodeSystem(IValidationSupport validation) {
    CodeSystem system = new CodeSystem();
    system.setUrl(SYSTEM);
    system.setVersion(VERSION);
    system.setContent(CodeSystem.CodeSystemContentMode.FRAGMENT);
    when(validation.fetchCodeSystem(SYSTEM)).thenReturn(system);
  }

  @BeforeAll
  static void createOptions() {
    options = Mockito.mock(ConceptValidationOptions.class);
    when(options.isValidateDisplay()).thenReturn(false);
  }

  @BeforeEach
  void createValidationSupport() {
    this.expandedValueSetsCodeValidationSupport =
        new ExpandedValueSetsCodeValidationSupport(
            FhirContext.forR4Cached(), this.validationConfigProperties, this.featureFlags);
  }

  @Test
  void shouldCreateMatchResult() {
    when(validationConfigProperties.minSeverityOutcome())
        .thenReturn(ResultSeverityEnum.INFORMATION);

    // support check
    assertThat(expandedValueSetsCodeValidationSupport.isValueSetSupported(context, VALUE_SET_URL))
        .as("value set support check")
        .isTrue();
    // validation
    final String hapi8VersionedCodeSystemUrl = SYSTEM + "|" + "version.illegal.but.unchecked";
    IValidationSupport.CodeValidationResult result =
        expandedValueSetsCodeValidationSupport.validateCode(
            context, options, hapi8VersionedCodeSystemUrl, CODE, DISPLAY, VALUE_SET_URL);
    // verify
    assertThat(result).as("validation result").isNotNull();
    assertThat(result.getCodeSystemVersion()).as("system version").isEqualTo(VERSION);
    assertThat(result.getCode()).as("result code").isEqualTo(CODE);
    assertThat(result.getMessage())
        .as("validation result message")
        .isEqualTo("Code was validated against existing expansion of ValueSet: " + VALUE_SET_URL);
  }
}
