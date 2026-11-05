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
import java.util.Map;
import java.util.Set;

/// Eine einzelne Slice-Variante innerhalb eines `SlicingRoot`.
///
/// Ein Element gehört zu diesem Slice, wenn *alle* Discriminatoren matchen (UND-Verknüpfung
/// gemäß FHIR). Die zugeordneten Regeln in `allowedProfilesByReferencePath`gelten
/// dann für Referenzfelder innerhalb dieses Slices.
///
/// @param sliceName                         name from the snapshot (e.g. `pathogenInformation`)
/// @param discriminators                    all discriminators of this slice context.
///                                          The slice matches only if every discriminator matches
/// @param allowedProfilesByReferencePath    path relative to the slicing root (e.g. "entry")
///                                          -> allowed target profiles for references
public record SliceDef(
    String sliceName,
    List<Discriminator> discriminators,
    Map<String, Set<String>> allowedProfilesByReferencePath) {}
