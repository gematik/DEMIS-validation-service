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
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;

public abstract class AbstractCustomValidator implements IValidatorModule {

  protected final Map<String, Questionnaire> questionnaireMap;

  /**
   * Constructor to initialize the validator with a map of questionnaires.
   *
   * @param questionnaireMap A map of questionnaire URLs to IBaseResource objects. Only
   *     Questionnaire resources are retained.
   */
  protected AbstractCustomValidator(Map<String, IBaseResource> questionnaireMap) {
    Map<String, Questionnaire> tmpMap =
        questionnaireMap.entrySet().stream()
            .filter(entry -> entry.getValue() instanceof Questionnaire)
            .collect(
                Collectors.toMap(Map.Entry::getKey, entry -> (Questionnaire) entry.getValue()));

    this.questionnaireMap = Map.copyOf(tmpMap);
  }

  /**
   * Validates a given FHIR resource.
   *
   * @param ctx The validation context containing the resource to validate.
   */
  @Override
  public void validateResource(IValidationContext<IBaseResource> ctx) {
    IBaseResource resource = ctx.getResource();
    validateResource(resource, ctx);
  }

  private void validateResource(IBaseResource resource, IValidationContext<IBaseResource> ctx) {
    if (resource instanceof QuestionnaireResponse response) {
      String questionnaireUrl = response.getQuestionnaire();
      if (questionnaireUrl == null) return;

      Questionnaire questionnaire = fetchQuestionnaire(questionnaireUrl);
      if (questionnaire == null) return;

      // 1. Map all questionnaire items recursively
      Map<String, Questionnaire.QuestionnaireItemComponent> itemMap = new HashMap<>();
      mapQuestionnaireItems(questionnaire.getItem(), itemMap);

      // 2. Validate all response items recursively
      validateResponseItems(response.getItem(), itemMap, ctx);
    } else if (resource instanceof Bundle bundle) {
      bundle.getEntry().forEach(entry -> validateResource(entry.getResource(), ctx));
    }
  }

  private void mapQuestionnaireItems(
      List<Questionnaire.QuestionnaireItemComponent> items,
      Map<String, Questionnaire.QuestionnaireItemComponent> itemMap) {
    for (Questionnaire.QuestionnaireItemComponent item : items) {
      itemMap.put(item.getLinkId(), item);
      if (item.hasItem()) {
        mapQuestionnaireItems(item.getItem(), itemMap);
      }
    }
  }

  /**
   * Recursively validates response items against the corresponding questionnaire items.
   *
   * @param responseItems The list of response items to validate.
   * @param itemMap The map of questionnaire items keyed by linkId.
   * @param ctx The validation context to report validation messages.
   */
  protected void validateResponseItems(
      List<QuestionnaireResponse.QuestionnaireResponseItemComponent> responseItems,
      Map<String, Questionnaire.QuestionnaireItemComponent> itemMap,
      IValidationContext<?> ctx) {
    for (QuestionnaireResponse.QuestionnaireResponseItemComponent item : responseItems) {
      Questionnaire.QuestionnaireItemComponent qItem = itemMap.get(item.getLinkId());
      if (qItem == null) continue;

      validateItemAnswer(item, qItem, ctx);

      // Recursive subitems
      if (item.hasItem()) {
        validateResponseItems(item.getItem(), itemMap, ctx);
      }
    }
  }

  protected abstract void validateItemAnswer(
      QuestionnaireResponse.QuestionnaireResponseItemComponent item,
      Questionnaire.QuestionnaireItemComponent qItem,
      IValidationContext<?> ctx);

  /**
   * Fetches a questionnaire by its URL from the internal map.
   *
   * @param questionnaireUrl The URL of the questionnaire to fetch.
   * @return The corresponding Questionnaire object, or null if not found.
   */
  private Questionnaire fetchQuestionnaire(String questionnaireUrl) {
    return questionnaireMap.get(questionnaireUrl);
  }

  /**
   * Creates a validation message with the given text.
   *
   * @param message The message text.
   * @return A SingleValidationMessage object with the given text and error severity.
   */
  protected static SingleValidationMessage createValidationMessage(String message) {
    SingleValidationMessage msg = new SingleValidationMessage();
    msg.setSeverity(ResultSeverityEnum.ERROR);
    msg.setMessage(message);
    return msg;
  }
}
