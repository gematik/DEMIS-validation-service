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
import de.gematik.demis.validationservice.services.ProfileSnapshot.ProfileSnapshotBuilder;
import de.gematik.demis.validationservice.services.terminology.remote.TerminologyServerConfigProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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

  public static final int MAX_FOLDER_DEPTH = 5;
  public static final String FOLDER_CODESYSTEM = "CodeSystem";
  public static final String FOLDER_VALUE_SET = "ValueSet";
  private static final String FOLDER_STRUCTURE_DEFINITION = "StructureDefinition";
  private static final String FOLDER_QUESTIONNAIRE = "Questionnaire";

  private static final EnumSet<ResourceType> VERSIONED_TYPES =
      EnumSet.of(ResourceType.CodeSystem, ResourceType.ValueSet);

  private final FhirContext fhirContext;
  private final TerminologyServerConfigProperties terminologyServerConfigProperties;

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

  public ProfileSnapshot parseProfile(final Path profileSnapshotsPath) {
    log.info("Start parsing Profiles {}", profileSnapshotsPath);
    if (!Files.exists(profileSnapshotsPath)) {
      throw new IllegalArgumentException("Profiles path " + profileSnapshotsPath + " not present");
    }

    final ProfileSnapshotLoader loader = new ProfileSnapshotLoader(profileSnapshotsPath);

    final ProfileSnapshotBuilder profileSnapshotBuilder = ProfileSnapshot.builder();
    profileSnapshotBuilder
        .name(profileSnapshotsPath.getFileName().toString())
        .structureDefinitions(
            loader.loadResources(StructureDefinition.class, FOLDER_STRUCTURE_DEFINITION))
        .questionnaires(loader.loadResources(Questionnaire.class, FOLDER_QUESTIONNAIRE));
    if (terminologyServerConfigProperties.enabled()) {
      profileSnapshotBuilder
          .withTerminologyResources(false)
          .codeSystems(Map.of())
          .valueSets(Map.of());
    } else {
      profileSnapshotBuilder
          .withTerminologyResources(true)
          .codeSystems(loader.loadResources(CodeSystem.class, FOLDER_CODESYSTEM))
          .valueSets(loader.loadResources(ValueSet.class, FOLDER_VALUE_SET));
    }

    final ProfileSnapshot profileSnapshot = profileSnapshotBuilder.build();

    if (profileSnapshot.getTotalCount() == 0) {
      throw new IllegalStateException("No profiles files found in " + profileSnapshotsPath);
    }

    log.info("Snapshot loading finished: {}", profileSnapshot);
    return profileSnapshot;
  }

  @RequiredArgsConstructor
  class ProfileSnapshotLoader {
    final IParser parser = fhirContext.newJsonParser();
    final Path profileSnapshotsPath;

    public Map<String, IBaseResource> loadResources(
        final Class<? extends MetadataResource> resourceType, final String folder) {
      try {
        final Path path = profileSnapshotsPath.resolve(folder);
        final var profileResources = getProfilesAsResources(path);
        final var result = new HashMap<String, IBaseResource>();
        for (final var resource : profileResources) {
          parseProfileResource(resourceType, result, resource);
        }
        log.info("Loaded {}: {} ", folder, result.size());
        return Map.copyOf(result);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    private void parseProfileResource(
        Class<? extends MetadataResource> resourceType,
        Map<String, IBaseResource> result,
        PathResource resource)
        throws IOException {
      final MetadataResource parsedResource =
          parser.parseResource(resourceType, resource.getInputStream());
      final String url = parsedResource.getUrl();
      result.put(url, parsedResource);
      if (versionedLookup(parsedResource)) {
        result.put(url + "|" + parsedResource.getVersion(), parsedResource);
      }
    }
  }
}
