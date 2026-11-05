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

/// A discriminator according to FHIR slicing rules:
/// only `value` and `pattern`, simple dot paths, pattern as a serialized
/// tree structure (not a HAPI type).
///
/// @param type          `VALUE` = exact primitive/structure match of the full value;
///                      `PATTERN` = recursive "is-subset" match
/// @param path          simple dot-path chain relative to the sliced element
///                      (e.g. `code.coding`, `url`)
/// @param expected      expected value / pattern tree in normalized form:
///                      primitives as String/Number/Boolean, composites as
///                      `Map<String,Object>`, arrays as `List<Object>`
public record Discriminator(DiscriminatorType type, String path, Object expected) {

  public enum DiscriminatorType {
    VALUE,
    PATTERN
  }
}
