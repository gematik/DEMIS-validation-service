package de.gematik.demis.validationservice.services.validation.custom.profile;

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

import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.IValidatorModule;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StructureDefinition;

/**
 * Custom validator module that checks whether any profile URL declared in {@code meta.profile} of a
 * resource instance refers to an abstract {@link StructureDefinition}. Abstract profiles are not
 * intended to be used directly on instances and such a declaration is treated as an error.
 */
public class AbstractProfileValidator implements IValidatorModule {

  static final String MESSAGE_ID = "Abstract_Profile_In_Meta";

  private final Map<String, StructureDefinition> structureDefinitionMap;

  /**
   * Constructs the validator with the pre-loaded StructureDefinitions of the active profile
   * version.
   *
   * @param structureDefinitions map of canonical URL → {@link IBaseResource} (StructureDefinition)
   */
  public AbstractProfileValidator(Map<String, IBaseResource> structureDefinitions) {
    Map<String, StructureDefinition> tmp = new HashMap<>();
    structureDefinitions.forEach(
        (key, value) -> {
          if (value instanceof StructureDefinition sd) {
            tmp.put(key, sd);
          }
        });
    this.structureDefinitionMap = Collections.unmodifiableMap(tmp);
  }

  @Override
  public void validateResource(IValidationContext<IBaseResource> ctx) {
    IBaseResource resource = ctx.getResource();
    if (resource instanceof Parameters parameters) {
      parameters.getParameter().stream()
          .filter(p -> p.getResource() instanceof Bundle)
          .forEach(p -> validateBundle((Bundle) p.getResource(), ctx));
    } else if (resource instanceof Bundle bundle) {
      validateBundle(bundle, ctx);
    } else {
      checkMetaProfiles(resource, ctx);
    }
  }

  private void validateBundle(Bundle bundle, IValidationContext<IBaseResource> ctx) {
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() != null) {
        checkMetaProfiles(entry.getResource(), ctx);
      }
    }
  }

  private void checkMetaProfiles(IBaseResource resource, IValidationContext<IBaseResource> ctx) {
    if (resource.getMeta() == null) {
      return;
    }
    List<? extends IPrimitiveType<String>> profiles = resource.getMeta().getProfile();
    if (profiles == null || profiles.isEmpty()) {
      return;
    }
    for (IPrimitiveType<String> profileRef : profiles) {
      String profileUrl = profileRef.getValueAsString();
      if (profileUrl == null || profileUrl.isBlank()) {
        continue;
      }
      StructureDefinition sd = structureDefinitionMap.get(profileUrl);
      if (sd != null && sd.getAbstract()) {
        addError(ctx, profileUrl, resource);
      }
    }
  }

  private void addError(
      IValidationContext<IBaseResource> ctx, String profileUrl, IBaseResource resource) {
    SingleValidationMessage msg = new SingleValidationMessage();
    msg.setSeverity(ResultSeverityEnum.ERROR);
    msg.setMessageId(MESSAGE_ID);
    msg.setLocationString(resource.fhirType() + ".meta.profile");
    msg.setMessage(
        String.format(
            "The profile '%s' is marked as abstract and cannot be used to directly validate or instantiate a resource. Please use a derived one.",
            profileUrl));
    ctx.addValidationMessage(msg);
  }
}
