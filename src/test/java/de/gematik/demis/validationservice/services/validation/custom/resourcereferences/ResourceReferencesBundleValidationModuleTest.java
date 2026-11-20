package de.gematik.demis.validationservice.services.validation.custom.resourcereferences;

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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import de.gematik.demis.validationservice.util.FileTestUtil;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests Bundle reference resolution using R4 resources and the R5 Bundle resolution rules.
 *
 * <p>In the non-transaction Bundles tested here, a relative reference is resolved using the RESTful
 * base of the {@code fullUrl} on the entry containing that reference; a URN {@code fullUrl} does
 * not provide such a base. R5 defines an additional server-base fallback for batch and transaction
 * request entries. URN references resolve by exact {@code fullUrl} match, and an absolute RESTful
 * reference can resolve by matching a Bundle entry's {@code fullUrl}. A resource's logical id and
 * its entry's {@code fullUrl} are distinct identifiers. Fragment references such as {@code #id}
 * refer to contained resources in the resource that contains the reference.
 */
@ExtendWith(MockitoExtension.class)
class ResourceReferencesBundleValidationModuleTest {

  private static final String INPUT_FILE =
      "src/test/resources/integrationtests/customValidators/input/strictCodingCheckExample.json";
  private static final String INPUT_FILE_WITH_PARAMETERS =
      "src/test/resources/integrationtests/customValidators/input/strictCodingCheckExample-Parameters.json";
  private static final String VALID_SPECIMEN_REFERENCE =
      "Specimen/4016fbc2-2439-3f6d-8935-0eb71c3d487c";

  private final FhirContext fhirContext = FhirContext.forR4();

  @Mock private IValidationContext<IBaseResource> validationContext;
  @Captor private ArgumentCaptor<SingleValidationMessage> messageCaptor;

  private ResourceReferencesBundleValidationModule validator;
  private String bundleNotification;
  private String parametersNotification;

  @BeforeEach
  void setUp() throws Exception {
    validator =
        new ResourceReferencesBundleValidationModule(
            new ResourceReferencesBundleValidationConfigProperties(List.of()));
    bundleNotification = FileTestUtil.readFileIntoString(INPUT_FILE);
    parametersNotification = FileTestUtil.readFileIntoString(INPUT_FILE_WITH_PARAMETERS);
  }

  private Bundle parseBundle(final String notification) {
    return fhirContext.newJsonParser().parseResource(Bundle.class, notification);
  }

  private void validate(final Resource resource) {
    when(validationContext.getResource()).thenReturn(resource);
    validator.validateResource(validationContext);
  }

  private List<SingleValidationMessage> capturedMessages() {
    verify(validationContext, atLeast(0)).addValidationMessage(messageCaptor.capture());
    return messageCaptor.getAllValues();
  }

  private void assertNoValidationMessages() {
    assertEquals(List.of(), capturedMessages(), "Validation messages");
  }

  private void assertValidationMessage(
      final ResultSeverityEnum expectedSeverity, final String expectedMessage) {
    List<SingleValidationMessage> messages = capturedMessages();
    assertEquals(1, messages.size(), "Number of validation messages: " + messages);
    SingleValidationMessage message = messages.getFirst();
    assertAll(
        "Validation message",
        () -> assertEquals(expectedSeverity, message.getSeverity(), "Severity"),
        () -> assertEquals(expectedMessage, message.getMessage(), "Message"));
  }

  private Bundle createBundleWithReplacedSpecimenReference(String newReference) {
    final String target = "\"reference\": \"" + VALID_SPECIMEN_REFERENCE + "\"";
    final String replacement = "\"reference\": \"" + newReference + "\"";
    final String text = bundleNotification.replace(target, replacement);
    return parseBundle(text);
  }

  @Nested
  class BundleReferences {

    @Nested
    class RelativeReferences {

      @Test
      void shouldResolveReferenceWithRestfulFullUrlOnSourceEntry() {
        Patient patient = new Patient();
        patient.setId("patient-1");

        Observation observation = new Observation();
        observation.setId("observation-1");
        observation.setSubject(new Reference("Patient/patient-1"));

        Bundle bundle = new Bundle();
        bundle
            .addEntry()
            .setFullUrl("https://example.org/fhir/Patient/patient-1")
            .setResource(patient);
        bundle
            .addEntry()
            .setFullUrl("https://example.org/fhir/Observation/observation-1")
            .setResource(observation);

        validate(bundle);

        assertNoValidationMessages();
      }

      @Test
      void shouldFailToResolveRelativeReferenceWithUrnFullUrlOnSourceEntry() {
        String reference = "Patient/patient-1";
        String sourceFullUrl = "urn:uuid:123e4567-e89b-12d3-a456-426614174001";

        Patient patient = new Patient();
        patient.setId("patient-1");

        Observation observation = new Observation();
        observation.setId("observation-1");
        observation.setSubject(new Reference(reference));

        Bundle bundle = new Bundle();
        bundle
            .addEntry()
            .setFullUrl("https://example.org/fhir/Patient/patient-1")
            .setResource(patient);
        bundle.addEntry().setFullUrl(sourceFullUrl).setResource(observation);

        validate(bundle);

        assertValidationMessage(
            ResultSeverityEnum.ERROR,
            String.format(
                "Source entry has a URN fullUrl. Unable to resolve relative reference against a RESTful base. Reference: %s BaseResourceFullUrl: %s",
                reference, sourceFullUrl));
      }

      @Test
      void shouldReportUnresolvableRelativeReference() {
        String reference = "Patient/wrong-reference";
        String resolvedFullUrl = "https://demis.rki.de/fhir/" + reference;
        final Bundle bundle = createBundleWithReplacedSpecimenReference(reference);
        validate(bundle);

        assertValidationMessage(
            ResultSeverityEnum.ERROR,
            String.format(
                "Referenced resource is missing in FHIR Bundle! Reference: %s ResolvedFullUrl: %s",
                reference, resolvedFullUrl));
      }

      @Test
      void shouldReportRelativeReferenceAsUnresolvableWhenTargetHasUrnFullUrl() {
        String patientId = "123e4567-e89b-12d3-a456-426614174000";
        String reference = "Patient/" + patientId;
        String resolvedFullUrl = "https://example.org/fhir/" + reference;
        Patient patient = new Patient();
        patient.setId(patientId);

        Observation observation = new Observation();
        observation.setId("observation-1");
        observation.setSubject(new Reference(reference));

        Bundle bundle = new Bundle();
        bundle.addEntry().setFullUrl("urn:uuid:" + patientId).setResource(patient);

        bundle
            .addEntry()
            .setFullUrl("https://example.org/fhir/Observation/observation-1")
            .setResource(observation);

        validate(bundle);

        assertValidationMessage(
            ResultSeverityEnum.ERROR,
            String.format(
                "Referenced resource is missing in FHIR Bundle! Reference: %s ResolvedFullUrl: %s",
                reference, resolvedFullUrl));
      }
    }

    @Nested
    class UrnReferences {

      @Test
      void shouldResolveReferenceMatchingFullUrlWhenTargetHasLogicalId() {
        String patientFullUrl = "urn:uuid:123e4567-e89b-12d3-a456-426614174000";
        Patient patient = new Patient();
        patient.setId("patient-1");

        Observation observation = new Observation();
        observation.setId("observation-1");
        observation.setSubject(new Reference(patientFullUrl));

        Bundle bundle = new Bundle();
        bundle.addEntry().setFullUrl(patientFullUrl).setResource(patient);
        bundle
            .addEntry()
            .setFullUrl("urn:uuid:123e4567-e89b-12d3-a456-426614174001")
            .setResource(observation);

        validate(bundle);

        assertNoValidationMessages();
      }

      @Test
      void shouldReportReferenceWithoutMatchingFullUrl() {
        String reference = "urn:uuid:123e4567-e89b-12d3-a456-426614174000";
        Observation observation = new Observation();
        observation.setSubject(new Reference(reference));

        Bundle bundle = new Bundle();
        bundle
            .addEntry()
            .setFullUrl("urn:uuid:123e4567-e89b-12d3-a456-426614174001")
            .setResource(observation);

        validate(bundle);

        assertValidationMessage(
            ResultSeverityEnum.ERROR,
            String.format(
                "Referenced resource is missing in FHIR Bundle! Reference: %s", reference));
      }
    }

    @Nested
    class AbsoluteReferences {

      @Test
      void shouldResolveReferenceMatchingRestfulFullUrl() {
        String patientFullUrl = "https://example.org/fhir/Patient/patient-1";
        Patient patient = new Patient();
        patient.setId("patient-1");

        Observation observation = new Observation();
        observation.setSubject(new Reference(patientFullUrl));

        Bundle bundle = new Bundle();
        bundle.addEntry().setFullUrl(patientFullUrl).setResource(patient);
        bundle
            .addEntry()
            .setFullUrl("https://example.org/fhir/Observation/observation-1")
            .setResource(observation);

        final var validatorWithTrustedExternalDomain =
            new ResourceReferencesBundleValidationModule(
                new ResourceReferencesBundleValidationConfigProperties(List.of("trusted.example")));
        when(validationContext.getResource()).thenReturn(bundle);
        validatorWithTrustedExternalDomain.validateResource(validationContext);

        assertNoValidationMessages();
      }

      @Test
      void shouldAcceptExternalReferenceWhenTrustedDomainListIsEmpty() {
        String reference = "https://example.com/Patient/123";
        final Bundle bundle = createBundleWithReplacedSpecimenReference(reference);
        validate(bundle);

        assertNoValidationMessages();
      }
    }

    @Nested
    class ContainedReferences {

      @Test
      void shouldResolveReferenceToContainedResource() {
        Patient containedPatient = new Patient();
        containedPatient.setId("contained-patient");
        containedPatient.setManagingOrganization(new Reference("#contained-organization"));

        Organization containedOrganization = new Organization();
        containedOrganization.setId("contained-organization");

        Observation observation = new Observation();
        observation.setId("observation-1");
        observation.addContained(containedPatient);
        observation.addContained(containedOrganization);
        observation.setSubject(new Reference("#contained-patient"));

        Bundle bundle = new Bundle();
        bundle
            .addEntry()
            .setFullUrl("urn:uuid:123e4567-e89b-12d3-a456-426614174000")
            .setResource(observation);

        validate(bundle);

        assertNoValidationMessages();
      }

      @Test
      void shouldReportUnresolvableContainedReference() {
        String reference = "#missing-patient";
        Observation observation = new Observation();
        observation.setId("observation-1");
        observation.setSubject(new Reference(reference));

        Bundle bundle = new Bundle();
        bundle
            .addEntry()
            .setFullUrl("urn:uuid:123e4567-e89b-12d3-a456-426614174000")
            .setResource(observation);

        validate(bundle);

        assertValidationMessage(
            ResultSeverityEnum.ERROR,
            String.format(
                "Referenced resource is missing in FHIR Bundle! Reference: %s", reference));
      }
    }
  }

  @Nested
  class ParametersResources {

    @Test
    void shouldResolveReferenceInParametersResource() {
      validate(fhirContext.newJsonParser().parseResource(Parameters.class, parametersNotification));

      assertNoValidationMessages();
    }
  }
}
