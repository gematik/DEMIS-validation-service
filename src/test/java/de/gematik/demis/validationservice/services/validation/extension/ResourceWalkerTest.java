package de.gematik.demis.validationservice.services.validation.extension;

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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.validationservice.services.validation.extension.ResourceWalker.ElementCtx;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

@Slf4j
class ResourceWalkerTest {
  private final ResourceWalker underTest = new ResourceWalker();

  @Test
  void forEachElementDepthFirst_bundleWithPatient() {
    final Parameters parameters = new Parameters();
    final Bundle bundle = new Bundle();

    final String bundleProfile =
        "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory";
    bundle.getMeta().addProfile(bundleProfile);
    parameters.addParameter().setName("content").setResource(bundle);
    bundle.setId("abc");
    final Patient patient = new Patient();
    bundle.addEntry().setResource(patient);

    patient.getMeta().addProfile("https://demis.rki.de/fhir/StructureDefinition/NotifiedPerson");

    final Extension extension0 =
        new Extension("http://demis.de/test/my-extension", new StringType("test"));
    final Extension extension1 =
        new Extension("http://demis.de/test/my-extension-2", new StringType("test-2"));
    patient
        .getBirthDateElement()
        .setValue(new Date())
        .addExtension(extension0)
        .addExtension(extension1);

    final List<ElementCtx> list = new ArrayList<>();
    underTest.forEachElementDepthFirst(parameters, list::add);

    list.forEach(ctx -> log.info(ctx.toString()));

    list.forEach(
        ctx ->
            log.info(
                ctx.locationPath()
                    + ", "
                    + ctx.resourcePath()
                    + ", "
                    + ctx.currentResource().fhirType()
                    + ", "
                    + ctx.element().fhirType()
                    + ", "
                    + ctx.bundleProfile()));

    list.forEach(
        ctx ->
            log.info(
                "new ElementCtx(\""
                    + ctx.locationPath()
                    + "\", \""
                    + ctx.resourcePath()
                    + "\", "
                    + ctx.currentResource().fhirType()
                    + ", "
                    + ctx.element().fhirType()
                    + ", \""
                    + ctx.bundleProfile()
                    + "\"),"));

    final List<ElementCtx> expected =
        List.of(
            new ElementCtx("Parameters", "Parameters", parameters, parameters, null),
            new ElementCtx(
                "Parameters.parameter[0]",
                "Parameters.parameter",
                parameters,
                parameters.getParameter().get(0),
                null),
            new ElementCtx(
                "Parameters.parameter[0].name",
                "Parameters.parameter.name",
                parameters,
                parameters.getParameter().get(0).getNameElement(),
                null),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/",
                "Bundle",
                bundle,
                bundle,
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.id",
                "Bundle.id",
                bundle,
                bundle.getIdElement(),
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.meta",
                "Bundle.meta",
                bundle,
                bundle.getMeta(),
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.meta.profile[0]",
                "Bundle.meta.profile",
                bundle,
                bundle.getMeta().getProfile().getFirst(),
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.entry[0]",
                "Bundle.entry",
                bundle,
                bundle.getEntry().getFirst(),
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.entry[0].resource/*Patient/null*/",
                "Patient",
                patient,
                patient,
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.entry[0].resource/*Patient/null*/.id",
                "Patient.id",
                patient,
                patient.getIdElement(),
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.entry[0].resource/*Patient/null*/.meta",
                "Patient.meta",
                patient,
                patient.getMeta(),
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.entry[0].resource/*Patient/null*/.meta.profile[0]",
                "Patient.meta.profile",
                patient,
                patient.getMeta().getProfile().getFirst(),
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.entry[0].resource/*Patient/null*/.birthDate",
                "Patient.birthDate",
                patient,
                patient.getBirthDateElement(),
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.entry[0].resource/*Patient/null*/.birthDate.extension[0]",
                "Patient.birthDate.extension",
                patient,
                extension0,
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.entry[0].resource/*Patient/null*/.birthDate.extension[0].url",
                "Patient.birthDate.extension.url",
                patient,
                extension0.getUrlElement(),
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.entry[0].resource/*Patient/null*/.birthDate.extension[0].value[x]",
                "Patient.birthDate.extension.value[x]",
                patient,
                extension0.getValue(),
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.entry[0].resource/*Patient/null*/.birthDate.extension[1]",
                "Patient.birthDate.extension",
                patient,
                extension1,
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.entry[0].resource/*Patient/null*/.birthDate.extension[1].url",
                "Patient.birthDate.extension.url",
                patient,
                extension1.getUrlElement(),
                bundleProfile),
            new ElementCtx(
                "Parameters.parameter[0].resource/*Bundle/abc*/.entry[0].resource/*Patient/null*/.birthDate.extension[1].value[x]",
                "Patient.birthDate.extension.value[x]",
                patient,
                extension1.getValue(),
                bundleProfile));

    assertThat(list).containsExactlyInAnyOrderElementsOf(expected);
  }
}
