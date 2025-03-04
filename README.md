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
during the service startup phase. In a Kubernetes environment, the FHIR profiles are mounted to the service's file 
system from a dedicated OCI container image.

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

The profiles are require to execute the unit and integration tests included in this repository. At runtime execution the
profile files must be available in a folder and this folder must be specified through the environment
variable `FHIR_PROFILES_PATH`.

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
mvn -e clean install -Pdownload-profile -Pdocker
```

Without Profiles
```sh
mvn -e clean install -DskipTests=true -Pdocker
```

The application can be started as Docker container with the following commands:

```shell
docker run --rm --name validation-service \
    -v $(pwd)/profiles:/profiles \
    -p 8080:8080 \
    -e FHIR_PROFILES_PATH=/profiles \
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

| Property                                     | Default Value | Description                                                                                                                 |
|----------------------------------------------|---------------|-----------------------------------------------------------------------------------------------------------------------------|
| demis.validation-service.profileResourcePath | `/profile`    | Path to the DEMIS profiles inside the resources.                                                                            |
| demis.validation-service.locale              | `en_US`       | Locale for the HAPI-FHIR context and validator. The language of diagnostics of the outcome is dependent on this locale.     |
| demis.validation-service.minSeverityOutcome  | `information` | Minimal severity that will not be filtered out in the Outcome. Possible values: `information`, `warning`, `error`, `fatal`. |



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

Copyright (c) 2023 gematik GmbH

See [LICENSE](./LICENSE.md).

## Contact
E-Mail to [DEMIS Entwicklung](mailto:demis-entwicklung@gematik.de?subject=[GitHub]%20Validation-Service)
