package de.gematik.demis.validationservice.services.validation.extension;

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

import static de.gematik.demis.validationservice.services.validation.extension.ResourceWalker.ElementCtx.rootContext;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class ResourceWalker {

  private static void traverse(final ElementCtx ctx, final Consumer<ElementCtx> action) {
    action.accept(ctx);

    for (final Property property : ctx.element().children()) {
      if (property.hasValues()) {
        final List<Base> values = property.getValues();
        for (int i = 0; i < values.size(); i++) {
          final Base child = values.get(i);
          traverse(ctx.childContext(property, i, child), action);
        }
      }
    }
  }

  private static String getProfile(final IBaseResource resource) {
    String profile = null;
    final List<? extends IPrimitiveType<String>> profiles = resource.getMeta().getProfile();
    if (profiles != null && !profiles.isEmpty()) {
      profile = profiles.getFirst().getValue();
    }
    if (profile == null || profile.isBlank()) {
      // Fallback Basis-Profil (Core)
      profile = "http://hl7.org/fhir/StructureDefinition/" + resource.fhirType();
    }
    return profile;
  }

  public void forEachElementDepthFirst(final Resource resource, final Consumer<ElementCtx> action) {
    traverse(rootContext(resource), action);
  }

  // Example location path:
  // Parameters.parameter[0].resource/*Bundle/aaaaaaaaa-d599-301e-91c3-zzzzzzzzzzzzz*/.entry[7].resource/*Observation/f9bc414d-1e28-37ba-a45e-aafa832b545c*/.method.coding[0]
  // Example Resource path:
  // Patient.gender.extension

  /**
   * Fhir Element with context information
   *
   * @param locationPath the absolute fhir path, e.g.
   * @param resourcePath the relativ fhir path in the current resource
   * @param currentResource the resource this element is contained
   * @param element the fhir element itself
   * @param bundleProfile the profile of the encapsulated bundle. Can be null
   */
  record ElementCtx(
      String locationPath,
      String resourcePath,
      Resource currentResource,
      Base element,
      @Nullable String bundleProfile) {

    private static ElementCtx newResourceContext(
        final String location, final Resource resource, final String currentBundleProfile) {
      final String bundleProfile =
          currentBundleProfile == null && resource instanceof Bundle
              ? getProfile(resource)
              : currentBundleProfile;
      return new ElementCtx(location, resource.fhirType(), resource, resource, bundleProfile);
    }

    static ElementCtx rootContext(final Resource resource) {
      return newResourceContext(resource.fhirType(), resource, null);
    }

    public String getCurrentResourceProfile() {
      return getProfile(currentResource());
    }

    ElementCtx childContext(final Property property, final int index, final Base child) {
      final StringBuilder locationSegment = buildLocationSegment(property, index);
      if (child instanceof Resource resource) {
        addResourceInfo(locationSegment, resource);
        return newResourceContext(locationPath() + locationSegment, resource, bundleProfile());
      } else {
        return new ElementCtx(
            locationPath() + locationSegment,
            resourcePath() + "." + property.getName(),
            currentResource(),
            child,
            bundleProfile());
      }
    }

    private StringBuilder buildLocationSegment(final Property property, final int index) {
      final StringBuilder segment = new StringBuilder(".").append(property.getName());
      if (property.isList()) {
        segment.append("[").append(index).append("]");
      }
      return segment;
    }

    private void addResourceInfo(final StringBuilder segment, final Resource resource) {
      segment
          .append("/*")
          .append(resource.fhirType())
          .append("/")
          .append(resource.getIdPart())
          .append("*/");
    }
  }
}
