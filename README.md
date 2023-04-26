<img align="right" width="250" height="47" src="media/Gematik_Logo_Flag.png"/> <br/> 

[![Quality Gate Status](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Avalidation-service&metric=alert_status&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)[![Vulnerabilities](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Avalidation-service&metric=vulnerabilities&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)[![Bugs](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Avalidation-service&metric=bugs&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)[![Code Smells](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Avalidation-service&metric=code_smells&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)[![Lines of Code](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Avalidation-service&metric=ncloc&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)[![Coverage](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Avalidation-service&metric=coverage&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)

# Validation-Service

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
       <ul>
        <li><a href="#status">Status</a></li>
        <li><a href="#release-notes">Release Notes</a></li>
        <li><a href="#properties">Properties</a></li>
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
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>

## About The Project

This service serves as a validation service for all notifications send to DEMIS. It uses a snapshot of all profiles and
the DEMIS-Schemas Project to validate any notification.

### Status

[![Quality gate](https://sonar.prod.ccs.gematik.solutions/api/project_badges/quality_gate?project=de.gematik.demis%3Avalidation-service&token=1625f4e36c06a30f797ae56839cc931512156967)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Avalidation-service)

### Release Notes

See [ReleaseNotes.md](./ReleaseNotes.md) for all information regarding the (newest) releases.

### Properties

| Property                                     | Default Value | Description                                                                                                                 |
|----------------------------------------------|---------------|-----------------------------------------------------------------------------------------------------------------------------|
| demis.validation-service.profileResourcePath | `/profile`    | Path to the DEMIS profiles inside the resources.                                                                            |
| demis.validation-service.locale              | `en_US`       | Locale for the HAPI-FHIR context and validator. The language of diagnostics of the outcome is dependent on this locale.     |
| demis.validation-service.minSeverityOutcome  | `information` | Minimal severity that will not be filtered out in the Outcome. Possible values: `information`, `warning`, `error`, `fatal`. |

## Getting Started

The application requires the DEMIS FHIR Profiles, they can be retrieved from a git
repository, [https://gitlab.prod.ccs.gematik.solutions/git/demis/demis-profile-snapshots](demis-fhir-profiles).

The profiles are require to execute the unit and integration tests included in this repository. At runtime execution the
profile files must be available in a folder and this folder must be specified through the environment
variable `FHIR_PROFILES_PATH`.

### Maven Build

The project can be built with the command:

```shell
mvn -e clean install -DskipTests=true
```
### Creation of Docker Image

build container with

```docker 
docker build -t europe-west3-docker.pkg.dev/gematik-all-infra-prod/demis-dev/validation-service:latest .
```

the image can alternatively also be built with maven:

```docker
# With Profiles
mvn -e clean install -Pdownload-profile -Pdocker
# Without Profiles
mvn -e clean install -DskipTests=true -Pdocker
```

### Docker run

The application can be started as Docker container with the following commands:

```shell
docker run --rm --name validation-service \
    -v $(pwd)/profiles:/profiles \
    -p 8080:8080 \
    -e FHIR_PROFILES_PATH=/profiles \
    europe-west3-docker.pkg.dev/gematik-all-infra-prod/demis-dev/validation-service:latest
```

### Intellij/CMD

Start the spring boot server with: `mvn clean spring-boot:run`
Check the server with: `curl -v localhost:8080/actuator/health`

### Kubernetes

In IntelliJ as SpringBoot Application:
![image](src/main/docs/SpringBootApplicationVS.png)

### Endpoints

`/status` get Endpunkt für Statusmeldung (aktuell minimal implementiert)

`/$validate` post Endpunkt um Meldungen zu validieren, liefert Validierungsergebnis von HAPI-Validator zurück

`/actuator/health/` Standardenpunkt vom Actuator

`/actuator/health/liveness` Standardenpunkt vom Actuator

`/actuator/health/readiness` Standardenpunkt vom Actuator

## Contributing

If you want to contribute, please check our [CONTRIBUTING.md](./CONTRIBUTING.md).

## License

EUROPEAN UNION PUBLIC LICENCE v. 1.2

EUPL © the European Union 2007, 2016

See [LICENSE](./LICENSE).

## Contact

E-Mail to [DEMIS Entwicklung](mailto:demis-entwicklung@gematik.de?subject=[GitHub]%20Validation-Service)
