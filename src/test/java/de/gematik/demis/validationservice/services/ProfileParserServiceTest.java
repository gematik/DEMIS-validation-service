/*
 * Copyright [2023], gematik GmbH
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either expressed or implied.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 */

package de.gematik.demis.validationservice.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.gematik.demis.validationservice.util.ResourceFileConstants;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProfileParserServiceTest {

  private static Map<Class<? extends MetadataResource>, Map<String, IBaseResource>> parsedProfile;

  @BeforeAll
  static void parseProfile() {
    FhirContextService fhirContextService = new FhirContextService("en_US");
    ProfileParserService service =
        new ProfileParserService(fhirContextService, ResourceFileConstants.PROFILE_RESOURCE_PATH);
    parsedProfile = service.getParseProfiles();
  }

  @Test
  void checkThereAreFourResourceMaps() {
    assertEquals(4, parsedProfile.values().size());
  }

  @Test
  void checkThereAre31CodeSystems() {
    Map<String, IBaseResource> codeSystems = parsedProfile.get(CodeSystem.class);
    assertEquals(32, codeSystems.values().size());
  }

  @Test
  void checkThereIsThreeQuestionnaire() {
    Map<String, IBaseResource> questionnaires = parsedProfile.get(Questionnaire.class);
    assertEquals(3, questionnaires.values().size());
  }

  @Test
  void checkThereAre255StructureDefinitions() {
    Map<String, IBaseResource> structureDefinitions = parsedProfile.get(StructureDefinition.class);
    assertEquals(255, structureDefinitions.values().size());
  }

  @Test
  void checkThereAre207ValueSets() {
    Map<String, IBaseResource> valueSets = parsedProfile.get(ValueSet.class);
    assertEquals(209, valueSets.values().size());
  }
}
