package de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.rules.ReferenceProfileRules;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.Discriminator;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SliceDef;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SlicingRoot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceProfileValidationModuleTest {

  private static final String DEMIS = "https://demis.rki.de/fhir/StructureDefinition/";
  private static final String BUNDLE_PROFILE = DEMIS + "NotificationBundleLaboratory";
  private static final String COMPOSITION_PROFILE = DEMIS + "NotificationLaboratory";
  private static final String NOTIFIED_PERSON = DEMIS + "NotifiedPerson";
  private static final String OTHER_PROFILE = DEMIS + "SomethingElse";
  private static final String PATHOGEN_DETECTION_PROFILE = DEMIS + "PathogenDetection";
  private static final String SECTION_SYSTEM =
      "https://demis.rki.de/fhir/CodeSystem/notificationSectionType";

  @Mock private IValidationContext<IBaseResource> theCtx;

  @Nested
  class UnslicedReferences {
    private ReferenceProfileRules rules;

    @BeforeEach
    void setUp() {
      rules =
          new ReferenceProfileRules(
              Set.of(BUNDLE_PROFILE),
              Map.of(COMPOSITION_PROFILE, Map.of("Composition.subject", Set.of(NOTIFIED_PERSON))),
              Map.of(BUNDLE_PROFILE, COMPOSITION_PROFILE),
              Map.of(
                  NOTIFIED_PERSON, Set.of(NOTIFIED_PERSON),
                  OTHER_PROFILE, Set.of(OTHER_PROFILE)),
              Map.of());
    }

    @Test
    void shouldSkipValidationWhenNoKnownRootProfilesClaimed() {
      Bundle bundle = new Bundle();

      validate(rules, bundle);

      verify(theCtx, never()).addValidationMessage(any());
    }

    @Test
    void shouldRejectReferenceWithDisallowedProfileUnderUnslicedPath() {
      Patient patient = patientWithProfile("p1", OTHER_PROFILE);
      Composition composition = compositionWithSubjectRef("Patient/p1");
      Bundle bundle = bundleOf(BUNDLE_PROFILE, composition, patient);

      validate(rules, bundle);

      assertErrorReported();
    }

    @Test
    void shouldAcceptReferenceWithAllowedProfileUnderUnslicedPath() {
      Patient patient = patientWithProfile("p1", NOTIFIED_PERSON);
      Composition composition = compositionWithSubjectRef("Patient/p1");
      Bundle bundle = bundleOf(BUNDLE_PROFILE, composition, patient);

      validate(rules, bundle);

      verify(theCtx, never()).addValidationMessage(any());
    }
  }

  @Nested
  class SlicedReferences {
    private ReferenceProfileRules rules;

    @BeforeEach
    void setUp() {
      SlicingRoot sectionRoot = getSectionRoot();

      rules =
          new ReferenceProfileRules(
              Set.of(BUNDLE_PROFILE),
              Map.of(),
              Map.of(BUNDLE_PROFILE, COMPOSITION_PROFILE),
              Map.of(
                  PATHOGEN_DETECTION_PROFILE, Set.of(PATHOGEN_DETECTION_PROFILE),
                  OTHER_PROFILE, Set.of(OTHER_PROFILE)),
              Map.of(COMPOSITION_PROFILE, Map.of("Composition.section", sectionRoot)));
    }

    @Test
    void shouldRejectReferenceUnderSlicedPathWhenTargetProfileNotAllowedForMatchedSlice() {
      Observation obs = observationWithProfile("o1", OTHER_PROFILE);
      Composition composition = compositionWithSectionEntry("pathogenDetection", "Observation/o1");
      Bundle bundle = bundleOf(BUNDLE_PROFILE, composition, obs);

      validate(rules, bundle);

      assertErrorReported();
    }

    @Test
    void shouldSkipReferenceUnderSlicedPathWhenNoSliceMatches() {
      Observation obs = observationWithProfile("o1", OTHER_PROFILE);
      Composition composition = compositionWithSectionEntry("unknownSectionCode", "Observation/o1");
      Bundle bundle = bundleOf(BUNDLE_PROFILE, composition, obs);

      validate(rules, bundle);

      verify(theCtx, never()).addValidationMessage(any());
    }
  }

  private static @NonNull SlicingRoot getSectionRoot() {
    SliceDef pathogenSlice =
        new SliceDef(
            "pathogenDetection",
            List.of(
                new Discriminator(
                    Discriminator.DiscriminatorType.VALUE,
                    "code.coding.code",
                    "pathogenDetection")),
            Map.of("entry", Set.of(PATHOGEN_DETECTION_PROFILE)));
    return new SlicingRoot("Composition.section", List.of(pathogenSlice), true);
  }

  // --- helpers ---

  private void validate(ReferenceProfileRules rules, IBaseResource resource) {
    when(theCtx.getResource()).thenReturn(resource);
    new ReferenceProfileValidationModule(rules).validateResource(theCtx);
  }

  private Patient patientWithProfile(String id, String profile) {
    Patient p = new Patient();
    p.setId(id);
    p.getMeta().addProfile(profile);
    return p;
  }

  private Observation observationWithProfile(String id, String profile) {
    Observation o = new Observation();
    o.setId(id);
    o.getMeta().addProfile(profile);
    return o;
  }

  private Composition compositionWithSubjectRef(String reference) {
    Composition c = new Composition();
    c.getMeta().addProfile(COMPOSITION_PROFILE);
    c.getSubject().setReference(reference);
    return c;
  }

  private Composition compositionWithSectionEntry(String sectionCode, String entryReference) {
    Composition c = new Composition();
    c.getMeta().addProfile(COMPOSITION_PROFILE);
    Composition.SectionComponent section = c.addSection();
    section.getCode().addCoding().setSystem(SECTION_SYSTEM).setCode(sectionCode);
    section.addEntry().setReference(entryReference);
    return c;
  }

  private Bundle bundleOf(String bundleProfile, Resource... resources) {
    Bundle bundle = new Bundle();
    bundle.getMeta().addProfile(bundleProfile);
    for (Resource r : resources) {
      bundle.addEntry().setResource(r);
    }
    return bundle;
  }

  private void assertErrorReported() {
    ArgumentCaptor<SingleValidationMessage> captor =
        ArgumentCaptor.forClass(SingleValidationMessage.class);
    verify(theCtx, atLeastOnce()).addValidationMessage(captor.capture());
    assertThat(captor.getAllValues()).anyMatch(m -> m.getSeverity() == ResultSeverityEnum.ERROR);
  }
}
