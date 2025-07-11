package de.gematik.demis.validationservice.services.terminology.remote;

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

import static de.gematik.demis.validationservice.util.ResourceFileConstants.TERMINOLOGY_PROFILES_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TerminologyVersionsLoaderTest {

  private final TerminologyVersionsLoader underTest = new TerminologyVersionsLoader();

  @Test
  void loadTerminologyProfiles() {
    final TerminologyVersions terminologyVersions =
        underTest.loadTerminologyVersions(TERMINOLOGY_PROFILES_PATH);

    final TerminologyVersions expected =
        new TerminologyVersions(
            Map.of(
                "https://demis.rki.de/fhir/ValueSet/mytest", "2.0.2",
                "https://demis.rki.de/fhir/ValueSet/yesOrNoAnswer", "1.1.2",
                "https://demis.rki.de/fhir/ValueSet/answerSetHospitalizationReason", "2.3.5"),
            Map.of(
                "https://demis.rki.de/fhir/CodeSystem/myTestSystem", "3.0.0",
                "https://demis.rki.de/fhir/CodeSystem/yesOrNoAnswer", "1.0.1",
                "https://demis.rki.de/fhir/CodeSystem/hospitalizationReason", "2.3.0"));

    assertThat(terminologyVersions).usingRecursiveComparison().isEqualTo(expected);
  }
}
