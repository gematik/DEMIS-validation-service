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

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.validationservice.services.ProfileSnapshot;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Questionnaire;

/**
 * Custom IValidationSupport that extends {@link PrePopulatedValidationSupport} for {@link
 * Questionnaire}s.
 */
class DemisPrePopulatedValidationSupportHapi4 extends PrePopulatedValidationSupport {

  private final Map<String, IBaseResource> myQuestionnaireSets;

  DemisPrePopulatedValidationSupportHapi4(
      final FhirContext context, final ProfileSnapshot profileSnapshot) {
    super(
        context,
        profileSnapshot.structureDefinitions(),
        profileSnapshot.valueSets(),
        profileSnapshot.codeSystems());
    this.myQuestionnaireSets = profileSnapshot.questionnaires();
  }

  @Override
  public List<IBaseResource> fetchAllConformanceResources() {
    List<IBaseResource> retVal = super.fetchAllConformanceResources();
    retVal.addAll(myQuestionnaireSets.values());
    return retVal;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends IBaseResource> T fetchResource(@Nullable Class<T> theClass, String theUri) {
    T resource = super.fetchResource(theClass, theUri);

    if (resource == null && Questionnaire.class.equals(theClass)) {
      return (T) myQuestionnaireSets.get(theUri);
    }

    return resource;
  }
}
