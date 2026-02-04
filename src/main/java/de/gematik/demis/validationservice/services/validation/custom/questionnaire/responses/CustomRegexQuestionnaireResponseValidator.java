package de.gematik.demis.validationservice.services.validation.custom.questionnaire.responses;

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

import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.IValidatorModule;
import ca.uhn.fhir.validation.SingleValidationMessage;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.StringType;

/**
 * Custom validator for validating FHIR QuestionnaireResponses using regex patterns. Implements the
 * IValidatorModule interface to validate resources.
 */
public class CustomRegexQuestionnaireResponseValidator
    extends AbstractCustomQuestionnaireResponseValidator implements IValidatorModule {

  /**
   * Constructor to initialize the validator with a map of questionnaires.
   *
   * @param questionnaireMap A map of questionnaire URLs to IBaseResource objects. Only
   *     Questionnaire resources are retained.
   */
  public CustomRegexQuestionnaireResponseValidator(Map<String, IBaseResource> questionnaireMap) {
    super(questionnaireMap);
  }

  protected void validateItemAnswer(
      QuestionnaireResponse.QuestionnaireResponseItemComponent item,
      Questionnaire.QuestionnaireItemComponent qItem,
      IValidationContext<?> ctx) {

    String regex = getRegexFromExtensions(qItem.getExtension());
    if (regex != null) {
      for (QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent answer :
          item.getAnswer()) {
        String value = null;
        // check StringType
        if (answer.getValue() instanceof StringType stringType) {
          value = stringType.getValue();
        }
        // check DateType
        else if (answer.getValue() instanceof DateType dateType) {
          value = dateType.getValueAsString();
        }
        if (value != null && !Pattern.matches(regex, value)) {
          SingleValidationMessage msg =
              createValidationMessage(
                  "Answer for item " + item.getLinkId() + " does not match the regex: " + regex);
          ctx.addValidationMessage(msg);
        }
      }
    }
  }

  /**
   * Extracts a regex pattern from the extensions of a questionnaire item.
   *
   * @param extensions The list of extensions to search for a regex pattern.
   * @return The regex pattern as a string, or null if not found.
   */
  private String getRegexFromExtensions(List<Extension> extensions) {
    for (Extension ext : extensions) {
      if (ext.getUrl().equals("http://hl7.org/fhir/StructureDefinition/questionnaire-regex")) {
        return ext.getValueAsPrimitive().getValueAsString();
      }
    }
    return null;
  }
}
