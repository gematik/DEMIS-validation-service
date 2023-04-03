/*
 * Copyright [2023], gematik GmbH
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either expressed or implied.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 */

package de.gematik.demis.validationservice.services;

import static java.util.function.Function.identity;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

/** Service that parses and stores the profile in memory. */
@Service
@Slf4j
public class ProfileParserService {
  private final Map<String, Class<? extends MetadataResource>> profileParts =
      Map.of(
          "CodeSystem", CodeSystem.class,
          "Questionnaire", Questionnaire.class,
          "StructureDefinition", StructureDefinition.class,
          "ValueSet", ValueSet.class);
  private final String profileResource;
  private final FhirContext fhirContext;
  private Map<Class<? extends MetadataResource>, Map<String, IBaseResource>> parsedProfile;

  public ProfileParserService(
      FhirContext fhirContext,
      @Value("${demis.validation-service.profileResourcePath}") String profileResource) {
    this.fhirContext = fhirContext;
    this.profileResource = profileResource;
  }

  private static Map<String, IBaseResource> parseFilesInDirectory(
      IParser parser, Entry<String, Class<? extends MetadataResource>> profilePart, Path path)
      throws IOException {
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources =
        resolver.getResources(String.format("classpath:%s/**/*.json", path.toString()));

    return Arrays.stream(resources)
        .map(ProfileParserService::newInputStream)
        .map(inputStream -> parser.parseResource(profilePart.getValue(), inputStream))
        .map(MetadataResource.class::cast)
        .collect(Collectors.toMap(MetadataResource::getUrl, identity()));
  }

  private static InputStream newInputStream(Resource resource) {
    try {
      return resource.getInputStream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Map<Class<? extends MetadataResource>, Map<String, IBaseResource>> getParseProfiles() {
    if (parsedProfile != null) {
      return parsedProfile;
    }

    log.info("Start parsing Profiles");
    parsedProfile = new HashMap<>();
    IParser parser = fhirContext.newJsonParser();
    for (Map.Entry<String, Class<? extends MetadataResource>> profilePart :
        profileParts.entrySet()) {
      Path path = Path.of(profileResource, profilePart.getKey());
      try {
        Map<String, IBaseResource> resourcesMap = parseFilesInDirectory(parser, profilePart, path);
        parsedProfile.put(profilePart.getValue(), resourcesMap);
        log.info(String.format("Loaded %s: %s ", profilePart.getKey(), resourcesMap.size()));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    log.info("Finish parsing Profiles");

    return parsedProfile;
  }
}
