Feature: Lokalisieurng Systemsprache
#Story: https://service.gematik.de/browse/DSC2-2755

  Scenario Outline: Fehlermeldungsspache wird unabhaengig von der Systemsprache ueber locale beim Start gesteuert
    Given Locale ist auf <sprache> gesetzt
    When Validator initiiert wird
    And Der Validator eine fehlerhafte Meldung erhaelt
    Then Dann muss die Fehlermeldung auf <sprache> sein
    Examples:
    |sprache  |
    |englisch |
    |deutsch  |