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

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public final class FilteredMessagePrefixesFactory implements Supplier<Set<String>> {

  private static final Set<String> FILTERED_MESSAGES_KEYS =
      Set.of(
          "Reference_REF_CantMatchChoice",
          "BUNDLE_BUNDLE_ENTRY_MULTIPLE_PROFILES_NO_MATCH_REASON",
          "Validation_VAL_Profile_NoMatch",
          "This_element_does_not_match_any_known_slice_");
  private final Set<String> messages;

  public FilteredMessagePrefixesFactory(final Locale locale) {
    final var resourceBundle = ResourceBundle.getBundle("Messages", locale);
    messages =
        FILTERED_MESSAGES_KEYS.stream()
            .map(resourceBundle::getString)
            .map(
                message -> {
                  final int indexParameter = message.indexOf("{");
                  return message.substring(
                      0, indexParameter > 0 ? indexParameter : message.length());
                })
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public Set<String> get() {
    return messages;
  }
}
