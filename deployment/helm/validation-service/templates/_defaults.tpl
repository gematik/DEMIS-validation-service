{{/* Check required values and define variables */}}
{{- define "init" }}
{{- $defaults := dict "defaults" dict -}}

{{- $_ := set $defaults.defaults "currentProvisioningMode" .Values.provisioningMode -}}
{{- $_ = set $defaults.defaults "dockerRepository" .Values.required.image.repository -}}
{{- $_ = set $defaults.defaults "dockerImage" .Values.required.image.name -}}
{{- $_ = set $defaults.defaults "dockerTag" (.Values.required.image.tag | default .Chart.AppVersion) -}}
{{- $_ = set $defaults.defaults "customEnvVars" .Values.customEnvVars -}}
{{- $_ = set $defaults.defaults "dockerProfilesRepository" .Values.required.profiles.repository -}}
{{- $_ = set $defaults.defaults "profilesName" (.Values.required.profiles.imageName | default .Values.required.profiles.name) -}}
{{- $_ = set $defaults.defaults "profilesVersions" (.Values.required.profiles.versions | default (list .Values.required.profiles.version) | uniq) -}}

{{- $_ = mustMergeOverwrite . $defaults  }}

{{- end }}
