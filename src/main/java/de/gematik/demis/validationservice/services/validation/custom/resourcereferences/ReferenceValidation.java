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

import de.gematik.demis.validationservice.services.validation.custom.ResourceWalker;
import org.hl7.fhir.r4.model.Reference;

/**
 * Stateful validation of a resource reference within a DEMIS FHIR bundle resource. The FHIR bundle
 * context already has been provided on object creation.
 */
interface ReferenceValidation {

  /**
   * Validates resource reference and adds validation messages.
   *
   * @param reference reference of a resource
   * @param context resource walker context of given resource reference to acquire the base resource
   *     or other metadata
   * @throws BundleResourceIndex.AmbiguousResourceReferenceException if multiple Bundle entries
   *     match the reference; handled centrally by the Bundle reference dispatcher
   */
  void validate(Reference reference, ResourceWalker.ElementCtx context)
      throws BundleResourceIndex.AmbiguousResourceReferenceException;
}
