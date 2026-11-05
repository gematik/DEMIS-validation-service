# Validation Service — Developer Onboarding Guide

Welcome! This guide gives you a working mental model of the **DEMIS Validation Service**
without assuming any prior FHIR, HAPI FHIR, or DEMIS deep knowledge. Read it top to bottom the first time; use it as a
reference afterwards.

---

## 1. What this service does

DEMIS ("Deutsches Elektronisches Melde- und Informationssystem für den Infektionsschutz") is the German system for
reporting infectious diseases. Health providers, labs, and public health authorities submit **notification messages**
(e.g. "patient X tested positive for disease Y", "hospital bed occupancy report").

These messages are formatted in **FHIR** (Fast Healthcare Interoperability Resources), a standard data format for
healthcare information. Before DEMIS accepts a message, it must check that:

- the message is syntactically valid FHIR (correct structure, data types, cardinalities),
- it conforms to the specific rules DEMIS/RKI (Robert Koch-Institut, the German public health institute) impose on top
  of plain FHIR (expressed as FHIR *profiles*),
- coded values (like disease codes, lab result codes) come from allowed, known code lists.

**The Validation Service is a single, focused HTTP microservice that answers exactly one question: "Is this FHIR message
valid according to the currently supported DEMIS profiles?"**

- **Input:** a FHIR resource (typically a `Bundle` or `Parameters` resource wrapping a notification) as JSON or XML,
  sent via HTTP POST.
- **Output:** a FHIR `OperationOutcome` resource — a standardized FHIR "report card" that lists every problem found (or
  is empty/successful if the message is fine), together with an HTTP status code (200 = OK/only warnings, 422 =
  validation errors found).
- **What it delegates:** it does not store, route, transform, or forward the message anywhere. Other DEMIS services take
  care of processing an already-validated message. The Validation Service is a stateless checkpoint.
- **What it depends on:** a set of **FHIR profile files** (the DEMIS/RKI rulebook, described in section 3) that are
  *not* part of this repository — they are mounted onto the filesystem at startup and loaded into memory once.

---

## 2. Validation in a nutshell

```
 1. HTTP POST /$validate  (JSON or XML body, e.g. a Bundle)
          │
 2. Optional: quick JSON/XML well-formedness check (feature-flagged)
          │
 3. ValidationService picks the FHIR validator for each supported profile version
    in configured order (typically newest first) and tries them one after another
    until one succeeds
          │
 4. For each attempt: HAPI FHIR's validation engine walks the resource and checks it
    against structural rules, terminology (allowed codes), and DEMIS custom rules
          │
 5. DEMIS-specific post-processing adjusts the raw findings:
    - some known/accepted HAPI quirks are downgraded from error to warning
    - findings below a configured minimum severity are dropped
          │
 6. Result is packaged into a FHIR OperationOutcome and serialized back as JSON/XML
          │
 7. HTTP response: 200 OK (no errors) or 422 Unprocessable Entity (errors/fatal issues)
```

Remember, step 3 may try several profile versions (usually configured newest first) before it finds one the message
passes. The response the caller receives always contains a single set of findings — never a mix from multiple versions.
Which set is returned depends on the outcome:

- If the message is valid against the **first attempted** profile version (typically the newest), that version's
  (empty/clean) findings are returned — the common, happy-path case.
- If the message **fails** against the first attempted version but **passes** against a later tried one, the response
  still shows the first attempted version's findings (so the sender learns what needs to change to support that target),
  but every error/fatal issue in that list is downgraded to a warning. This keeps the message accepted (HTTP 200) while
  nudging senders to migrate, instead of hard-blocking them.
- If the message fails against **every** supported version, the findings from the first attempted version are returned
  as-is, with errors intact (HTTP 422).

---

## 3. Essential concepts

You need only a handful of FHIR/HAPI concepts to follow the rest of this document.

- **FHIR resource** — A structured healthcare data object, e.g. `Patient`, `Observation`,
  `Bundle`. Represented as JSON or XML with a fixed set of possible fields.
- **Bundle** — A FHIR resource that packages multiple other resources together (e.g. a
  `Patient`, a `Condition`, and an `Observation` describing one disease notification). Most DEMIS messages are Bundles.
- **StructureDefinition / Profile** — A machine-readable rule set that *constrains* a generic FHIR resource type for a
  specific use case: which fields are required, which are forbidden, what data type or length is allowed, etc. "Base
  FHIR" defines what a
  `Patient` *can* contain; a DEMIS *profile* narrows this down to what a DEMIS `Patient`
  notification *must/may* contain.
- **ValueSet** — A named, defined collection of allowed codes (e.g. "all valid German federal states" or "all reportable
  diseases"). Profiles reference ValueSets to say
  "this field's code must come from that list."
- **CodeSystem** — The authoritative source that defines a set of codes and their meanings (e.g. LOINC lab codes, SNOMED
  CT, or a DEMIS-specific code list). A ValueSet is usually built from one or more CodeSystems.
- **Profiles reference terminology, they don't contain it.** A `StructureDefinition`
  doesn't list allowed codes itself — one of its element definitions simply declares a *binding* ("this field's code
  must be a member of ValueSet X"). The actual `ValueSet`
  and `CodeSystem` resources are separate files. In this repository, a DEMIS "profile"
  is really a *package* of several kinds of files shipped together (StructureDefinitions, ValueSets, CodeSystems,
  Questionnaires — see `ProfileParserService`), and terminology validation is the step of resolving that binding and
  checking the code against the referenced ValueSet/CodeSystem from the same package.
- **Terminology validation** — The specific act of checking whether a code found in the message is actually a member of
  the ValueSet/CodeSystem the profile requires.
- **Validation severity** — Every finding has a severity: `information` (FYI),
  `warning` (questionable but accepted), `error` (message is invalid), `fatal` (validator itself could not even process
  the message, e.g. malformed JSON).
- **OperationOutcome** — The standard FHIR resource used to report the *result* of an operation (like validation) as a
  list of issues, each with a severity, a code, a location, and a human-readable diagnostic message.
- **HAPI FHIR** — The Java library ecosystem this service is built on (see section 4); it provides FHIR data model
  classes, parsers, and the actual validation engine.

---

## 4. How HAPI FHIR is used

[HAPI FHIR](https://hapifhir.io/) is the de-facto standard Java implementation of FHIR. It provides:

- **`FhirContext`** — the central object that knows the FHIR version (this service uses **R4**) and provides
  parsers/serializers between JSON/XML text and Java model objects (`org.hl7.fhir.r4.model.*`). It only knows about
  **base FHIR** — the generic specification (e.g. that a `Patient` resource *can* have a `name`, `birthDate`, etc.). It
  has **no knowledge of DEMIS profiles, ValueSets, or CodeSystems** at all; there is exactly one shared, version-scoped
  `FhirContext` for the whole application. DEMIS's profiles and terminology live entirely in the separate
  `IValidationSupport` chain described next, which the validator consults *in addition to* the `FhirContext`.
- **`FhirValidator` / `FhirInstanceValidator`** — the actual rule-checking engine, and the only piece that judges
  whether the resource being validated is correct or not. It walks the resource and applies the standard FHIR structural
  rules (cardinality, data types, invariants, terminology bindings), producing a list of `SingleValidationMessage`s. It
  doesn't know *where* profiles or ValueSets come from — whenever it needs that reference data, it asks an
  `IValidationSupport` (described next).
- **`IValidationSupport`** — an interface for *looking up* reference data, not for applying rules: "give me the
  StructureDefinition for this profile URL", "give me the ValueSet for this URL", "is code X valid in ValueSet Y". It
  never judges the resource itself — it only answers factual questions `FhirInstanceValidator` asks while it validates.
  Multiple implementations exist, each answering from a different source (e.g. a fixed in-memory map of DEMIS profile
  files, HAPI's bundled base FHIR definitions, an in-memory terminology checker). HAPI's `ValidationSupportChain` is
  itself just one more
  `IValidationSupport` implementation: a composite that holds a list of other implementations and, for each lookup, asks
  them one by one until one returns an answer. **This service's own code** (`FhirValidatorFactory`) is what assembles
  that list — it decides which implementations to add and in what order — and then hands the resulting chain to
  `FhirInstanceValidator` as its single source of reference data.
- **`IValidatorModule`** — a pluggable hook that lets you add extra validation *rules*
  beyond what `FhirInstanceValidator` already checks — the counterpart to
  `IValidationSupport`. Where `IValidationSupport` only supplies data, an
  `IValidatorModule` is allowed to inspect the resource and raise its own findings. DEMIS registers several custom
  modules (see section 5) for rules HAPI doesn't enforce out of the box.

Put simply: `IValidationSupport` (and its `ValidationSupportChain` composite) is about **where profile/terminology data
comes from**; it's the main integration point for plugging in DEMIS's own profiles and terminology. `IValidatorModule`
is about **adding new rules**; it's the main integration point for DEMIS-specific checks HAPI doesn't have natively.
Both are exercised together during a single validation call, but they answer different kinds of questions and are
configured independently.

**How it fits together in this service** (see `FhirValidatorFactory`):

1. A `ValidationSupportChain` is built per supported profile *version*, combining (in order): the DEMIS profile files
   loaded from disk, HAPI's default FHIR base definitions, DEMIS terminology support, snapshot generation support, and
   (optionally)
   HAPI's common code systems service.
2. A `FhirInstanceValidator` is created from that chain and registered on a `FhirValidator`. The `FhirInstanceValidator`
   performs the standard FHIR validation against the loaded profiles, structure definitions, terminology, and snapshots.
3. Optional DEMIS-specific `IValidatorModule`s are registered on the same `FhirValidator`. Their activation is
   controlled independently by configuration and feature flags:
    - the extension validator checks allowed and modifier extensions,
    - `ResourceReferencesValidationModule` checks whether internal references in Bundles can be resolved,
    - `ReferenceProfileValidationModule` checks whether a referenced resource declares an allowed profile for the
      corresponding reference path,
    - QuestionnaireResponse validators check custom quantity/comparator and regular-expression constraints,
    - `StrictValueSetMembershipValidator` performs additional strict ValueSet membership checks, and
    - `AbstractProfileValidator` optionally validates against abstract profiles.
4. The custom modules that process direct resources or resources wrapped in a FHIR `Parameters` resource use
   `ParametersValidationModule`. Shared helpers such as `ResourceWalker`, `BundleResourceIndex`, and
   `HapiValidationMessages` provide resource traversal, Bundle entry lookup, and location-aware message mapping.
5. One validator is built and cached per supported FHIR profile package version (`FhirValidatorManager`), because DEMIS
   supports validating against multiple profile generations. `ValidationService` invokes these validators in the
   configured order and applies the final severity filtering and error-to-warning handling.

Everything DEMIS-specific is additive around a mostly-standard HAPI FHIR validation pipeline — this is important: when
debugging, first figure out whether a finding comes from stock HAPI FHIR or from one of the custom modules described
below.

---

## 5. How the project is organized

```
src/main/java/de/gematik/demis/validationservice/
├── ValidationServiceApplication.java        Spring Boot entry point
├── controller/
│   └── ValidationController.java            HTTP endpoints, including /$validate
├── config/
│   ├── ValidationServiceConfiguration.java  FhirContext bean and application-wide setup
│   └── ValidationConfigProperties.java      Typed validation-service configuration
└── services/
    ├── CodeSystemConsolidator.java          Consolidates CodeSystem resources from the profile package
    ├── FormatValidator.java                 Optional JSON/XML well-formedness check
    ├── ProfileParserService.java            Reads profile resources from disk
    ├── ProfileSnapshot.java                 In-memory resources for one profile package version
    ├── ValidationMetrics.java               Micrometer metrics for validation outcomes
    ├── terminology/
    │   ├── TerminologyValidationProvider.java
    │   │                                      Interface for plugging in a terminology strategy
    │   └── local/
    │       ├── ExpandedValueSetsCodeValidationSupport.java
    │       │                                  Local ValueSet and code validation support
    │       └── LocalTerminologyValidationProvider.java
    │                                              Configures local terminology validation
    └── validation/
        ├── CacheDelegator.java               Delegates validation-support cache operations
        ├── DemisPrePopulatedValidationSupportHapi4.java
        │                                      Provides DEMIS profile resources to HAPI FHIR
        ├── ExpireAfterAccessCacheProvider.java
        │                                      Cache provider with expire-after-access behavior
        ├── FhirValidatorFactory.java         Builds and configures one FhirValidator
        ├── FhirValidatorManager.java         Manages one validator per profile package version
        ├── FilteredMessagePrefixesFactory.java
        │                                      Creates localized prefixes for error-to-warning filtering
        ├── LoadingCacheDelegator.java        Delegates loading-cache operations
        ├── SeverityComparator.java           Orders validation severities
        ├── ValidationService.java            Orchestrates multi-version validation and result filtering
        └── custom/
            ├── BundleResourceIndex.java      Indexes resources contained in a Bundle
            ├── HapiValidationMessages.java   Maps custom findings to HAPI validation messages
            ├── ParametersValidationModule.java
            │                                  Base class for validators handling direct resources
            │                                  and resources wrapped in Parameters
            ├── ResourceWalker.java           Traverses FHIR resources and provides element context
            ├── ValidationMessages.java      Abstraction for adding mapped validation messages
            ├── extension/
            │   ├── ExtensionAllowedValidator.java
            │   │                              Checks allowed and modifier extensions
            │   ├── ExtensionAllowedValidatorProvider.java
            │   │                              Creates the extension validation module
            │   └── StructureDefinitionExtensionExtractor.java
            │                                          Extracts allowed extensions from profiles
            ├── profile/
            │   └── AbstractProfileValidator.java
            │                                      Optional validation against abstract profiles
            ├── questionnaire/
            │   └── responses/
            │       ├── AbstractCustomQuestionnaireResponseValidator.java
            │       │                              Base class for QuestionnaireResponse validators
            │       ├── ComparatorHelper.java
            │       │                              Compares QuestionnaireResponse quantities
            │       ├── CustomQuantityComparatorQuestionnaireResponseValidator.java
            │       │                              Validates quantity/comparator constraints
            │       └── CustomRegexQuestionnaireResponseValidator.java
            │                                              Validates QuestionnaireResponse regex constraints
            ├── referenceprofilevalidation/
            │   ├── ReferenceProfileValidationModule.java
            │   │                              Checks profiles of Bundle-local referenced resources
            │   ├── rules/
            │   │   ├── EffectiveReferenceRulesResolver.java
            │   │   │                              Resolves effective rules for reference paths
            │   │   ├── ReferenceProfileRules.java
            │   │   │                              Stores reference-profile rules derived from profiles
            │   │   ├── ReferenceProfileRulesBuilder.java
            │   │   │                              Builds reference-profile rules from StructureDefinitions
            │   │   └── SlicingRootResolver.java
            │   │                                  Resolves slicing roots for reference validation
            │   └── slicing/
            │       ├── Discriminator.java       Describes a slice discriminator
            │       ├── SliceDef.java            Represents one slice and its reference rules
            │       ├── SliceExtractor.java      Extracts slice definitions from profiles
            │       ├── SliceMatcher.java        Matches Bundle elements to slice definitions
            │       └── SlicingRoot.java         Represents a slicing root
            ├── resourcereferences/
            │   └── ResourceReferencesValidationModule.java
            │                                      Checks that internal Bundle references resolve
            └── strict/
                └── valuesets/
                    ├── BindingInfo.java        Stores binding information for coded elements
                    └── StrictValueSetMembershipValidator.java
                                                   Performs additional strict ValueSet membership checks
```

### Suggested reading path

Read these files in order for the fastest path to understanding the implementation:

1. `README.md` — endpoints, properties, feature flags, how to run locally.
2. `controller/ValidationController.java` — entry point, request/response shape.
3. `services/validation/ValidationService.java` — the multi-version validation flow.
4. `services/validation/FhirValidatorManager.java` and `FhirValidatorFactory.java` — how a validator is assembled per
   profile version, and how custom modules get plugged in.
5. `services/ProfileParserService.java` + `ProfileSnapshot.java` — how profile files become in-memory lookup structures.
6. `services/validation/custom/ParametersValidationModule.java` — how custom modules process direct resources or
   resources wrapped in a FHIR `Parameters` resource.
7. `services/validation/custom/resourcereferences/ResourceReferencesValidationModule.java` — checks that Bundle-internal
   references can be resolved.
8. `services/validation/custom/referenceprofilevalidation/ReferenceProfileValidationModule.java` — checks that resources
   referenced within a Bundle declare an allowed profile for the corresponding reference path. The related `rules/` and
   `slicing/` packages show how these rules are derived from StructureDefinitions and how sliced reference paths are
   matched.
9. `services/terminology/local/ExpandedValueSetsCodeValidationSupport.java` — implements local terminology validation
   for ValueSets and coded values loaded from the DEMIS profile package.
10. `services/validation/custom/extension/ExtensionAllowedValidator.java` — shows how DEMIS-specific extension rules are
    implemented, including allowed extension URLs and modifier extensions.
11. `services/validation/custom/strict/valuesets/StrictValueSetMembershipValidator.java` — shows the additional
    validation used when strict membership in a required ValueSet is enabled.
12. `services/validation/ValidationServiceIntegrationProfileTest.java` (test) — see real example messages exercised
    end-to-end and how the resulting validation outcome is asserted.

## 6. Configuration overview

Configuration is loaded from `application.properties` (overridable via environment variables — see `README.md` for the
exact variable names). It is split between typed properties and direct flags/options:

- **`ValidationConfigProperties`** (`demis.validation-service.*`) — structural settings:
    - where profile files live on disk, and which profile package *versions* to load and validate against
      (`profiles.basepath`, `profiles.versions`).
    - the **locale** for validation diagnostic messages.
    - **`minSeverityOutcome`** — findings below this severity are stripped from the response entirely. This is the
      single most impactful setting for how "noisy" responses are.
    - **`unexpectedExtensionSeverity`**, **`commonCodeSystemTerminologyEnabled`**, **`customRegexValidatorEnabled`**,
      and **`customQuantityValidatorEnabled`** — optional validator behavior.
- **Direct flags/options** (`feature.flag.*`, `config.option.*`) — toggles wired via `@Value`, e.g.
  `feature.flag.format.validation.enabled`, `feature.flag.filtered.errors.as.warnings.disabled` and
  `config.option.additional.strict.coding.validator.enabled`.

Rule of thumb: config properties change *what is structurally possible* (which profiles, which locale); feature flags
change *how strict or lenient* the service is about edge cases.

---

## 7. Validation results and errors

- Every problem HAPI's validator (or a custom module) finds becomes a
  `SingleValidationMessage` with a severity, a message text, a message ID (used for filtering), and a location
  (FHIRPath-like string pointing at the offending field).
- **`ValidationService.filterValidationResult(...)`** adjusts the raw list of messages and:
    1. downgrades specific known `error` messages to `warning` based on localized message prefixes from
       `FilteredMessagePrefixesFactory` (when `feature.flag.filtered.errors.as.warnings.disabled=false`),
    2. appends a migration note to each downgraded message indicating the downgrade is temporary,
    3. removes any remaining messages below the configured `minSeverityOutcome`.
- Custom validator modules add their findings to the same HAPI validation context. This includes extension checks,
  internal reference resolution, referenced-resource profile checks, abstract-profile validation, strict ValueSet
  membership checks, and QuestionnaireResponse-specific checks. ParametersValidationModule and HapiValidationMessages
  preserve the correct input location when a resource is wrapped in a Parameters resource.
- The final list is converted into a FHIR `OperationOutcome` (`ValidationResult
  .populateOperationOutcome(...)`), one `issue` entry per message.
- The controller inspects the outcome: if any issue has severity `error` or `fatal`, HTTP status is
  `422 Unprocessable Entity`; otherwise `200 OK` (even if there are warnings/ info issues).

---

## 8. Testing and debugging

- **Unit tests** live next to the class they test (mirrored package structure under
  `src/test/java`), e.g. `ValidationServiceTest`,
  `ExpandedValueSetsCodeValidationSupportTest`, `SeverityComparatorTest`.
- **Integration/profile tests** (classes ending in `...IntegrationProfileTest` or
  `...ProfileTest`, e.g. `ValidationServiceIntegrationProfileTest`,
  `ValidationControllerIntegrationProfileTest`) spin up the real Spring context and run actual example FHIR messages
  (JSON files under test resources) through the full validator, asserting on the resulting `OperationOutcome`. These
  require the real DEMIS profile files to be present locally (see README — `-PskipProfileTests` to skip them if you
  don't have profiles).
- **Regression tests** (e.g. `ExpandedValueSetsCodeValidationSupportRegressionTest`,
  `ValidationServiceIntegrationProfileErrorToWarningRegressionTest`) pin down specific historic bugs/behaviors so they
  don't silently reappear.
- **If a message unexpectedly passes or fails validation**, walk the validation flow in this order:
    1. **Check the input format first.** Verify whether
       `feature.flag.format.validation.enabled` accepts the raw JSON or XML body. If the format check rejects the
       input, profile validation has not started yet.
    2. **Check the input resource and wrapper.** Determine whether the request contains a Bundle directly or contains
       resources inside a FHIR `Parameters` resource. Custom modules based on `ParametersValidationModule` process
       resources from both forms, but they may only apply their rules to specific resource types.
    3. **Check the profile package version.** Determine which configured profile version was tried first and which versions
       were tried afterwards. `ValidationService` may validate the same input against multiple versions. A message can fail
       against the first version but pass against a later one. The per-attempt validation log contains the selected version
       and the validation result.
    4. **Separate raw findings from final findings.** Check whether the expected HAPI or custom validation message was
       produced at all. If it was produced but is missing from the response, inspect
       `ValidationService.filterValidationResult(...)`, the configured `minSeverityOutcome`, and the
       `FilteredMessagePrefixesFactory`. Known findings may be downgraded from `error` to `warning` or removed because
       of the minimum severity setting.
    5. **Identify the responsible validation module.** Depending on the finding, inspect:
        - `FhirInstanceValidator` for standard FHIR structure, cardinality, datatype, invariant, profile, and terminology
          validation;
        - `ExtensionAllowedValidator` for allowed or modifier extension findings;
        - `ResourceReferencesValidationModule` for unresolved Bundle-internal references;
        - `ReferenceProfileValidationModule` for a referenced resource that declares a profile not allowed at the
          corresponding reference path;
        - `CustomRegexQuestionnaireResponseValidator` and
          `CustomQuantityComparatorQuestionnaireResponseValidator` for QuestionnaireResponse-specific constraints;
        - `StrictValueSetMembershipValidator` for additional strict ValueSet membership checks; and
        - `AbstractProfileValidator` for optional abstract-profile validation.
    6. **For reference-profile findings, inspect the derived rules and slicing.** Check the relevant
       `StructureDefinition`, the rules built by `ReferenceProfileRulesBuilder`, and the path and slice resolution in
       the `rules/` and `slicing/` packages. A reference may be resolvable but still fail because the target resource's
       `meta.profile` is not allowed at that reference path.
    7. **Check the relevant feature flag or configuration option.** In particular, verify
       `feature.flag.vs.reference.validation`,
       `feature.flag.reference.profile.validation`,
       `feature.flag.abstract.profile.validation`,
       `config.option.additional.strict.coding.validator.enabled`,
       `customRegexValidatorEnabled`, and `customQuantityValidatorEnabled`.
- **Useful log lines to watch:** startup logs from `FhirValidatorFactory` and `FhirValidatorManager` showing the
  configured profile paths and versions; `ValidationService` logs for each profile-version attempt; debug logs from
  `ExpandedValueSetsCodeValidationSupport` for terminology lookups; and debug logs from the reference validation
  modules for Bundle reference paths, resolved targets, and declared profiles.
- When a custom validation message is reported for a resource wrapped in `Parameters`, inspect the mapped location
  produced by `HapiValidationMessages`. The reported path includes the corresponding
  `Parameters.parameter[index].resource` path and may therefore differ from the resource-local path used inside the
  validator.

### Git Submodules

**What are git submodules?**
A git submodule is a separate Git repository embedded in your repository at a pinned commit. We use them to pull in the
`demis-profile-snapshots` repo (DEMIS FHIR profiles) under `profiles/`, without merging its history into ours.

*We used them for the **integration tests** (see above)
— `ValidationServiceIntegrationProfileTest` and related tests read profile files straight from
`profiles/<version>/...` on disk. They are **not** used at runtime: the running application loads profiles from a path
given via `FHIR_PROFILES_BASEPATH`, normally populated from the separate
`fhir-profile-snapshots` Docker image (see `README.md`/`README_internal.md`), not from this submodule checkout.

**What you need to know as a developer:**

| Situation                                                                             | Command                                                                                                                                                                      |
|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **After cloning or pulling changes from the main repository**                         | `git submodule update --init --recursive` after `git clone` or `git pull`. This initializes newly added submodules and checks out the commits pinned by the main repository. |
| **You want to update all profiles to the latest commit of their configured branches** | `git submodule update --remote`, then commit the changed submodule references.                                                                                               |
| **You want to update one profile to the latest commit of its configured branch**      | `git submodule update --remote profiles/6.1.7`, then commit the changed submodule reference.                                                                                 |

**Do I need to edit .gitmodules?**
Usually, no. Use Git commands when adding or removing a profile submodule because .gitmodules contains only the
submodule configuration. Git must also add or remove the corresponding submodule entry in the parent repository.

- Add a new profile version
  `git submodule add -b <branch> <repo-url> profiles/<version>`
  This automatically updates .gitmodules and adds the submodule to the repository.

- Remove a profile version
  `git submodule deinit -f profiles/<version>`
  `git rm -f profiles/<version>`
  This removes the submodule and updates .gitmodules.

You may edit .gitmodules directly when changing configuration of an existing submodule, for example its repository URL
or tracked branch.

---

## 9. Glossary

| Term                                            | Meaning                                                                                                                                                                                                          |
|-------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **FHIR**                                        | Fast Healthcare Interoperability Resources — the HL7 data exchange standard used for the messages this service validates.                                                                                        |
| **R4**                                          | The specific FHIR version (Release 4) this service supports.                                                                                                                                                     |
| **Resource**                                    | A single structured FHIR data object (`Patient`, `Observation`, `Bundle`, …).                                                                                                                                    |
| **Bundle**                                      | A FHIR resource that packages several other resources together; typical shape of a DEMIS notification message.                                                                                                   |
| **Profile / StructureDefinition**               | A rule set that narrows a generic FHIR resource type for a specific use case (here: DEMIS/RKI notifications).                                                                                                    |
| **ValueSet**                                    | A named set of allowed codes for a field.                                                                                                                                                                        |
| **CodeSystem**                                  | The authoritative source/definition of a set of codes (e.g. LOINC, SNOMED CT, or a DEMIS-specific list).                                                                                                         |
| **Terminology validation**                      | Checking whether a code used in a message belongs to the ValueSet/CodeSystem required by the profile.                                                                                                            |
| **Binding (required/extensible/…)**             | How strictly a profile enforces that a coded field's value must come from a bound ValueSet. `REQUIRED` = must be a member.                                                                                       |
| **OperationOutcome**                            | The standard FHIR resource used to report the result of an operation (like validation) as a list of issues.                                                                                                      |
| **Issue / SingleValidationMessage**             | One individual finding: a severity, message, code/ID, and location.                                                                                                                                              |
| **Severity**                                    | `information` < `warning` < `error` < `fatal`, in increasing seriousness.                                                                                                                                        |
| **HAPI FHIR**                                   | The Java library this service is built on; provides the FHIR data model, parsers, and the validation engine.                                                                                                     |
| **FhirContext**                                 | HAPI's central object for a given FHIR version; creates parsers and the validator.                                                                                                                               |
| **FhirValidator / FhirInstanceValidator**       | HAPI's engine that actually checks a resource against profiles and terminology.                                                                                                                                  |
| **IValidationSupport**                          | HAPI's pluggable interface for looking up profiles/ValueSets/CodeSystems and validating codes; DEMIS implements/chains several of these.                                                                         |
| **ValidationSupportChain**                      | An ordered list of `IValidationSupport` implementations queried in turn.                                                                                                                                         |
| **IValidatorModule**                            | A pluggable extra validation step registered on a `FhirValidator`; DEMIS uses this to add rules HAPI doesn't have natively.                                                                                      |
| **Profile version / package version**           | One generation of the DEMIS/RKI profile rulebook (e.g. `5.2.0`); this service can validate against several versions at once and picks the best match.                                                            |
| **RKI**                                         | Robert Koch-Institut — the German public health institute that authors the FHIR profiles DEMIS validates against.                                                                                                |
| **Parameters**                                  | A FHIR resource that can wrap one or more resources in `parameter.resource`. The service accepts Bundles directly and also processes resources contained in Parameters for custom validation modules.            |
| **ParametersValidationModule**                  | A base class for custom validator modules that handles resources supplied directly or wrapped in a Parameters resource and maps findings back to their original input locations.                                 |
| **Bundle-local reference / Internal reference** | A reference to a resource within the same Bundle, for example `ResourceType/id`, `urn:uuid:…`, or a contained-resource reference. The service can resolve these references without accessing an external server. |
| **Resource reference validation**               | Checking whether a Bundle-local reference points to an existing resource in the Bundle.                                                                                                                          |
| **Reference profile validation**                | Checking whether the resource targeted by a Bundle-local reference declares a profile allowed at the corresponding reference path.                                                                               |
| **`meta.profile`**                              | The list of profile URLs declared by a FHIR resource. The reference-profile validator uses these declarations when checking whether a referenced resource is allowed at a particular path.                       |
| **Profile inheritance / Derived profile**       | The relationship between a more specific profile and its base profile. A referenced resource may be accepted when its declared profile or one of its ancestors is allowed by the reference rule.                 |
| **Slicing**                                     | A FHIR mechanism for dividing repeated elements into named slices with different constraints, usually identified by discriminator values.                                                                        |
| **Slice / Slice discriminator**                 | A slice is one constrained part of a sliced element. A discriminator defines how the validator determines which slice a particular element belongs to.                                                           |
| **Snapshot / Differential**                     | A StructureDefinition differential contains changes relative to the base profile; a snapshot contains the complete set of element definitions needed for validation.                                             |
| **Abstract profile**                            | A profile intended as a reusable base definition rather than for direct instance validation. The service can optionally validate against abstract profiles through `AbstractProfileValidator`.                   |
| **Profile version / package version**           | One generation of the DEMIS/RKI profile package, including StructureDefinitions and related terminology resources. The service can validate against several package versions and tries them in configured order. |
| **ValidationSupportChain**                      | An ordered composite of `IValidationSupport` implementations. It resolves profile, terminology, and other reference data by querying its configured support components.                                          |
