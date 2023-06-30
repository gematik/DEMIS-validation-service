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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.uhn.fhir.context.FhirContext;
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
  static void parseProfile() throws Exception {
    ProfileParserService service =
        new ProfileParserService(FhirContext.forR4(), ResourceFileConstants.PROFILE_RESOURCE_PATH);
    service.afterPropertiesSet();
    parsedProfile = service.getParseProfiles();
  }

  @Test
  void checkThereAreFourResourceMaps() {
    assertEquals(4, parsedProfile.values().size());
  }

  @Test
  void checkNumberOfCodeSystems() {
    Map<String, IBaseResource> codeSystems = parsedProfile.get(CodeSystem.class);
    assertThat(codeSystems).as("versioned and non-versioned code systems").hasSize(2 * 30);
  }

  @Test
  void checkNumberOfQuestionnaires() {
    Map<String, IBaseResource> questionnaires = parsedProfile.get(Questionnaire.class);
    assertThat(questionnaires).as("non-versioned questionnaires").hasSize(3);
  }

  @Test
  void checkNumberOfStructureDefinitions() {
    Map<String, IBaseResource> structureDefinitions = parsedProfile.get(StructureDefinition.class);
    assertThat(structureDefinitions).as("non-versioned structure definitions").hasSize(300);
  }

  @Test
  void checkNumberOfValueSets() {
    Map<String, IBaseResource> valueSets = parsedProfile.get(ValueSet.class);
    assertThat(valueSets).as("versioned and non-versioned value sets").hasSize(2 * 295);
  }
}
