package de.gematik.demis.validationservice.services.validation.custom;

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

import static org.junit.jupiter.api.Assertions.*;

import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComparatorHelperTest {

  @Test
  @DisplayName("quantitiesHaveSameUnit returns true for identical system and code")
  void testQuantitiesHaveSameUnit_true() {
    Quantity a = new Quantity().setSystem("http://unitsofmeasure.org").setCode("mg");
    Quantity b = new Quantity().setSystem("http://unitsofmeasure.org").setCode("mg");
    assertTrue(ComparatorHelper.quantitiesHaveSameUnit(a, b));
  }

  @Test
  @DisplayName("quantitiesHaveSameUnit returns false for different system or code")
  void testQuantitiesHaveSameUnit_false() {
    Quantity a = new Quantity().setSystem("http://unitsofmeasure.org").setCode("mg");
    Quantity b = new Quantity().setSystem("http://unitsofmeasure.org").setCode("g");
    assertFalse(ComparatorHelper.quantitiesHaveSameUnit(a, b));
    Quantity c = new Quantity().setSystem("other").setCode("mg");
    assertFalse(ComparatorHelper.quantitiesHaveSameUnit(a, c));
  }

  @Test
  @DisplayName("quantitiesHaveSameUnit returns false if one quantity is null")
  void testQuantitiesHaveSameUnit_oneNull() {
    Quantity a = new Quantity().setSystem("http://unitsofmeasure.org").setCode("mg");
    assertFalse(ComparatorHelper.quantitiesHaveSameUnit(a, null));
    assertFalse(ComparatorHelper.quantitiesHaveSameUnit(null, a));
  }

  @Test
  @DisplayName("quantitiesHaveSameUnit returns false if both quantities are null")
  void testQuantitiesHaveSameUnit_bothNull() {
    assertFalse(ComparatorHelper.quantitiesHaveSameUnit(null, null));
  }

  @Test
  @DisplayName("getComparator returns '>=' for minQuantity URLs")
  void testGetComparator_minQuantity() {
    assertEquals(">=", ComparatorHelper.getComparator("http://foo/bar/questionnaire-minQuantity"));
  }

  @Test
  @DisplayName("getComparator returns '<=' for maxQuantity URLs")
  void testGetComparator_maxQuantity() {
    assertEquals("<=", ComparatorHelper.getComparator("http://foo/bar/questionnaire-maxQuantity"));
  }

  @Test
  @DisplayName("getComparator throws for unknown extension URLs")
  void testGetComparator_unknownExtension() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> ComparatorHelper.getComparator("http://foo/bar/questionnaire-unknown"));
    assertTrue(ex.getMessage().contains("Unknown quantity extension URL"));
  }

  @Test
  @DisplayName("compareQuantities returns false if actual value is null")
  void testCompareQuantities_actualNull() {
    Quantity actual = new Quantity();
    Quantity ref = new Quantity().setValue(5);
    assertFalse(ComparatorHelper.compareQuantities(actual, ref, ">="));
  }

  @Test
  @DisplayName("compareQuantities returns true if ref value is null")
  void testCompareQuantities_refNull() {
    Quantity actual = new Quantity().setValue(5);
    Quantity ref = new Quantity();
    assertTrue(ComparatorHelper.compareQuantities(actual, ref, ">="));
  }

  @Test
  @DisplayName("compareQuantities for '>=' comparator")
  void testCompareQuantities_greaterOrEqual() {
    Quantity actual = new Quantity().setValue(10);
    Quantity ref = new Quantity().setValue(5);
    assertTrue(ComparatorHelper.compareQuantities(actual, ref, ">="));
    assertTrue(ComparatorHelper.compareQuantities(actual, ref, ">="));
    actual.setValue(5);
    assertTrue(ComparatorHelper.compareQuantities(actual, ref, ">="));
    actual.setValue(4);
    assertFalse(ComparatorHelper.compareQuantities(actual, ref, ">="));
  }

  @Test
  @DisplayName("compareQuantities for '<=' comparator")
  void testCompareQuantities_lessOrEqual() {
    Quantity actual = new Quantity().setValue(3);
    Quantity ref = new Quantity().setValue(5);
    assertTrue(ComparatorHelper.compareQuantities(actual, ref, "<="));
    actual.setValue(5);
    assertTrue(ComparatorHelper.compareQuantities(actual, ref, "<="));
    actual.setValue(6);
    assertFalse(ComparatorHelper.compareQuantities(actual, ref, "<="));
  }

  @Test
  @DisplayName(
      "compareQuantities throws for unknown comparator (defensive, unreachable in production)")
  void testCompareQuantities_unknownComparator() {
    Quantity actual = new Quantity().setValue(1);
    Quantity ref = new Quantity().setValue(2);
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> ComparatorHelper.compareQuantities(actual, ref, "=="));
    assertTrue(ex.getMessage().contains("Unreachable comparator"));
  }

  @Test
  @DisplayName("isQuantityConstraintExtension returns true for minQuantity URL")
  void testIsQuantityConstraintExtension_minQuantity() {
    assertTrue(
        ComparatorHelper.isQuantityConstraintExtension("http://foo/bar/questionnaire-minQuantity"));
  }

  @Test
  @DisplayName("isQuantityConstraintExtension returns true for maxQuantity URL")
  void testIsQuantityConstraintExtension_maxQuantity() {
    assertTrue(
        ComparatorHelper.isQuantityConstraintExtension("http://foo/bar/questionnaire-maxQuantity"));
  }

  @Test
  @DisplayName("isQuantityConstraintExtension returns false for unrelated URL")
  void testIsQuantityConstraintExtension_unrelated() {
    assertFalse(ComparatorHelper.isQuantityConstraintExtension("http://foo/bar/questionnaire-foo"));
    assertFalse(ComparatorHelper.isQuantityConstraintExtension("minQuantity"));
    assertFalse(ComparatorHelper.isQuantityConstraintExtension("maxQuantity"));
    assertFalse(ComparatorHelper.isQuantityConstraintExtension(""));
  }

  @Test
  @DisplayName("isQuantityConstraintExtension returns false for null input")
  void testIsQuantityConstraintExtension_null() {
    assertFalse(ComparatorHelper.isQuantityConstraintExtension(null));
  }

  @Test
  @DisplayName("quantitiesHaveSameUnit returns true when codes equal and system equal")
  void testQuantitiesHaveSameUnit_primaryCodeMatch() {
    Quantity a =
        new Quantity().setSystem("http://unitsofmeasure.org").setCode("mg").setUnit("Milligram");
    Quantity b = new Quantity().setSystem("http://unitsofmeasure.org").setCode("mg").setUnit("mg");
    assertTrue(ComparatorHelper.quantitiesHaveSameUnit(a, b));
  }

  @Test
  @DisplayName("quantitiesHaveSameUnit returns false when codes differ even if units match")
  void testQuantitiesHaveSameUnit_codesDifferUnitsSame() {
    Quantity a =
        new Quantity().setSystem("http://unitsofmeasure.org").setCode("mg").setUnit("Milligram");
    Quantity b =
        new Quantity().setSystem("http://unitsofmeasure.org").setCode("g").setUnit("Milligram");
    assertFalse(ComparatorHelper.quantitiesHaveSameUnit(a, b));
  }

  @Test
  @DisplayName("quantitiesHaveSameUnit returns false when one code missing")
  void testQuantitiesHaveSameUnit_codeMissingNoFallback() {
    Quantity a =
        new Quantity().setSystem("http://unitsofmeasure.org").setUnit("Milligram"); // code fehlt
    Quantity b =
        new Quantity().setSystem("http://unitsofmeasure.org").setCode("mg").setUnit("Milligram");
    assertFalse(ComparatorHelper.quantitiesHaveSameUnit(a, b));
  }

  @Test
  @DisplayName("quantitiesHaveSameUnit returns false when both codes missing")
  void testQuantitiesHaveSameUnit_bothCodesMissingNoFallback() {
    Quantity a = new Quantity().setSystem("http://unitsofmeasure.org").setUnit("Milligram");
    Quantity b = new Quantity().setSystem("http://unitsofmeasure.org").setUnit("Milligram");
    assertFalse(ComparatorHelper.quantitiesHaveSameUnit(a, b));
  }

  @Test
  @DisplayName("quantitiesHaveSameUnit returns false when system differs even if codes match")
  void testQuantitiesHaveSameUnit_systemDiffers() {
    Quantity a = new Quantity().setSystem("http://unitsofmeasure.org").setCode("mg");
    Quantity b = new Quantity().setSystem("http://other.system").setCode("mg");
    assertFalse(ComparatorHelper.quantitiesHaveSameUnit(a, b));
  }

  @Test
  @DisplayName("quantitiesHaveSameUnit returns false when system blank")
  void testQuantitiesHaveSameUnit_systemBlank() {
    Quantity a = new Quantity().setSystem(" ").setCode("mg");
    Quantity b = new Quantity().setSystem("http://unitsofmeasure.org").setCode("mg");
    assertFalse(ComparatorHelper.quantitiesHaveSameUnit(a, b));
  }
}
