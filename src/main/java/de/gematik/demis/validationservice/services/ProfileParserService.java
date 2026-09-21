package de.gematik.demis.validationservice.services;

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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.validationservice.services.ProfileSnapshot.ProfileSnapshotBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

/** Service that parses and stores the profile in memory. */
@Service
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
  private final CodeSystemConsolidator codeSystemConsolidator;
  private final boolean featureFlagFhirPackagePostprocessing;

  @Autowired
  ProfileParserService(
      FhirContext fhirContext,
      CodeSystemConsolidator codeSystemConsolidator,
      @Value("${feature.flag.fhir.package.postprocessing}")
          boolean featureFlagFhirPackagePostprocessing) {
    this.fhirContext = fhirContext;
    this.codeSystemConsolidator = codeSystemConsolidator;
    this.featureFlagFhirPackagePostprocessing = featureFlagFhirPackagePostprocessing;
  }

  ProfileParserService(FhirContext fhirContext, CodeSystemConsolidator codeSystemConsolidator) {
    this(fhirContext, codeSystemConsolidator, false);
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

  private static List<FileSystemResource> getProfilesAsResources(final Path folderPath)
      throws IOException {
    log.info("Loading profiles from folder {}", folderPath);
    if (!Files.exists(folderPath)) {
      log.warn("Folder {} not present", folderPath);

      return Collections.emptyList();
    }
    try (final Stream<Path> stream = Files.walk(folderPath, MAX_FOLDER_DEPTH)) {
      return stream
          .filter(file -> !Files.isDirectory(file))
          .map(Path::toAbsolutePath)
          .sorted()
          .map(path -> new FileSystemResource(path.toAbsolutePath().toString()))
          .toList();
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
    profileSnapshotBuilder
        .withTerminologyResources(true)
        .codeSystems(loader.loadResources(CodeSystem.class, FOLDER_CODESYSTEM))
        .valueSets(loader.loadResources(ValueSet.class, FOLDER_VALUE_SET));

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
        List<? extends MetadataResource> resourcesForLookup =
            parseProfileResources(resourceType, path);

        if (CodeSystem.class.equals(resourceType) && featureFlagFhirPackagePostprocessing) {
          log.info(
              "Consolidating CodeSystems to ensure a single CodeSystem per url/version combination.");
          resourcesForLookup =
              codeSystemConsolidator.consolidateByUrlAndVersion(
                  resourcesForLookup.stream().map(CodeSystem.class::cast).toList());
        }

        if (featureFlagFhirPackagePostprocessing) {
          ensureNoDuplicateResources(resourcesForLookup);
        }

        final var result = toResourceLookupMap(resourcesForLookup);
        log.info("Loaded {}: {} ", folder, result.size());
        return result;
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    private List<MetadataResource> parseProfileResources(
        Class<? extends MetadataResource> resourceType, Path path) throws IOException {
      final var profileResources = getProfilesAsResources(path);
      final var parsedResources =
          new java.util.ArrayList<MetadataResource>(profileResources.size());
      for (final var resource : profileResources) {
        parsedResources.add(parser.parseResource(resourceType, resource.getInputStream()));
      }
      return parsedResources;
    }

    private Map<String, IBaseResource> toResourceLookupMap(
        List<? extends MetadataResource> parsedResources) {
      final Map<String, IBaseResource> result = new LinkedHashMap<>();
      for (final var parsedResource : parsedResources) {
        final String url = parsedResource.getUrl();
        result.put(url, parsedResource);
        if (versionedLookup(parsedResource)) {
          result.put(url + "|" + parsedResource.getVersion(), parsedResource);
        }
      }
      return Map.copyOf(result);
    }

    private void ensureNoDuplicateResources(List<? extends MetadataResource> resources) {
      final Set<String> lookupKeys = new HashSet<>();
      for (final var resource : resources) {
        final String key = toDuplicateCheckKey(resource);
        if (!lookupKeys.add(key)) {
          throw new IllegalStateException(
              "Duplicate resource for lookup key "
                  + key
                  + " ("
                  + resource.getResourceType()
                  + ").");
        }
      }
    }

    private String toDuplicateCheckKey(MetadataResource resource) {
      if (!versionedLookup(resource)) {
        return Objects.toString(resource.getUrl(), "");
      }
      return Objects.toString(resource.getUrl(), "")
          + "|"
          + Objects.toString(resource.getVersion(), "");
    }
  }
}
