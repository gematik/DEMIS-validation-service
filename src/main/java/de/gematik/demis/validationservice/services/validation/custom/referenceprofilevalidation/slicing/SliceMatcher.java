package de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing;

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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.PrimitiveType;

/// Determines which slice a runtime element belongs to, given the slicing definition
/// derived at startup.
///
/// A slice matches when *all* of its discriminators match. A discriminator matches when
/// there is at least one value at the discriminator path that satisfies the expected
/// value (for `value`) or contains the expected pattern (for `pattern`).
///
/// Pattern values use the normalized form produced by `SliceExtractor`: primitives as
/// `String` / `Number` / `Boolean`, composite types as `Map<String, Object>`, and
/// repeating fields as `List<Object>`. Pattern matching is a recursive "is-subset"
/// check: for each field in an expected map, at least one actual child must match; for
/// each element in an expected list, at least one actual child must match it.
///
/// If no slice matches, the caller must treat the element as outside the sliced scope
/// (no slice-specific rules apply).
@Slf4j
public class SliceMatcher {

  public Optional<SliceDef> matchSlice(Base element, SlicingRoot root) {
    return root.slices().stream()
        .filter(slice -> allDiscriminatorsMatch(element, slice))
        .findFirst();
  }

  private boolean allDiscriminatorsMatch(Base element, SliceDef slice) {
    return slice.discriminators().stream().allMatch(d -> discriminatorMatches(element, d));
  }

  private boolean discriminatorMatches(Base element, Discriminator discriminator) {
    List<Base> values = navigate(element, discriminator.path());
    return switch (discriminator.type()) {
      case VALUE -> values.stream().anyMatch(v -> valueEquals(v, discriminator.expected()));
      case PATTERN -> values.stream().anyMatch(v -> patternMatches(v, discriminator.expected()));
    };
  }

  private List<Base> navigate(Base start, String dotPath) {
    List<Base> current = List.of(start);
    for (String segment : dotPath.split("\\.")) {
      current =
          current.stream()
              .flatMap(b -> Arrays.stream(b.getProperty(segment.hashCode(), segment, false)))
              .toList();
    }
    return current;
  }

  private boolean valueEquals(Base actual, Object expected) {
    Object actualNormalized = unwrap(actual);
    return expected.equals(actualNormalized);
  }

  private boolean patternMatches(Base actual, Object expectedPattern) {
    if (expectedPattern instanceof Map<?, ?> expectedMap) {
      return mapPatternMatches(actual, expectedMap);
    }
    return valueEquals(actual, expectedPattern);
  }

  private boolean mapPatternMatches(Base actual, Map<?, ?> expectedMap) {
    return expectedMap.entrySet().stream().allMatch(entry -> propertyPatternMatches(actual, entry));
  }

  private boolean propertyPatternMatches(Base actual, Map.Entry<?, ?> entry) {
    String name = (String) entry.getKey();

    Base[] children = actual.getProperty(name.hashCode(), name, false);
    if (children == null || children.length == 0) {
      return false;
    }

    Object expected = entry.getValue();
    if (expected instanceof List<?> expectedList) {
      return expectedList.stream().allMatch(item -> anyChildMatches(children, item));
    }

    return anyChildMatches(children, expected);
  }

  private boolean anyChildMatches(Base[] children, Object expected) {
    return Arrays.stream(children).anyMatch(child -> patternMatches(child, expected));
  }

  private Object unwrap(Base value) {
    return value instanceof PrimitiveType<?> p ? p.getValue() : value;
  }
}
