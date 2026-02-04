<div style="text-align:right"><img src="https://raw.githubusercontent.com/gematik/gematik.github.io/master/Gematik_Logo_Flag_With_Background.png" width="250" height="47" alt="gematik GmbH Logo"/> <br/> </div> <br/>    

# Release Validation-Service

## Release 2.11.0
- added new validation module for strict validation of codings with fragmented code systems and required binding for a
  value set.
- upgrade fhir-package-initializer to 1.0.6 for faster package loading
- validate extensions in FHIR resources and log warnings for unexpected extensions
- deny modifier extensions
- update spring-parent version to 2.14.15

## Release 2.10.1
- removed feature flag FEATURE_FLAG_FILTERED_VALIDATION_ERRORS_DISABLED from values.yaml

## Release 2.10.0
- added support of FHIR packages through new Docker base image (FHIR package initializer)
- update custom quantity validation to match profile definitions from 6.1.7 upwards
- update dependency guava to 33.5.0
- update dependency error prone annotation to 2.42.0
- update dependency commons-text 1.14.0
- update dependency commons-compress 1.28.0
- update dependency checker-qual 3.51.0
- update dependency sqlite-jdbc 3.50.3.0

## 2.9.1
- refactor deployment resource from helm chart for not using versions of profiles in selector

## 2.9.0
- update spring-parent version to 2.13.4
- add .gitattributes

## 2.8.2
### changed
- collect metrics on validation errors
- add quantity and regex checks to validation behind FEATURE_FLAG_CUSTOM_QUANTITY_VALIDATOR_ENABLED and FEATURE_FLAG_CUSTOM_REGEX_VALIDATOR_ENABLED 

## 2.8.1
### changed
- add default feature flags FEATURE_FLAG_FILTERED_VALIDATION_ERRORS_DISABLED, FEATURE_FLAG_FILTERED_ERRORS_AS_WARNINGS_DISABLED to values.yaml
- removed feature flag FEATURE_FLAG_RELAXED_VALIDATION from values.yaml
- refactor helm chart for supporting provisioning of deployment in modes "dedicated", "distributed" and "combined"
- use spring-parent version 2.12.7

## 2.8.0
### changed
- optional support for remote terminology server
- updated profiles
- removed filter of validation errors in the notification
- updated base image
- add feature.flag.relaxed.validation
### fixed
- Dependency-Updates (CVEs et al.)

## 2.7.0
### changed
- support for multiple profile snapshots versions: Breaking Change in Environment Variables.
- updated dependencies

## 2.6.2
- Updated ospo-resources for adding additional notes and disclaimer
- setting new resources in helm chart
- setting new timeouts and retries in helm chart

## 2.6.1
- Rollback HAPI-FHIR to 7.2.2

## 2.6.0
### fixed
- Dependency-Updates (CVEs et al.)

### changed
- First official GitHub-Release
- Update Base-Image to OSADL

## 2.5.0

### fixed
- CVEs

### changed
- Upgraded SpringBoot to 3.3.5
- Upgraded HAPI FHIR to 7.4.5
- Updated Profiles from RKI
- Upgraded Docker Base Image to Eclipse-Temurin JRE 21.0.5
- Stricter validation of the notification

## 2.1.0

### fixed
- CVEs
- Upgraded SpringBoot to 3.2.0


## 2.0.1

### fixed
- add missing License-Header
- add checks to CI-Pipeline


## 2.0.0

### fixed
- fix CVEs

### changed
- Support for new RKI Profiles 1.23.0.alpha3
- Upgraded SpringBoot to 3.0.7


## 1.4.3

### changed
- Upgraded Docker Image to use JRE 17.0.7
- Upgraded HAPI FHIR Utilities to 6.4.4


## 1.4.2

## fixed
- fix CVE-2023-24057
- fix CVE-2023-28465

### changed
- Upgraded HAPI FHIR Utilities to 6.2.5


## 1.4.1

### changed
- Changed logging format to JSON

## 1.4.0

### fixed
- fix Apache Tomcat CVE-2022-45143

### changed
- add code autoformat plugin to pom
- changed base image to alpine

## 1.3.0 (2023-01-25)

### fixed
- fix Apache Tomcat CVE-2022-45143

### changed
- Update Helm Chart


## 1.2.0 (2022-12-04)

### changed
- update code system, introduced hospitalization-reason


## 1.1.0 (2022-11-11)

### changed
- update profile snapshot to v2022-09-27


## 1.0.0 (2022-09-12)

initial Release