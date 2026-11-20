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

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Reference;

/** Classifies literal reference address forms; does not validate syntax or resolve targets. */
enum ReferenceType {

  /** URN reference, e.g. {@code urn:uuid:...} or {@code urn:oid:...}. */
  URN,

  /** Relative reference in RESTful form, e.g. {@code Patient/123}, requiring a base URL. */
  RELATIVE,

  /** Local fragment reference within a resource's contained scope, e.g. {@code #patient}. */
  CONTAINED,

  /** Absolute HTTP(S) reference; the target may still be an entry in the same Bundle. */
  EXTERNAL,

  /**
   * Missing Reference object or absent literal address; identifier-only references are included.
   */
  EMPTY,

  /** Literal address not recognized as any of the supported reference forms. */
  UNKNOWN;

  static @Nonnull ReferenceType from(Reference reference) {
    if ((reference == null) || !reference.hasReference()) {
      return EMPTY;
    }
    return from(reference.getReference());
  }

  static @Nonnull ReferenceType from(String reference) {
    final String ref = reference.toLowerCase();
    if (ref.startsWith("#")) {
      return CONTAINED;
    } else if (ref.startsWith("urn:")) {
      return URN;
    } else if (ref.startsWith("http://") || ref.startsWith("https://")) {
      return EXTERNAL;
    }
    if (isRelativeReference(ref)) {
      return RELATIVE;
    }
    return UNKNOWN;
  }

  private static boolean isRelativeReference(String reference) {
    final int indexOfSlash = reference.indexOf('/');
    if (indexOfSlash > 0) {
      final String preSlash = reference.substring(0, indexOfSlash);
      return StringUtils.isAlpha(preSlash);
    }
    return false;
  }
}
