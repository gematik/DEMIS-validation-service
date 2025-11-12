package de.gematik.demis.validationservice.services.validation.custom;

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

import java.util.Objects;
import org.hl7.fhir.r4.model.Quantity;

public final class ComparatorHelper {

  private ComparatorHelper() {}

  /**
   * Checks if the given extension URL represents a quantity constraint extension (min or max).
   *
   * @param url The extension URL to check.
   * @return true if the URL ends with 'questionnaire-minQuantity' or 'questionnaire-maxQuantity'.
   */
  public static boolean isQuantityConstraintExtension(String url) {
    return url != null
        && (url.endsWith("questionnaire-minQuantity") || url.endsWith("questionnaire-maxQuantity"));
  }

  /** Helper to treat null, empty or whitespace-only strings as blank. */
  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  /**
   * Determines whether two FHIR {@link Quantity} instances represent the same UCUM unit. Strict
   * UCUM check. Returns true only if: - both quantities are non-null - both system values are
   * non-blank and exactly equal - both code values are non-blank and exactly equal Otherwise
   * returns false.
   *
   * <p>Rationale: UCUM relies on the normalized code; the display text field ("unit") is not
   * reliable for canonical comparison and is therefore intentionally ignored.
   */
  public static boolean quantitiesHaveSameUnit(Quantity a, Quantity b) {
    if (a == null || b == null) {
      return false;
    }
    String aSystem = a.getSystem();
    String bSystem = b.getSystem();
    if (isBlank(aSystem) || isBlank(bSystem)) {
      return false;
    }
    if (!Objects.equals(aSystem, bSystem)) {
      return false;
    }
    String aCode = a.getCode();
    String bCode = b.getCode();
    if (isBlank(aCode) || isBlank(bCode)) {
      return false;
    }
    return Objects.equals(aCode, bCode);
  }

  public static String getComparator(String extensionUrl) {
    if (extensionUrl.endsWith("minQuantity")) {
      return ">=";
    } else if (extensionUrl.endsWith("maxQuantity")) {
      return "<=";
    } else {
      throw new IllegalArgumentException("Unknown quantity extension URL: " + extensionUrl);
    }
  }

  /**
   * Compares two quantities based on a comparator.
   *
   * <p>Null-safety: If ref quantity value is null, the comparison is skipped and treated as valid,
   * since there is no value to compare with. If actual quantity value is null, the comparison is
   * skipped and treated as invalid, since the value is not equal to the expected value from the
   * questionnaire profile definition. This avoids failing on partially specified Quantity answers.
   *
   * @param actual The actual quantity provided in the response.
   * @param ref The reference quantity defined in the questionnaire.
   * @param comparator The comparator to use for comparison (e.g., "<=", ">=").
   * @return True if the comparison is valid, false otherwise.
   */
  public static boolean compareQuantities(Quantity actual, Quantity ref, String comparator) {
    if (actual.getValue() == null) {
      return false;
    }
    if (ref.getValue() == null) {
      return true;
    }
    double actualValue = actual.getValue().doubleValue();
    double refValue = ref.getValue().doubleValue();

    return switch (comparator) {
      case "<=" -> actualValue <= refValue;
      case ">=" -> actualValue >= refValue;
      default -> throw new IllegalStateException("Unreachable comparator: " + comparator);
    };
  }
}
