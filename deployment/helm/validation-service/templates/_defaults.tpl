{{/* Check required values and define variables */}}
{{- define "init" }}
{{- $currentProvisioningMode := .Values.provisioningMode | default "dedicated" -}}
{{ if not (has $currentProvisioningMode (list "dedicated" "combined" "distributed")) }}
{{- fail (printf "Invalid provisioningMode '%s'. Valid values are: 'dedicated', 'combined', 'distributed'." $currentProvisioningMode) -}}
{{- end }}
{{- $defaults := dict "defaults" dict -}}

{{- $_ := set $defaults.defaults "currentProvisioningMode" $currentProvisioningMode -}}
{{- $_ = set $defaults.defaults "dockerRepository" (.Values.required.image.repository | required ".Values.required.image.repository is required.") -}}
{{- $_ = set $defaults.defaults "dockerImage" (.Values.required.image.name | required ".Values.required.image.name is required.") -}}
{{- $_ = set $defaults.defaults "dockerTag" (.Values.required.image.tag | default .Chart.AppVersion) -}}
{{- $_ = set $defaults.defaults "customEnvVars" .Values.customEnvVars -}}
{{- $_ = set $defaults.defaults "dockerProfilesRepository" (.Values.required.profiles.repository | required ".Values.required.profiles.repository is required.") -}}
{{- $_ = set $defaults.defaults "profilesName" (.Values.required.profiles.imageName | default ( .Values.required.profiles.name | required ".Values.required.profiles.name is required.")) -}}
{{- $_ = set $defaults.defaults "profilesVersions" (.Values.required.profiles.versions | default (list (.Values.required.profiles.version | required ".Values.required.profiles.versions xor .Values.required.profiles.version is required")) | uniq) -}}

{{- $_ = mustMergeOverwrite . $defaults  }}

{{- end }}