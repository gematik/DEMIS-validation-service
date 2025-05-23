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

import ca.uhn.fhir.sl.cache.Cache;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class CacheDelegator<K, V>
    implements Cache<K, V>, Supplier<com.github.benmanes.caffeine.cache.Cache<K, V>> {

  private final com.github.benmanes.caffeine.cache.Cache<K, V> cache;

  @Override
  public V getIfPresent(K key) {
    return this.cache.getIfPresent(key);
  }

  @Override
  public V get(K key, Function<? super K, ? extends V> mappingFunction) {
    return this.cache.get(key, mappingFunction);
  }

  @Override
  public Map<K, V> getAllPresent(Iterable<? extends K> keys) {
    return this.cache.getAllPresent(keys);
  }

  @Override
  public void put(K key, V value) {
    this.cache.put(key, value);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    this.cache.putAll(map);
  }

  @Override
  public void invalidate(K key) {
    this.cache.invalidate(key);
  }

  @Override
  public void invalidateAll(Iterable<? extends K> keys) {
    this.cache.invalidateAll(keys);
  }

  @Override
  public void invalidateAll() {
    this.cache.invalidateAll();
  }

  @Override
  public long estimatedSize() {
    return this.cache.estimatedSize();
  }

  @Override
  public void cleanUp() {
    this.cache.cleanUp();
  }

  @Override
  public com.github.benmanes.caffeine.cache.Cache<K, V> get() {
    return this.cache;
  }
}
