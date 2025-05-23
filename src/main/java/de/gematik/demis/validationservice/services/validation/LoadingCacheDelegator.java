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

import ca.uhn.fhir.sl.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.Map;

final class LoadingCacheDelegator<K, V> extends CacheDelegator<K, V> implements LoadingCache<K, V> {

  LoadingCacheDelegator(Cache<K, V> cache) {
    super(cache);
  }

  @Override
  public V get(K key) {
    return get().get(key);
  }

  @Override
  public Map<K, V> getAll(Iterable<? extends K> keys) {
    return get().getAll(keys);
  }

  @Override
  public void refresh(K key) {
    get().refresh(key);
  }

  @Override
  public com.github.benmanes.caffeine.cache.LoadingCache<K, V> get() {
    return (com.github.benmanes.caffeine.cache.LoadingCache<K, V>) super.get();
  }
}
