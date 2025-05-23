package de.gematik.demis.validationservice.services;

/*-
 * #%L
 * validation-service
 * %%
 * Copyright (C) 2025 gematik GmbH
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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Service;

/** Service that parses and stores the profile in memory. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileParserService {

  private static final int MAX_FOLDER_DEPTH = 5;

  private static final EnumSet<ResourceType> VERSIONED_TYPES =
      EnumSet.of(ResourceType.CodeSystem, ResourceType.ValueSet);

  private static final Map<String, Class<? extends MetadataResource>> PROFILE_PARTS =
      java.util.Map.of(
          "CodeSystem", CodeSystem.class,
          "Questionnaire", Questionnaire.class,
          "StructureDefinition", StructureDefinition.class,
          "ValueSet", ValueSet.class);

  private final FhirContext fhirContext;

  private static Map<String, IBaseResource> parseFilesInDirectory(
      final IParser parser,
      final Entry<String, Class<? extends MetadataResource>> profilePart,
      final Path path)
      throws IOException {
    final var profileResources = getProfilesAsResources(path);
    final var result = new HashMap<String, IBaseResource>();
    for (final var resource : profileResources) {
      parseProfileResource(parser, profilePart, result, resource);
    }
    return result;
  }

  private static void parseProfileResource(
      IParser parser,
      Entry<String, Class<? extends MetadataResource>> profilePart,
      HashMap<String, IBaseResource> result,
      PathResource resource)
      throws IOException {
    final MetadataResource parsedResource =
        parser.parseResource(profilePart.getValue(), resource.getInputStream());
    final String url = parsedResource.getUrl();
    result.put(url, parsedResource);
    if (versionedLookup(parsedResource)) {
      result.put(url + "|" + parsedResource.getVersion(), parsedResource);
    }
  }

  /**
   * With FHIR snapshot 09.05.2023 code systems and value sets can be looked up old style without
   * version suffix <b>and</b> with the newly added version suffix.
   *
   * @param resource FHIR profile resource
   * @return <code>true</code> if versioned lookup has to be supported, <code>false</code> if
   *     versioned lookup will not be performed on runtime
   */
  private static boolean versionedLookup(MetadataResource resource) {
    return VERSIONED_TYPES.contains(resource.getResourceType());
  }

  private static Set<PathResource> getProfilesAsResources(final Path folderPath)
      throws IOException {
    log.info("Loading profiles from folder {}", folderPath);
    if (!Files.exists(folderPath)) {
      log.warn("Folder {} not present", folderPath);

      return Collections.emptySet();
    }
    try (final Stream<Path> stream = Files.walk(folderPath, MAX_FOLDER_DEPTH)) {
      return stream
          .filter(file -> !Files.isDirectory(file))
          .map(path -> new PathResource(path.toAbsolutePath().toString()))
          .collect(Collectors.toSet());
    }
  }

  public Map<Class<? extends MetadataResource>, Map<String, IBaseResource>> parseProfile(
      final Path profileSnapshotsPath) {
    log.info("Start parsing Profiles {}", profileSnapshotsPath);
    if (!Files.exists(profileSnapshotsPath)) {
      throw new IllegalArgumentException("Profiles path " + profileSnapshotsPath + " not present");
    }
    final Map<Class<? extends MetadataResource>, Map<String, IBaseResource>> profile =
        new HashMap<>();
    final IParser parser = fhirContext.newJsonParser();
    for (final Entry<String, Class<? extends MetadataResource>> profilePart :
        PROFILE_PARTS.entrySet()) {
      final Path path = profileSnapshotsPath.resolve(profilePart.getKey());
      try {
        final Map<String, IBaseResource> resourcesMap =
            parseFilesInDirectory(parser, profilePart, path);
        profile.put(profilePart.getValue(), ImmutableMap.copyOf(resourcesMap));
        log.info("Loaded {}: {} ", profilePart.getKey(), resourcesMap.size());
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    if (profile.values().stream().allMatch(Map::isEmpty)) {
      throw new IllegalStateException("No profiles files found in " + profileSnapshotsPath);
    }

    log.info("Finish parsing Profiles {}", profileSnapshotsPath);
    return Collections.unmodifiableMap(profile);
  }
}
