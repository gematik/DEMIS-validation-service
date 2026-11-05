package de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing;

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

import static de.gematik.demis.validationservice.test.LogListener.listenToLog;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.SnapshotFixtures;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.ElementDefinition.DiscriminatorType;
import org.hl7.fhir.r4.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.Test;

class SliceExtractorTest {
  private static final String DEMIS = "https://demis.rki.de/fhir/StructureDefinition/";
  private static final String EXTRACTOR_LOGGER = SliceExtractor.class.getName();

  private final SliceExtractor underTest = new SliceExtractor();
  private final SnapshotFixtures snapshotFixtures = new SnapshotFixtures();

  @Test
  void shouldReturnEmptyMapWhenSnapshotHasNoSlicings() {
    var sd =
        compositionSd(
            DEMIS + "NotificationLaboratory",
            simpleReference("Composition.subject", DEMIS + "NotifiedPerson"));

    Map<String, SlicingRoot> result = underTest.extract(sd);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldExtractSliceWithValueDiscriminatorAndReferenceRule() {
    String compositionUrl = DEMIS + "NotificationLaboratory";
    String pathogenReport = DEMIS + "PathogenDetectionReport";

    var sd =
        compositionSd(
            compositionUrl,
            snapshotFixtures.slicingRoot(
                "Composition.section", DiscriminatorType.VALUE, "code.coding.code"),
            snapshotFixtures.sliceHeader("Composition.section", "pathogenDetection"),
            snapshotFixtures.fixedStringUnderSlice(
                "Composition.section",
                "pathogenDetection",
                "code.coding.code",
                "pathogenDetection"),
            snapshotFixtures.referenceUnderSlice(
                "Composition.section", "pathogenDetection", "entry", pathogenReport));

    Map<String, SlicingRoot> result = underTest.extract(sd);

    assertThat(result).containsOnlyKeys("Composition.section");
    SlicingRoot root = result.get("Composition.section");
    assertThat(root.slices()).hasSize(1);
    SliceDef slice = root.slices().getFirst();
    assertThat(slice.sliceName()).isEqualTo("pathogenDetection");
    assertThat(slice.discriminators()).hasSize(1);
    Discriminator d = slice.discriminators().getFirst();
    assertThat(d.type()).isEqualTo(Discriminator.DiscriminatorType.VALUE);
    assertThat(d.path()).isEqualTo("code.coding.code");
    assertThat(d.expected()).isEqualTo("pathogenDetection");
    assertThat(slice.allowedProfilesByReferencePath())
        .containsEntry("entry", Set.of(pathogenReport));
  }

  @Test
  void shouldWarnAndSkipUnsupportedDiscriminatorType() {
    String compositionUrl = DEMIS + "NotificationExample";
    ListAppender<ILoggingEvent> logEntries = listenToLog(EXTRACTOR_LOGGER);

    var sd =
        compositionSd(
            compositionUrl,
            snapshotFixtures.slicingRoot(
                "Composition.example", DiscriminatorType.PROFILE, "resolve()"));

    Map<String, SlicingRoot> result = underTest.extract(sd);

    assertThat(result).isEmpty();
    assertThat(logEntries.list)
        .filteredOn(e -> e.getLevel() == Level.WARN)
        .anySatisfy(
            e ->
                assertThat(e.getFormattedMessage())
                    .contains("Skipping slicing")
                    .contains("PROFILE")
                    .contains(compositionUrl));
  }

  private StructureDefinition compositionSd(String url, ElementDefinition... snapshot) {
    StructureDefinition sd = new StructureDefinition();
    sd.setUrl(url);
    sd.setType("Composition");
    for (ElementDefinition ed : snapshot) {
      sd.getSnapshot().addElement(ed);
    }
    return sd;
  }

  private ElementDefinition simpleReference(String path, String targetProfile) {
    ElementDefinition ed = new ElementDefinition();
    ed.setId(path);
    ed.setPath(path);
    TypeRefComponent type = ed.addType();
    type.setCode("Reference");
    type.addTargetProfile(targetProfile);
    return ed;
  }
}
