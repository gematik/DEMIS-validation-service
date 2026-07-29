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
{{- $name := include "validation-service.fullname" . }}
{{- $version := regexReplaceAll "(\\.|_)+" .Chart.Version "-" }}
{{- printf "%s-%s" $name $version | trunc 63 | trimSuffix "-" }}
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
{{- include "validation-service.versionLabels" . }}
{{ if .Chart.AppVersion -}}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{ end -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- with .Values.customLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
version labels
*/}}
{{- define "validation-service.versionLabels" -}}
{{- $labels := merge (dict) (include "validation-service.selectorLabels" . | fromYaml) }}
{{- $_ := set $labels "fhirProfileVersion" (include "validation-service.packageVersionName" . ) }}
{{- if and .Values.required.packages.versions (gt (len .Values.required.packages.versions) 1) }}
{{- range $idx, $version := .Values.required.packages.versions }}
{{- $_ := set $labels (printf "fhirProfileVersions_%d" $idx) $version }}
{{- end }}
{{- end }}
{{ toYaml $labels }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "validation-service.selectorLabels" -}}
{{- $labels := dict }}
{{- $_ := set $labels "app" (include "validation-service.name" .) }}
{{- $_ = set $labels "version" ( .Chart.AppVersion ) }}
{{- $_ = set $labels "app.kubernetes.io/name" (include "validation-service.name" .) }}
{{- $_ = set $labels "app.kubernetes.io/instance" .Release.Name }}
{{- $_ = set $labels "fhirProfile" .Values.required.packages.name }}
{{- $_ = set $labels "fullVersionName" (include "validation-service.fullversionname" .) }}
{{- toYaml $labels }}
{{- end }}

{{/*
Deployment labels
*/}}
{{- define "validation-service.deploymentLabels" -}}
{{- $labels := dict "istio-validate-jwt" (.Values.istio.validateJwt | required ".Values.istio.validateJwt is required" | toString) }}
{{- $_ := mergeOverwrite $labels ( include "validation-service.versionLabels" . | fromYaml ) }}
{{- with .Values.deploymentLabels }}
{{- $_ := mergeOverwrite $labels . }}
{{- end }}
{{- toYaml $labels }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "validation-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "validation-service.fullversionname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}


{{/*
generate profile version name
*/}}
{{- define "validation-service.packageVersionName" -}}
{{- if gt (len .Values.required.packages.versions) 1 }}
{{- printf "%s-%s" (regexReplaceAll "(\\.|_)+" .Chart.Version "-") (regexReplaceAll "(\\.|_)+" .Values.required.packages.name "-") | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- .Values.required.packages.versions | first | trunc 63 | trimSuffix "-" }}
{{- end}}
{{- end -}}

{{/*
Environment Variables
*/}}
{{- define "validation-service.env" -}}
{{- $envs := dict -}}
{{- $envs = set $envs "PACKAGE_VERSIONS" (join "," .Values.required.packages.versions) -}}
{{- if .Values.customEnvVars -}}
{{- range $key, $value := .Values.customEnvVars -}}
{{- if not (kindIs "invalid" $value) -}}
{{- $envs = set $envs $key $value }}
{{- end -}}
{{- end -}}
{{- end -}}
{{- if .Values.debug.enable -}}
{{- $toolOptions := printf "%s %s" (get $envs "JAVA_TOOL_OPTIONS") .Values.debug.params | trim -}}
{{- $envs = set $envs "JAVA_TOOL_OPTIONS" $toolOptions -}}
{{- end -}}
{{- range $i, $key := keys $envs | sortAlpha -}}
{{- if $i }}
{{ end -}}
{{- $v := get $envs $key -}}
- name: {{ $key | quote }}
{{- if kindIs "string" $v }}
  value: {{ tpl $v $ | quote }}
{{- else }}
  value: {{ $v | quote }}
{{- end }}
{{- end -}}
{{- end -}}
