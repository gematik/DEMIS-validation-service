Feature: Aufräumen der Ergebnisse nach der Validierung

  #Story: https://service.gematik.de/browse/DSC2-2668
  #Nach der erfolgreichen Validierung des Reports muss das Ergebnis aufgeräumt werden
  # Wenn Kardinalitäten etc verletzt werden, dann werden diese entsprechend zum NES angepasst,
  # sodass der Report Processing Service nicht einfach abbricht.
  #Story: https://service.gematik.de/browse/DSC2-2756
  #Ein Validierungsergebnis darf keine Informationen zu Slicingfehlern enthalten
  #Einträge der Art "Dieses Element stimmt mit keinem bekannten Slice define in profile xxxx überein"

  Scenario: Nach der erfolgreichen Validierung des Reports muss das Ergebnis aufgeräumt werden
    Given Eine Validierung einer Bettenbelegungsmeldung liefert ein Ergebnis
    When Constraints verletzt wurden
    Then werden Fehlermeldungen die mit folgenden Informationen starten aus der Ergebnisliste rausgefiltert
      | "Unable to find a match for profile"                                             |
      | "Unable to find a match for the specified profile among choices"                 |
      | "Multiple profiles found for entry resource. This is not supported at this time" |
      | "This element does not match any known slice defined in the profile"             |