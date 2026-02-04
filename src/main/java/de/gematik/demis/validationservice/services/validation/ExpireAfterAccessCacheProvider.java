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

import ca.uhn.fhir.sl.cache.Cache;
import ca.uhn.fhir.sl.cache.CacheLoader;
import ca.uhn.fhir.sl.cache.CacheProvider;
import ca.uhn.fhir.sl.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

/**
 * HAPI FHIR cache provider using Caffeine and expire after access.
 *
 * @param <K> key
 * @param <V> value
 */
public final class ExpireAfterAccessCacheProvider<K, V> implements CacheProvider<K, V> {

  @Override
  public Cache<K, V> create(long timeoutMillis) {
    return new CacheDelegator<>(
        Caffeine.newBuilder().expireAfterAccess(timeoutMillis, TimeUnit.MILLISECONDS).build());
  }

  @Override
  public LoadingCache<K, V> create(long timeoutMillis, CacheLoader<K, V> loading) {
    return new LoadingCacheDelegator<>(
        Caffeine.newBuilder()
            .expireAfterAccess(timeoutMillis, TimeUnit.MILLISECONDS)
            .build(loading::load));
  }

  @Override
  public Cache<K, V> create(long timeoutMillis, long maximumSize) {
    return new CacheDelegator<>(
        Caffeine.newBuilder()
            .expireAfterAccess(timeoutMillis, TimeUnit.MILLISECONDS)
            .maximumSize(maximumSize)
            .build());
  }

  @Override
  public LoadingCache<K, V> create(
      long timeoutMillis, long maximumSize, CacheLoader<K, V> loading) {
    return new LoadingCacheDelegator<>(
        Caffeine.newBuilder()
            .expireAfterAccess(timeoutMillis, TimeUnit.MILLISECONDS)
            .maximumSize(maximumSize)
            .build(loading::load));
  }
}
