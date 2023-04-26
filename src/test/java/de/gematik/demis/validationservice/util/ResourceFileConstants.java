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

package de.gematik.demis.validationservice.util;

/** Class to hold resource paths. */
public final class ResourceFileConstants {

  public static final String VALID_REPORT_BED_OCCUPANCY_EXAMPLE =
      "./src/test/resources/valid_report_bed_occupancy_example.json";
  public static final String INVALID_REPORT_BED_OCCUPANCY_EXAMPLE =
      "./src/test/resources/invalid_report_bed_occupancy_example.json";
  public static final String INVALID_TEST_NOTIFICATION_DV_2 =
      "./src/test/resources/invalid_test_notification_dv2.json";
  public static final String NOT_PARSEABLE_REPORT_BED_OCCUPANCY_EXAMPLE =
      "./src/test/resources/not_parseable_report_bed_occupancy_example.json";
  public static final String PROFILE_RESOURCE_PATH = "./profiles";

  private ResourceFileConstants() {}
}
