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

  /**
   * Validates the answers of a questionnaire item if the item type is QUANTITY.
   *
   * @param item The response item to validate.
   * @param qItem The corresponding questionnaire item definition.
   * @param ctx The validation context to report validation messages.
   */
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
   * Validates a quantity against the constraints defined in the corresponding questionnaire item. *
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
      String url = ext.getUrl();
      if (ComparatorHelper.isQuantityConstraintExtension(url)) {

        Quantity refQuantity = (Quantity) ext.getValue();
        String comparator = ComparatorHelper.getComparator(url);

        if (refQuantity == null) continue;

        if (!ComparatorHelper.quantitiesHaveSameUnit(answerQuantity, refQuantity)) {
          addUnitValidationMessageToContext(answerQuantity, ctx, linkId, refQuantity);
          return;
        }

        if (!ComparatorHelper.compareQuantities(answerQuantity, refQuantity, comparator)) {
          addQuantityValidationMessageToContext(ctx, linkId, comparator, refQuantity);
        }
      }
    }
  }

  private static void addQuantityValidationMessageToContext(
      IValidationContext<?> ctx, String linkId, String comparator, Quantity refQuantity) {
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

  private static void addUnitValidationMessageToContext(
      Quantity answerQuantity, IValidationContext<?> ctx, String linkId, Quantity refQuantity) {
    ctx.addValidationMessage(
        createValidationMessage(
            "Unit mismatch in item "
                + linkId
                + ": expected "
                + refQuantity.getSystem()
                + "|"
                + refQuantity.getCode()
                + ", got "
                + answerQuantity.getSystem()
                + "|"
                + answerQuantity.getCode()));
  }
}
