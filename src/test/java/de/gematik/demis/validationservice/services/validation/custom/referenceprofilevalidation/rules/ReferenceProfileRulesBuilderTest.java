package de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.rules;

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
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SliceDef;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SlicingRoot;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReferenceProfileRulesBuilderTest {
  private static final String DEMIS = "https://demis.rki.de/fhir/StructureDefinition/";
  private static final String NOTIFICATION_BUNDLE = DEMIS + "NotificationBundle";
  private static final String BUILDER_LOGGER = ReferenceProfileRulesBuilder.class.getName();

  private final ReferenceProfileRulesBuilder underTest = new ReferenceProfileRulesBuilder();
  private final SnapshotFixtures fixtures = new SnapshotFixtures();

  @Nested
  class BundleDiscoveryTests {
    @Test
    void shouldDeriveTransitivelyRootBundles() {
      String directUrl = DEMIS + "NotificationBundleDirect";
      String transitiveUrl = DEMIS + "NotificationBundleTransitive";

      var direct = bundleSd(directUrl, NOTIFICATION_BUNDLE);
      var transitive = bundleSd(transitiveUrl, directUrl);

      ReferenceProfileRules rules = underTest.build(asInput(direct, transitive));

      assertThat(rules.knownRootBundleProfiles())
          .containsExactlyInAnyOrder(directUrl, transitiveUrl);
    }

    @Test
    void shouldNotIncludeNotificationBundleItselfInWhitelist() {
      var notificationBundle =
          bundleSd(NOTIFICATION_BUNDLE, "http://hl7.org/fhir/StructureDefinition/Bundle");

      ReferenceProfileRules rules = underTest.build(asInput(notificationBundle));

      assertThat(rules.knownRootBundleProfiles()).isEmpty();
    }

    @Test
    void shouldIgnoreNonDemisBundles() {
      String foreignUrl = "http://example.org/fhir/StructureDefinition/ForeignBundle";
      var foreign = bundleSd(foreignUrl, NOTIFICATION_BUNDLE);

      ReferenceProfileRules rules = underTest.build(asInput(foreign));

      assertThat(rules.knownRootBundleProfiles()).isEmpty();
      assertThat(rules.compositionProfileByBundleProfile()).isEmpty();
      assertThat(rules.allowedProfilesByProfileAndPath()).isEmpty();
    }

    @Test
    void shouldTolerateBundleSdWithoutRelevantSnapshotContent() {
      String bundleUrl = DEMIS + "NotificationBundleLaboratory";
      var bundle = bundleSd(bundleUrl, NOTIFICATION_BUNDLE); // Snapshot ohne Composition-Slot

      ReferenceProfileRules rules = underTest.build(asInput(bundle));

      assertThat(rules.knownRootBundleProfiles()).containsExactly(bundleUrl);
      assertThat(rules.compositionProfileByBundleProfile()).isEmpty();
      assertThat(rules.allowedProfilesByProfileAndPath()).isEmpty();
    }

    @Test
    void shouldReturnEmptyRulesForEmptyInput() {
      ReferenceProfileRules rules = underTest.build(Map.of());

      assertThat(rules.knownRootBundleProfiles()).isEmpty();
      assertThat(rules.compositionProfileByBundleProfile()).isEmpty();
      assertThat(rules.allowedProfilesByProfileAndPath()).isEmpty();
      assertThat(rules.profileAncestorsIncludingSelf()).isEmpty();
    }
  }

  @Nested
  class AncestorResolutionTests {
    @Test
    void shouldDeriveDirectAncestorForDemisProfile() {
      String childUrl = DEMIS + "NotifiedPerson";
      String parentUrl = DEMIS + "NotifiedPersonBase";

      var child = profileSd(childUrl, parentUrl);
      var parent = profileSd(parentUrl, "http://hl7.org/fhir/StructureDefinition/Patient");

      ReferenceProfileRules rules = underTest.build(asInput(child, parent));
      assertThat(rules.profileAncestorsIncludingSelf())
          .containsEntry(childUrl, Set.of(childUrl, parentUrl))
          .containsEntry(parentUrl, Set.of(parentUrl));
    }

    @Test
    void shouldDeriveTransitiveAncestorsAlongDemisChain() {
      String urlA = DEMIS + "ProfileA";
      String urlB = DEMIS + "ProfileB";
      String urlC = DEMIS + "ProfileC";

      var a = profileSd(urlA, urlB);
      var b = profileSd(urlB, urlC);
      var c = profileSd(urlC, "http://hl7.org/fhir/StructureDefinition/Patient");

      ReferenceProfileRules rules = underTest.build(asInput(a, b, c));

      assertThat(rules.profileAncestorsIncludingSelf().get(urlA))
          .containsExactlyInAnyOrder(urlA, urlB, urlC);
      assertThat(rules.profileAncestorsIncludingSelf().get(urlB))
          .containsExactlyInAnyOrder(urlB, urlC);
      assertThat(rules.profileAncestorsIncludingSelf().get(urlC)).containsExactlyInAnyOrder(urlC);
    }

    @Test
    void shouldStopAncestorChainAtNonDemisBase() {
      String demisUrl = DEMIS + "NotifiedPerson";
      String hl7Patient = "http://hl7.org/fhir/StructureDefinition/Patient";

      var demisProfile = profileSd(demisUrl, hl7Patient);
      var hl7Base = profileSd(hl7Patient, "http://hl7.org/fhir/StructureDefinition/DomainResource");

      ReferenceProfileRules rules = underTest.build(asInput(demisProfile, hl7Base));

      assertThat(rules.profileAncestorsIncludingSelf()).containsOnlyKeys(demisUrl);
      assertThat(rules.profileAncestorsIncludingSelf().get(demisUrl))
          .containsExactlyInAnyOrder(demisUrl);
    }

    @Test
    void shouldWarnAndTruncateWhenDemisBaseIsMissing() {
      String childUrl = DEMIS + "ProfileA";
      String missingBaseUrl = DEMIS + "ProfileB";

      ListAppender<ILoggingEvent> logEntries = listenToLog(BUILDER_LOGGER);

      var child = profileSd(childUrl, missingBaseUrl);

      ReferenceProfileRules rules = underTest.build(asInput(child));

      assertThat(rules.profileAncestorsIncludingSelf().get(childUrl)).containsExactly(childUrl);

      assertThat(logEntries.list)
          .filteredOn(e -> e.getLevel() == Level.WARN)
          .anySatisfy(
              e -> assertThat(e.getFormattedMessage()).contains(missingBaseUrl).contains(childUrl));
    }

    @Test
    void shouldReturnSelfOnlyWhenProfileHasNoBaseDefinition() {
      String url = DEMIS + "StandaloneProfile";

      var standalone = profileSd(url, null);

      ReferenceProfileRules rules = underTest.build(asInput(standalone));

      assertThat(rules.profileAncestorsIncludingSelf()).containsEntry(url, Set.of(url));
    }

    @Test
    void shouldNotIncludeNonDemisProfileInAncestorMap() {
      String demisUrl = DEMIS + "NotifiedPerson";
      String foreignUrl = "http://example.org/fhir/StructureDefinition/ForeignProfile";

      var demisProfile = profileSd(demisUrl, foreignUrl);
      var foreignProfile = profileSd(foreignUrl, null);

      ReferenceProfileRules rules = underTest.build(asInput(demisProfile, foreignProfile));

      assertThat(rules.profileAncestorsIncludingSelf()).containsOnlyKeys(demisUrl);
    }
  }

  @Nested
  class CompositionRuleExtractionTests {
    @Test
    void shouldExtractReferenceRulesForUnslicedElements() {
      String bundleUrl = DEMIS + "NotificationBundleLaboratory";
      String compositionUrl = DEMIS + "NotificationLaboratory";
      String notifiedPersonUrl = DEMIS + "NotifiedPerson";
      String notifierRoleUrl = DEMIS + "NotifierRole";

      var bundle = bundleSd(bundleUrl, NOTIFICATION_BUNDLE, notificationEntrySlot(compositionUrl));

      var composition =
          compositionSd(
              compositionUrl,
              DEMIS + "Notification",
              referenceElement("Composition.subject", notifiedPersonUrl),
              referenceElement("Composition.author", notifierRoleUrl));

      ReferenceProfileRules rules = underTest.build(asInput(bundle, composition));

      assertThat(rules.knownRootBundleProfiles()).containsExactly(bundleUrl);
      assertThat(rules.compositionProfileByBundleProfile())
          .containsEntry(bundleUrl, compositionUrl);
      assertThat(rules.allowedProfilesByProfileAndPath()).containsOnlyKeys(compositionUrl);
      assertThat(rules.allowedProfilesByProfileAndPath().get(compositionUrl))
          .containsEntry("Composition.subject", Set.of(notifiedPersonUrl))
          .containsEntry("Composition.author", Set.of(notifierRoleUrl));
    }

    @Test
    void shouldSkipSlicedElements() {
      String bundleUrl = DEMIS + "NotificationBundleDisease";
      String compositionUrl = DEMIS + "NotificationDisease";
      String allowedProfileUrl = DEMIS + "NotifiedPerson";
      String slicedTargetUrl = DEMIS + "InfectProtectFacility";

      var bundle = bundleSd(bundleUrl, NOTIFICATION_BUNDLE, notificationEntrySlot(compositionUrl));

      ElementDefinition sliced = referenceElement("Composition.section.entry", slicedTargetUrl);
      sliced.setSliceName("diseaseInformation");

      var composition =
          compositionSd(
              compositionUrl,
              DEMIS + "Notification",
              referenceElement("Composition.subject", allowedProfileUrl),
              sliced);

      ReferenceProfileRules rules = underTest.build(asInput(bundle, composition));

      assertThat(rules.allowedProfilesByProfileAndPath().get(compositionUrl))
          .containsOnlyKeys("Composition.subject");
    }

    @Test
    void shouldKeepFirstEntryOnDuplicateRule() {
      String bundleUrl = DEMIS + "NotificationBundleLaboratory";
      String compositionUrl = DEMIS + "NotificationLaboratory";
      String firstUrl = DEMIS + "NotifiedPerson";
      String secondUrl = DEMIS + "NotifiedPersonAnonymous";

      var bundle = bundleSd(bundleUrl, NOTIFICATION_BUNDLE, notificationEntrySlot(compositionUrl));

      var composition =
          compositionSd(
              compositionUrl,
              DEMIS + "Notification",
              referenceElement("Composition.subject", firstUrl),
              referenceElement("Composition.subject", secondUrl));

      ReferenceProfileRules rules = underTest.build(asInput(bundle, composition));

      assertThat(rules.allowedProfilesByProfileAndPath().get(compositionUrl))
          .containsEntry("Composition.subject", Set.of(firstUrl));
    }
  }

  @Nested
  class SlicingTests {
    @Test
    void shouldPopulateSlicingRootsForCompositionWithSlicedSection() {
      String bundleUrl = DEMIS + "NotificationBundleLaboratory";
      String compositionUrl = DEMIS + "NotificationLaboratory";
      String pathogenReport = DEMIS + "PathogenDetectionReport";

      var bundle = bundleSd(bundleUrl, NOTIFICATION_BUNDLE, notificationEntrySlot(compositionUrl));

      var composition =
          compositionSd(
              compositionUrl,
              DEMIS + "Notification",
              fixtures.slicingRoot(
                  "Composition.section",
                  ElementDefinition.DiscriminatorType.VALUE,
                  "code.coding.code"),
              fixtures.sliceHeader("Composition.section", "pathogenDetection"),
              fixtures.fixedStringUnderSlice(
                  "Composition.section",
                  "pathogenDetection",
                  "code.coding.code",
                  "pathogenDetection"),
              fixtures.referenceUnderSlice(
                  "Composition.section", "pathogenDetection", "entry", pathogenReport));

      ReferenceProfileRules rules = underTest.build(asInput(bundle, composition));

      assertThat(rules.slicingRootsByProfileAndPath()).containsOnlyKeys(compositionUrl);
      Map<String, SlicingRoot> rootsForComposition =
          rules.slicingRootsByProfileAndPath().get(compositionUrl);
      assertThat(rootsForComposition).containsOnlyKeys("Composition.section");

      SlicingRoot section = rootsForComposition.get("Composition.section");
      assertThat(section.slices()).hasSize(1);
      SliceDef slice = section.slices().getFirst();
      assertThat(slice.sliceName()).isEqualTo("pathogenDetection");
      assertThat(slice.allowedProfilesByReferencePath())
          .containsEntry("entry", Set.of(pathogenReport));
    }
  }

  private StructureDefinition bundleSd(
      String url, String baseDefinition, ElementDefinition... snapshot) {
    return sd(url, "Bundle", baseDefinition, snapshot);
  }

  private StructureDefinition compositionSd(
      String url, String baseDefinition, ElementDefinition... snapshot) {
    return sd(url, "Composition", baseDefinition, snapshot);
  }

  private StructureDefinition sd(
      String url, String type, String baseDefinition, ElementDefinition... snapshot) {
    StructureDefinition sd = new StructureDefinition();
    sd.setUrl(url);
    sd.setType(type);
    sd.setBaseDefinition(baseDefinition);
    for (ElementDefinition ed : snapshot) {
      sd.getSnapshot().addElement(ed);
    }
    return sd;
  }

  private ElementDefinition notificationEntrySlot(String compositionProfileUrl) {
    ElementDefinition ed = new ElementDefinition();
    ed.setId("Bundle.entry:notification.resource");
    ed.setPath("Bundle.entry.resource");
    TypeRefComponent type = ed.addType();
    type.setCode("Composition");
    type.addProfile(compositionProfileUrl);
    return ed;
  }

  private ElementDefinition referenceElement(String path, String... targetProfiles) {
    ElementDefinition ed = new ElementDefinition();
    ed.setId(path);
    ed.setPath(path);
    TypeRefComponent type = ed.addType();
    type.setCode("Reference");
    for (String targetProfile : targetProfiles) {
      type.addTargetProfile(targetProfile);
    }
    return ed;
  }

  private Map<String, IBaseResource> asInput(StructureDefinition... sds) {
    Map<String, IBaseResource> map = new HashMap<>();
    for (StructureDefinition sd : sds) {
      map.put(sd.getUrl(), sd);
    }
    return map;
  }

  private StructureDefinition profileSd(String url, String baseDefinition) {
    return sd(url, "Patient", baseDefinition);
  }
}
