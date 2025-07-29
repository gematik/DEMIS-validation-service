{{/*
Expand the name of the chart.
*/}}
{{- define "validation-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "validation-service.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "validation-service.fullversionname" -}}
{{- if .Values.provisioningMode }}
{{- $name := include "validation-service.fullname" . }}
{{- $version := regexReplaceAll "(\\.|_)+" .Chart.Version "-" }}
{{- $profile := ""}}
{{- $versionSpecifier := "-v2" }}
{{- if ( hasKey . "profileVersion" ) }}
{{- $profile = printf "-p-%s" (regexReplaceAll "(\\.|_)+" .profileVersion "-") }}
{{- end }}
{{- printf "%s-%s%s%s" $name $version $profile $versionSpecifier | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := include "validation-service.fullname" . }}
{{- $version := regexReplaceAll "\\.+" .Chart.Version "-" }}
{{- printf "%s-%s" $name $version | trunc 63 }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "validation-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "validation-service.labels" -}}
helm.sh/chart: {{ include "validation-service.chart" . }}
{{ include "validation-service.selectorLabels" . }}
{{ if .Chart.AppVersion -}}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{ end -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- with .Values.customLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "validation-service.selectorLabels" -}}
{{- if .Values.provisioningMode }}
{{- $labels := dict }}
{{- $_ := set $labels "app" (include "validation-service.name" .) }}
{{- $_ = set $labels "version" ( .Chart.AppVersion ) }}
{{- $_ = set $labels "app.kubernetes.io/name" (include "validation-service.name" .) }}
{{- $_ = set $labels "app.kubernetes.io/instance" .Release.Name }}
{{- $_ = set $labels "fhirProfileVersion" (include "validation-service.profileVersionName" . ) }}
{{- $_ = set $labels "fhirProfile" .Values.required.profiles.name }}
{{- if and .Values.required.profiles.versions (not (hasKey . "profileVersion")) }}
{{- range $idx, $version := .Values.required.profiles.versions }}
{{- $_ := set $labels (printf "fhirProfileVersions_%d" $idx) $version }}
{{- end }}
{{- end }}
{{- toYaml $labels }}
{{- else }}
app: {{ include "validation-service.name" . }}
version: {{ .Chart.AppVersion | quote }}
fhirProfile: {{ .Values.required.profiles.name | quote }}
app.kubernetes.io/name: {{ include "validation-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
{{- end }}

{{/*
Deployment labels
*/}}
{{- define "validation-service.deploymentLabels" -}}
istio-validate-jwt: "{{ .Values.istio.validateJwt | required ".Values.istio.validateJwt is required" }}"
{{- with .Values.deploymentLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "validation-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- if .Values.provisioningMode }}
{{- $accountData := . }}
{{- if hasKey $accountData "profileVersion" }}
{{- $_ := unset $accountData "profileVersion" }}
{{- end }}
{{- default (include "validation-service.fullversionname" $accountData) .Values.serviceAccount.name }}
{{- else }}
{{- default (include "validation-service.fullversionname" .) .Values.serviceAccount.name }}
{{- end }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}


{{/*
generate profile version name
*/}}
{{- define "validation-service.profileVersionName" -}}
{{- if and .Values.required.profiles.versions (not (hasKey . "profileVersion")) }}
{{- $profileVersionSuffix := "" }}
{{- if ( gt (len .Values.required.profiles.versions) 0 ) }}
{{- $profileVersionSuffix = join "" .Values.required.profiles.versions | sha256sum | trunc 10 }}
{{- end }}
{{- printf "%s-%s-%s" (regexReplaceAll "(\\.|_)+" .Chart.Version "-") (regexReplaceAll "(\\.|_)+" .Values.required.profiles.name "-") $profileVersionSuffix | trunc 63 | trimSuffix "-" }}
{{- else if (hasKey . "profileVersion") }}
{{- .profileVersion | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- .Values.required.profiles.version | trunc 63 | trimSuffix "-" }}
{{- end}}
{{- end -}}
