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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** Service that parses and stores the profile in memory. */
@Service
@Slf4j
public class ProfileParserService {

  private static final int MAX_FOLDER_DEPTH = 5;
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
      final FhirContext fhirContext,
      @Value("${demis.validation-service.profileResourcePath}") final String profileResource) {
    this.fhirContext = fhirContext;
    this.profileResource = profileResource;
  }

  private static Map<String, IBaseResource> parseFilesInDirectory(
      final IParser parser,
      final Entry<String, Class<? extends MetadataResource>> profilePart,
      final Path path)
      throws IOException {
    final var profileResources = getProfilesAsResources(path.toString());
    final var result = new HashMap<String, IBaseResource>();
    for (final var resource : profileResources) {
      final var inputStream = resource.getInputStream();
      final MetadataResource parsedResource =
          parser.parseResource(profilePart.getValue(), inputStream);
      result.put(parsedResource.getUrl(), parsedResource);
    }
    return result;
  }

  private static InputStream newInputStream(final Resource resource) {
    try {
      return resource.getInputStream();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Set<PathResource> getProfilesAsResources(final String folderPath)
      throws IOException {
    log.info("Loading profiles from folder {}", folderPath);
    try (final Stream<Path> stream = Files.walk(Paths.get(folderPath), MAX_FOLDER_DEPTH)) {
      return stream
          .filter(file -> !Files.isDirectory(file))
          .map(path -> new PathResource(path.toAbsolutePath().toString()))
          .collect(Collectors.toSet());
    }
  }

  public Map<Class<? extends MetadataResource>, Map<String, IBaseResource>> getParseProfiles() {
    if (parsedProfile != null) {
      return parsedProfile;
    }

    log.info("Start parsing Profiles");
    parsedProfile = new HashMap<>();
    final IParser parser = fhirContext.newJsonParser();
    for (final Map.Entry<String, Class<? extends MetadataResource>> profilePart :
        profileParts.entrySet()) {
      final Path path = Path.of(profileResource, profilePart.getKey());
      try {
        final Map<String, IBaseResource> resourcesMap =
            parseFilesInDirectory(parser, profilePart, path);
        parsedProfile.put(profilePart.getValue(), resourcesMap);
        log.info(String.format("Loaded %s: %s ", profilePart.getKey(), resourcesMap.size()));
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    log.info("Finish parsing Profiles");

    return parsedProfile;
  }
}
