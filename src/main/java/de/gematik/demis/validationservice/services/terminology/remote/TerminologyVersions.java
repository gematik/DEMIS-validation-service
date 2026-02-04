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

import java.util.Map;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Builder
class TerminologyVersions {

  private final Map<String, String> valueSetUrlToVersionMap;
  private final Map<String, String> codeSystemUrlToVersionMap;

  @Nullable
  public String getCodeSystemVersion(final String codeSystemUrl) {
    return codeSystemUrlToVersionMap.get(codeSystemUrl);
  }

  @Nullable
  public String getValueSetVersion(final String valueSetUrl) {
    return valueSetUrlToVersionMap.get(valueSetUrl);
  }
}
