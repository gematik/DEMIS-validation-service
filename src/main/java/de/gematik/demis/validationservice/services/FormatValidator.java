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

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;

@Service
public class FormatValidator {

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final DocumentBuilderFactory documentBuilderFactory =
      DocumentBuilderFactory.newInstance();

  private FormatValidator() {}

  public static boolean isValidJson(String content) {
    try {
      mapper.readTree(content);
    } catch (JacksonException e) {
      return false;
    }
    return true;
  }

  public static boolean isValidXml(String content) {
    try {
      DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
      InputSource is = new InputSource(new StringReader(content));
      builder.parse(is);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
