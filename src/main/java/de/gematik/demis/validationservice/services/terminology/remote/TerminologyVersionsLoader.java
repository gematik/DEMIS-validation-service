package de.gematik.demis.validationservice.services.terminology.remote;

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

import static de.gematik.demis.validationservice.services.ProfileParserService.FOLDER_CODESYSTEM;
import static de.gematik.demis.validationservice.services.ProfileParserService.FOLDER_VALUE_SET;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.gematik.demis.validationservice.services.ProfileParserService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnTerminologyServerEnabled
@Slf4j
class TerminologyVersionsLoader {
  private final JsonFactory jsonFactory = new JsonFactory();

  public TerminologyVersions loadTerminologyVersions(final Path profileSnapshotsPath) {
    return TerminologyVersions.builder()
        .codeSystemUrlToVersionMap(readAllFiles(profileSnapshotsPath.resolve(FOLDER_CODESYSTEM)))
        .valueSetUrlToVersionMap(readAllFiles(profileSnapshotsPath.resolve(FOLDER_VALUE_SET)))
        .build();
  }

  private Map<String, String> readAllFiles(final Path folderPath) {
    try (final Stream<Path> stream =
        Files.walk(folderPath, ProfileParserService.MAX_FOLDER_DEPTH)) {
      return stream
          .filter(Files::isRegularFile)
          .map(this::readVersionInfo)
          .filter(Objects::nonNull)
          .collect(Collectors.toMap(e -> e.url, e -> e.version));
    } catch (final IOException e) {
      throw new UncheckedIOException(e.getMessage(), e);
    }
  }

  private UrlVersion readVersionInfo(final Path path) {
    try (final JsonParser parser = jsonFactory.createParser(path.toFile())) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        throw new IOException("Expected JSON object at root level");
      }

      String version = null;
      String url = null;

      while (parser.nextToken() != JsonToken.END_OBJECT) {
        final String fieldName = parser.currentName();
        parser.nextToken();

        switch (fieldName) {
          case "version":
            version = parser.getText();
            break;
          case "url":
            url = parser.getText();
            break;
          default:
            parser.skipChildren();
        }

        if (version != null && url != null) {
          return new UrlVersion(url, version);
        }
      }
    } catch (final IOException e) {
      // ignore error and skip file
      log.error("error reading file {}", path, e);
    }
    return null;
  }

  private record UrlVersion(String url, String version) {}
}
