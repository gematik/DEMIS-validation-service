package de.gematik.demis.validationservice.services.terminology.remote;

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

import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.sl.cache.Cache;
import ca.uhn.fhir.sl.cache.CacheFactory;
import jakarta.annotation.Nullable;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.instance.model.api.IBaseResource;

class SpecialCachingValidationSupport extends CachingValidationSupport {

  private final Cache<String, Boolean> valueSetSupportedCache;
  private final Cache<String, Boolean> notFoundValueSetCache;

  public SpecialCachingValidationSupport(
      final IValidationSupport theWrap, final CacheTimeouts theCacheTimeouts) {
    super(theWrap, theCacheTimeouts);
    valueSetSupportedCache = CacheFactory.build(theCacheTimeouts.getMiscMillis(), 50000);
    notFoundValueSetCache = CacheFactory.build(theCacheTimeouts.getMiscMillis(), 50000);
  }

  @Override
  public boolean isValueSetSupported(
      final ValidationSupportContext theValidationSupportContext, final String theValueSetUrl) {
    final var cachedValue = valueSetSupportedCache.getIfPresent(theValueSetUrl);
    if (cachedValue != null) {
      return cachedValue;
    }
    final boolean value = super.isValueSetSupported(theValidationSupportContext, theValueSetUrl);
    valueSetSupportedCache.put(theValueSetUrl, value);
    return value;
  }

  // This method (not fetchValueSet) is called from hapi validator to check if the system is not a
  // valueSet
  @Override
  public <T extends IBaseResource> T fetchResource(
      @Nullable final Class<T> theClass, final String theUri) {
    final String key = theClass + " " + theUri;
    final Boolean cachedValue = notFoundValueSetCache.getIfPresent(key);
    if (cachedValue != null && cachedValue) {
      return null;
    }
    final T valueSet = super.fetchResource(theClass, theUri);
    if (valueSet == null) {
      // only insert null values into the cache. The nonnull values are cached by super class
      notFoundValueSetCache.put(key, true);
    }
    return valueSet;
  }
}
