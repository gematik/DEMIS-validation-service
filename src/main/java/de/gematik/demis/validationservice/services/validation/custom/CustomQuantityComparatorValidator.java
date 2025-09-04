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

import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.IValidatorModule;
import java.util.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;

/**
 * Custom validator for comparing quantities in FHIR QuestionnaireResponses. Implements the
 * IValidatorModule interface to validate resources.
 */
public class CustomQuantityComparatorValidator extends AbstractCustomValidator
    implements IValidatorModule {

  /**
   * Constructor to initialize the validator with a map of questionnaires.
   *
   * @param questionnaireMap A map of questionnaire URLs to IBaseResource objects. Only
   *     Questionnaire resources are retained.
   */
  public CustomQuantityComparatorValidator(Map<String, IBaseResource> questionnaireMap) {
    super(questionnaireMap);
  }

  @Override
  protected void validateItemAnswer(
      QuestionnaireResponse.QuestionnaireResponseItemComponent item,
      Questionnaire.QuestionnaireItemComponent qItem,
      IValidationContext<?> ctx) {
    if (qItem.getType() == Questionnaire.QuestionnaireItemType.QUANTITY) {
      for (QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent answer :
          item.getAnswer()) {
        Quantity quantity = answer.getValueQuantity();
        if (quantity != null) {
          validateQuantityAnswer(quantity, qItem, ctx, item.getLinkId());
        }
      }
    }
  }

  /**
   * Validates a quantity answer against constraints defined in the questionnaire item.
   *
   * @param answerQuantity The quantity provided in the response.
   * @param qItem The corresponding questionnaire item.
   * @param ctx The validation context to report validation messages.
   * @param linkId The linkId of the questionnaire item.
   */
  private void validateQuantityAnswer(
      Quantity answerQuantity,
      Questionnaire.QuestionnaireItemComponent qItem,
      IValidationContext<?> ctx,
      String linkId) {

    for (Extension ext : qItem.getExtension()) {
      if (!isQuantityConstraintExtension(ext)) continue;

      Quantity refQuantity = getSubExtensionValue(ext);
      String comparator = getSubExtensionComparator(ext);

      if (refQuantity == null || comparator == null) continue;

      if (!quantitiesHaveSameUnit(answerQuantity, refQuantity)) {
        ctx.addValidationMessage(
            createValidationMessage(
                "Unit mismatch in item "
                    + linkId
                    + ": expected "
                    + refQuantity.getUnit()
                    + ", got "
                    + answerQuantity.getUnit()));
        return;
      }

      if (!compareQuantities(answerQuantity, refQuantity, comparator)) {
        ctx.addValidationMessage(
            createValidationMessage(
                "Quantity in item "
                    + linkId
                    + " violates constraint: "
                    + comparator
                    + " "
                    + refQuantity.getValue()
                    + " "
                    + refQuantity.getUnit()));
      }
    }
  }

  private boolean isQuantityConstraintExtension(Extension ext) {
    String url = ext.getUrl();
    return url.endsWith("questionnaire-minQuantity") || url.endsWith("questionnaire-maxQuantity");
  }

  private Quantity getSubExtensionValue(Extension ext) {
    return ext.getExtension().stream()
        .filter(sub -> sub.getUrl().equals("value"))
        .map(sub -> (Quantity) sub.getValue())
        .findFirst()
        .orElse(null);
  }

  private String getSubExtensionComparator(Extension ext) {
    return ext.getExtension().stream()
        .filter(sub -> sub.getUrl().equals("comparator"))
        .map(sub -> sub.getValueAsPrimitive().getValueAsString())
        .findFirst()
        .orElse(null);
  }

  private boolean quantitiesHaveSameUnit(Quantity a, Quantity b) {
    return Objects.equals(a.getUnit(), b.getUnit())
        && Objects.equals(a.getSystem(), b.getSystem())
        && Objects.equals(a.getCode(), b.getCode());
  }

  /**
   * Compares two quantities based on a comparator.
   *
   * @param actual The actual quantity provided in the response.
   * @param ref The reference quantity defined in the questionnaire.
   * @param comparator The comparator to use for comparison (e.g., "<", "<=", ">", ">=").
   * @return True if the comparison is valid, false otherwise.
   */
  private boolean compareQuantities(Quantity actual, Quantity ref, String comparator) {
    double actualValue = actual.getValue().doubleValue();
    double refValue = ref.getValue().doubleValue();
    return switch (comparator) {
      case "<" -> actualValue < refValue;
      case "<=" -> actualValue <= refValue;
      case ">" -> actualValue > refValue;
      case ">=" -> actualValue >= refValue;
      default -> true;
    };
  }
}
