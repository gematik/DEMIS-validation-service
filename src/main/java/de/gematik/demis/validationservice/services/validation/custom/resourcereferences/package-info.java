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

/**
 * Validation of literal resource references in DEMIS FHIR Bundle notifications.
 *
 * <h2>Applicable specification and DEMIS policy</h2>
 *
 * <p>FHIR R4 (4.0.1) is authoritative. Where R4 leaves reference resolution undefined or ambiguous,
 * use the published FHIR R5 (5.0.0) rules as supplementary guidance, not as a replacement for an
 * explicit R4 rule. R6 rules are not currently adopted by this package.
 *
 * <p>DEMIS requires literal reference targets to be available within the notification. Unlike the
 * general FHIR resolution procedure, this validation does not retrieve missing targets from an
 * external server. An HTTP(S) reference is not necessarily external: it may identify a Bundle
 * entry. The enum name {@code EXTERNAL} describes its address form, not its resolution result.
 *
 * <h2>Resolution rules</h2>
 *
 * <p>Resolution is scoped to the direct entries of the Bundle containing the reference. Nested
 * Bundles have their own index; references are not resolved through enclosing or nested Bundles.
 * Each reference form requires its own procedure:
 *
 * <ul>
 *   <li>Relative references (for example {@code Patient/123}) are resolved against the RESTful base
 *       of the Bundle entry containing the reference, then matched to a target entry's {@code
 *       fullUrl}. This is already specified in R4. A URN {@code fullUrl} supplies no RESTful base;
 *       a matching {@code ResourceType/id} alias alone is not a valid fallback.
 *   <li>URN references (for example {@code urn:uuid:...}) are resolved by exact match with an entry
 *       {@code fullUrl}, regardless of whether the source entry has a RESTful or URN {@code
 *       fullUrl}. There is no general prohibition on mixing these address forms in a Bundle.
 *   <li>Absolute HTTP(S) references are matched against entry {@code fullUrl} values. Lookup is an
 *       exact, case-sensitive match: HTTP and HTTPS, or differently cased URL spellings, do not
 *       identify the same entry. No network retrieval is performed. Unmatched external references
 *       are subject to the configured DEMIS external-reference policy.
 *   <li>Local fragment references (for example {@code #local-id}) use the enclosing container's
 *       contained-resource scope. Sibling contained resources share that scope; other Bundle or
 *       Parameters entries do not. A reference consisting of {@code #} from a contained resource
 *       points back to its container, as explicitly illustrated in R4.
 *   <li>Version-specific references (for example {@code Patient/123/_history/2}) are resolved by
 *       matching the versionless target URL and {@code Resource.meta.versionId}.
 * </ul>
 *
 * <p>A resource's logical {@code id} is distinct from its entry's {@code fullUrl}; matching an id
 * alone does not establish that a reference resolves in the Bundle. Substring similarity between a
 * relative reference and a URN does not establish target identity either.
 *
 * <p>For relative references, the base is taken from the enclosing Bundle entry, including when the
 * reference occurs in a contained resource. Current base extraction checks URL structure but does
 * not yet strictly validate the FHIR RESTful resource-address syntax. Multiple matching entries are
 * reported as ambiguous rather than resolved by entry order.
 *
 * <h2>Implementation</h2>
 *
 * <p>{@code BundleResourceIndex} is used by the HTTP(S), URN and relative-reference validators. It
 * performs exact {@code fullUrl} lookup, resolves version-specific references against {@code
 * meta.versionId}, and throws {@code AmbiguousResourceReferenceException} when more than one entry
 * matches. The exception carries the full lookup reference, including the resolved URL for relative
 * references, and is handled centrally by the Bundle reference dispatcher. An ambiguity is reported
 * at the reference's location without stopping validation of subsequent references. Local fragment
 * references are resolved separately within the enclosing resource's contained scope: contained
 * siblings share that scope, while other Bundle entries do not.
 *
 * <p>R5 explicitly describes qualified references to contained resources in another container (for
 * example {@code Observation/123#pat}). R4 instead states that references to contained resources
 * are never resolved outside the container. Do not import this R5 extension as an assumed R4
 * omission under the precedence rule above.
 *
 * <p>Transaction-only conditional references and the R5 batch/transaction server-base fallback are
 * not notification-resolution features. Do not apply the fallback without the relevant Bundle type,
 * request method and target-server context. References without a literal address (for example
 * identifier-only references) are currently skipped; this does not assert that their identifiers
 * have been resolved.
 *
 * @see <a href="https://hl7.org/fhir/R4/bundle.html#references">R4 Bundle reference resolution</a>
 * @see <a href="https://hl7.org/fhir/R4/references.html#contained">R4 contained resources</a>
 * @see <a href="https://hl7.org/fhir/R4/references.html#regex">R4 RESTful reference syntax</a>
 * @see <a href="https://hl7.org/fhir/R5/bundle.html#references">Supplementary R5 Bundle rules</a>
 * @see <a href="https://hl7.org/fhir/R5/references.html#contained">R5 contained resources</a>
 */
package de.gematik.demis.validationservice.services.validation.custom.resourcereferences;
