<img align="right" width="250" height="47" src="media/Gematik_Logo_Flag.png"/> <br/>

# Validation-Service

[![Quality Gate Status](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Avalidation-service&metric=alert_status&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)
[![Vulnerabilities](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Avalidation-service&metric=vulnerabilities&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)
[![Bugs](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Avalidation-service&metric=bugs&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)
[![Code Smells](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Avalidation-service&metric=code_smells&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)
[![Lines of Code](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Avalidation-service&metric=ncloc&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)
[![Coverage](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Avalidation-service&metric=coverage&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)


<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
       <ul>
        <li><a href="#release-notes">Release Notes</a></li>
      </ul>
	</li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#docker-build">Docker build</a></li>
        <li><a href="#docker-run">Docker run</a></li>
        <li><a href="#intellij-cmd">Intellij/CMD</a></li>
        <li><a href="#kubernetes">Kubernetes</a></li>
        <li><a href="#endpoints">Endpoints</a></li>
      </ul>
    </li>
    <li><a href="#security-policy">Security Policy</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>

## About The Project

The Validation Service is responsible for validating FHIR resources according to the HL7 FHIR specification, based on 
predefined FHIR profiles, provided from RKI (Robert Koch Institute). These profiles are loaded from the file system 
during the service startup phase. Please note that the profiles are not part of this repository. In a Kubernetes 
environment, the FHIR profiles are mounted to the service's file system from a dedicated OCI container image.

This service is a Spring Boot-based application that processes HTTP requests and leverages appropriate HAPI FHIR library
methods to perform the following tasks:
- Message Parsing
- Validation
- Response Serialization


### Release Notes

See [ReleaseNotes](ReleaseNotes.md) for all information regarding the (newest) releases.

## Getting Started

The application requires the DEMIS FHIR Profiles. This image is maintained in DockerHub:
[demis-profile-snapshots](https://hub.docker.com/repository/docker/gematik1/demis-fhir-profile-snapshots/general).

The profiles are require to execute the integration profile tests included in this repository. With -PskipProfileTests
you can skip these tests if you don't have profiles locally.
At runtime execution the profile files must be available in a folder and this folder must be specified through the
environment variable `FHIR_PROFILES_BASEPATH`.

### Installation

```sh
mvn clean verify
```

The Project can be built with the following command:

```sh
mvn -e clean install -DskipTests=true
```
build with docker image:

```docker
docker build -t validation-service:latest .
```
The Docker Image associated to the service can be built alternatively with the extra profile `docker`:

```docker
mvn -e clean install -Pdocker
```
or if you don't have the profiles
```docker
mvn -e clean install -PskipProfileTests -Pdocker
```

The application can be started as Docker container with the following commands assuming that the profile snapshots are 
located in directory /profiles/5.2.0/Fhir

```shell
docker run --rm --name validation-service \
    -v $(pwd)/profiles:/profiles \
    -p 8080:8080 \
    -e FHIR_PROFILES_BASEPATH=/profiles \
    -e FHIR_PROFILES_VERSIONS=5.2.0 \
    validation-service:latest
```
## Kubernetes

## Local

aus IntelliJ als SpringBoot Application starten

![image](src/main/docs/SpringBootApplicationVS.png)


## Intellij/CMD

Start the spring boot server with: `mvn clean spring-boot:run`
Check the server with: `curl -v localhost:8080/actuator/health`

aus IntelliJ als SpringBoot Application starten


## VM


## Properties

| Property                                    | Default Value | Environment Variable    | Environment Variable  Description                                                                                           |
|---------------------------------------------|---------------|-------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| demis.validation-service.profiles.basepath  |               | FHIR_PROFILES_BASEPATH  | Path to base directory with all profiles versions                                                                           |
| demis.validation-service.profiles.fhirpath  | Fhir          |                         | Path inside of a profiles versions directory to the resources.                                                              |
| demis.validation-service.profiles.versions  |               | FHIR_PROFILES_VERSIONS  | List of versions. Must match the directory names under the base path.                                                       |
| demis.validation-service.locale             | `en_US`       |                         | Locale for the HAPI-FHIR context and validator. The language of diagnostics of the outcome is dependent on this locale.     |
| demis.validation-service.minSeverityOutcome | `information` |                         | Minimal severity that will not be filtered out in the Outcome. Possible values: `information`, `warning`, `error`, `fatal`. |



## Usage

Start the spring boot server with: `mvn clean spring-boot:run`
Check the server with: `curl -v localhost:8080/actuator/health`

### Endpoints

| Endpoint                     | Description                                                                                |
|------------------------------|--------------------------------------------------------------------------------------------|
| `/status`                    | GET endpoint for status notifications. Currently minimally implemented.                    |
| `/$validate`                 | POST endpoint for validating messages. Returns validation results from the HAPI Validator. |
| `/actuator/health/`          | Standard endpoint from Actuator.                                                           |
| `/actuator/health/liveness`  | Standard endpoint from Actuator.                                                           |
| `/actuator/health/readiness` | Standard endpoint from Actuator.                                                           |


## Security Policy
If you want to see the security policy, please check our [SECURITY.md](.github/SECURITY.md).

## Contributing
If you want to contribute, please check our [CONTRIBUTING.md](.github/CONTRIBUTING.md).

## License
EUROPEAN UNION PUBLIC LICENCE v. 1.2

EUPL © the European Union 2007, 2016

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions::
    1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
    2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
    3. The software is the result of research and development activities, therefore not necessarily quality assured and without the character of a liable product. For this reason, gematik does not provide any support or other user assistance (unless otherwise stated in individual cases and without justification of a legal obligation). Furthermore, there is no claim to further development and adaptation of the results to a more current state of the art.
3. Gematik may remove published results temporarily or permanently from the place of publication at any time without prior notice or justification.
4. Please note: Parts of this code may have been generated using AI-supported technology.’ Please take this into account, especially when troubleshooting, for security analyses and possible adjustments.

See [LICENSE](LICENSE.md).

## Contact
E-Mail to [DEMIS Entwicklung](mailto:demis-entwicklung@gematik.de?subject=[GitHub]%20Validation-Service)
