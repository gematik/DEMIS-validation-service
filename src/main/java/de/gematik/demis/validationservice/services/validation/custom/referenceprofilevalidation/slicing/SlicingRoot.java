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

import java.util.List;

/// A sliced field node from the snapshot of a StructureDefinition.
///
/// Example: `Composition.section` in `NotificationLaboratory` with the slices
/// `diagnostic`, `laboratoryReport`, `pathogenDetection`
///
/// @param path fully qualified path of the slicing root (e.g. `Composition.section`)
/// @param slices ordered list of slice definitions. The order from the snapshot is preserved
///     so that slice assignment remains deterministic
/// @param closed `true` if `slicing.rules == "closed"`. Currently informational only
public record SlicingRoot(String path, List<SliceDef> slices, boolean closed) {}
