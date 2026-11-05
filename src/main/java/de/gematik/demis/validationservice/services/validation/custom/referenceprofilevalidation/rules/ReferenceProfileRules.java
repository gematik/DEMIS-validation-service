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

import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SliceDef;
import de.gematik.demis.validationservice.services.validation.custom.referenceprofilevalidation.slicing.SlicingRoot;
import java.util.Map;
import java.util.Set;

/// Immutable snapshot of precomputed reference-profile validation rules derived from
/// StructureDefinition snapshots at startup.
///
/// The runtime validator uses this record as a read-only lookup table and does not
/// inspect raw StructureDefinitions anymore.
///
/// The rules are organized around two orthogonal axes:
/// - the *root axis* identifies which bundle/composition combination applies to a
///   given payload (see `knownRootBundleProfiles` and
///   `compositionProfileByBundleProfile`),
/// - the *rule axis* provides the allowed target profiles for each reference field,
///   either unsliced (`allowedProfilesByProfileAndPath`) or per slice within a
///   sliced field (`slicingRootsByProfileAndPath`).
///
/// @param knownRootBundleProfiles       DEMIS bundle profiles that are eligible as
///                                      validation roots
/// @param allowedProfilesByProfileAndPath
///                                      mapping: composition profile URL -> (FHIR
///                                      element path -> allowed target profile URLs).
///                                      Covers *unsliced* reference fields only
/// @param compositionProfileByBundleProfile
///                                      mapping: root bundle profile URL -> composition
///                                      profile URL at
///                                      `Bundle.entry:notification.resource`
/// @param profileAncestorsIncludingSelf mapping: profile URL -> transitive DEMIS
///                                      ancestor set including the profile itself,
///                                      used for derived-profile checks
/// @param slicingRootsByProfileAndPath  mapping: composition profile URL -> (slicing
///                                      root path -> [SlicingRoot]). The runtime
///                                      matcher consults this map when a reference
///                                      field sits under a sliced parent, and applies
///                                      the slice-specific rules from the matching
///                                      [SliceDef]
public record ReferenceProfileRules(
    Set<String> knownRootBundleProfiles,
    Map<String, Map<String, Set<String>>> allowedProfilesByProfileAndPath,
    Map<String, String> compositionProfileByBundleProfile,
    Map<String, Set<String>> profileAncestorsIncludingSelf,
    Map<String, Map<String, SlicingRoot>> slicingRootsByProfileAndPath) {}
