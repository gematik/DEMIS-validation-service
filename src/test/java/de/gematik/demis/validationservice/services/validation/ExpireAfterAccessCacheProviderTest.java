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
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ca.uhn.fhir.sl.cache.Cache;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class ExpireAfterAccessCacheProviderTest {

  private static final ExpireAfterAccessCacheProvider<String, String> CACHE_PROVIDER =
      new ExpireAfterAccessCacheProvider<>();
  private static final long TIMEOUT_MILLIS = 50L;

  @Test
  void shouldCreateExpireAfterAccessCache() {
    Cache<String, String> cache = CACHE_PROVIDER.create(TIMEOUT_MILLIS);
    assertThat(cache.estimatedSize()).isZero();
    cache.put("one", "one");
    cache.put("two", "two");
    await("expire after access")
        .pollDelay(TIMEOUT_MILLIS + (TIMEOUT_MILLIS / 2), TimeUnit.MILLISECONDS)
        .atMost(TIMEOUT_MILLIS * 3, TimeUnit.MILLISECONDS)
        .until(() -> Objects.isNull(cache.getIfPresent("one")));
  }

  @Test
  void shouldCreateWithMaximumSize() {
    final int maximumSize = 2;
    Cache<String, String> cache = CACHE_PROVIDER.create(TIMEOUT_MILLIS, maximumSize);
    cache.put("one", "one");
    cache.put("two", "two");
    cache.put("three", "three");
    cache.put("four", "four");
    // waiting because the cache size can be exceeded for a short period of time
    await("maximum size eviction")
        .atMost(1L, TimeUnit.SECONDS)
        .until(() -> cache.estimatedSize() <= maximumSize);
  }
}
