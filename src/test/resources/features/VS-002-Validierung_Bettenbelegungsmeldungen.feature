Feature: Validierung von Bettenbelegungsmeldungen

  #Story: https://service.gematik.de/browse/DSC2-2631
  #Story: https://service.gematik.de/browse/DSC2-2652
  #Der Validation-Service soll mittelfristig alle Validierungen in DEMIS übernehmen.
  #Kurzfristig soll er für die Bettenbelegung zur Verfügung stehen.
  #Er soll FHIR-Meldungen im JSON Format annehmen und ein Operation-Outcome zurück liefern.

  Scenario: Positiv - Korrekte Meldungen senden
  - und ein Operation-Outcome zurück liefern
    Given es existiert ein Endpunkt fuer die Annahme der Fhirmeldungen im JSON Format
    When Eine Bettenbelegungsmeldung zur Validierung gesendet wird
    Then Wird eine Antwort mit folgendem HTTP Statuscode und Operation Outcome erwartet
      | 200 | information | processing |

  Scenario: Negativ - Falsche Meldungen senden
  - und ein entsprechender Operation-Outcome inklusive entsprechender Fehlermeldung zurück liefern
    Given es existiert ein Endpunkt fuer die Annahme der Fhirmeldungen im JSON Format
    When Eine falsche Bettenbelegungsmeldung zur Validierung gesendet wird
    Then Wird eine Antwort mit folgendem HTTP Statuscode und Operation Outcome erwartet
      | 422 | error | exception |


  Scenario: Negativ - Was passiert wenn der Validation Service FHIR-Meldungen in einem anderen Format als JSON bekommt
    Given es existiert ein Endpunkt fuer die Annahme der Fhirmeldungen im JSON Format
    When Eine Bettenbelegungsmeldung im XML Format zur Validierung gesendet wird
    Then Wird eine Antwort mit Http Statuscode 415 erwartet
