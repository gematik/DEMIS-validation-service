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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport.CacheTimeouts;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SpecialCachingValidationSupportTest {

  private SpecialCachingValidationSupport underTest;
  private IValidationSupport validationSupportMock;

  @BeforeEach
  void setUp() {
    validationSupportMock = Mockito.mock(IValidationSupport.class);
    final FhirContext fhirContext = Mockito.mock(FhirContext.class);
    when(validationSupportMock.getFhirContext()).thenReturn(fhirContext);
    underTest =
        new SpecialCachingValidationSupport(validationSupportMock, CacheTimeouts.defaultValues());
  }

  @Test
  void isValueSetSupportedCached() {
    final ValidationSupportContext validationSupportContext =
        Mockito.mock(ValidationSupportContext.class);

    final String url = "http://my.com/ValueSet/test";
    when(validationSupportMock.isValueSetSupported(validationSupportContext, url)).thenReturn(true);
    assertThat(underTest.isValueSetSupported(validationSupportContext, url)).isTrue();
    // call method with same url again
    assertThat(underTest.isValueSetSupported(validationSupportContext, url)).isTrue();
    // and verify that underlying validation support is only called one times
    verify(validationSupportMock, times(1)).isValueSetSupported(validationSupportContext, url);

    // test another url (not cached) that returns false
    final String anotherUrl = "http://my.com/ValueSet/testFalse";
    when(validationSupportMock.isValueSetSupported(validationSupportContext, anotherUrl))
        .thenReturn(false);
    assertThat(underTest.isValueSetSupported(validationSupportContext, anotherUrl)).isFalse();
  }

  @Test
  void fetchResourceExists() {
    final String url = "http://my.com/ValueSet/test";
    final ValueSet valueSet = new ValueSet();
    when(validationSupportMock.fetchResource(ValueSet.class, url)).thenReturn(valueSet);
    assertThat(underTest.fetchResource(ValueSet.class, url)).isSameAs(valueSet);
    // call method with same url again
    assertThat(underTest.fetchResource(ValueSet.class, url)).isSameAs(valueSet);
    // and verify that underlying validation support is only called one times
    verify(validationSupportMock, times(1)).fetchResource(ValueSet.class, url);
  }

  @Test
  void fetchResourceNotExists() {
    final String url = "http://my.com/ValueSet/test";
    when(validationSupportMock.fetchResource(ValueSet.class, url)).thenReturn(null);
    assertThat(underTest.fetchResource(ValueSet.class, url)).isNull();
    // call method with same url again
    assertThat(underTest.fetchResource(ValueSet.class, url)).isNull();
    // and verify that underlying validation support is only called one times
    verify(validationSupportMock, times(1)).fetchResource(ValueSet.class, url);
  }
}
