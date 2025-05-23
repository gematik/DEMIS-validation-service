{{/*
Expand the name of the chart.
*/}}
{{- define "validation-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "validation-service.fullversionname" -}}
{{- $name := .Values.fullName }}
{{- $version := regexReplaceAll "\\.+" .Values.version "-" }}
{{- printf "%s-%s" $name $version | trunc 63 }}
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
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- with .Values.customLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "validation-service.selectorLabels" -}}
app: {{ .Values.fullName }}
{{- end }}

{{/*
Detect if the external routing is defined
*/}}
{{- define "istio.service.external.match" -}}
{{- if (hasKey .Values.istio.virtualService "externalHttp") -}}
{{- toYaml .Values.istio.virtualService.externalHttp.match }}
{{- else -}}
{{- toYaml .Values.istio.virtualService.http.match }}
{{- end -}}
{{- end -}}

{{/*
Detect if the external rewrite uri is defined
*/}}
{{- define "istio.service.external.rewrite.url" -}}
{{- if (hasKey .Values.istio.virtualService "externalHttp") -}}
rewrite:
  uri: {{ .Values.istio.virtualService.externalHttp.rewrite.uri }}
{{- else -}}
rewrite:
  uri: {{ .Values.istio.virtualService.http.rewrite.uri }}
{{- end -}}
{{- end -}}

{{/*
Detect if the external timeout is defined
*/}}
{{- define "istio.service.external.timeout" -}}
{{- if (and (hasKey .Values.istio.virtualService "externalHttp") (.Values.istio.virtualService.externalHttp.timeout)) -}}
timeout: {{ .Values.istio.virtualService.externalHttp.timeout }}
{{- else if (and (hasKey .Values.istio.virtualService "http") (.Values.istio.virtualService.http.timeout)) -}}
timeout: {{ .Values.istio.virtualService.http.timeout }}
{{- end -}}
{{- end -}}

{{/*
Detect if the external retry is defined
*/}}
{{- define "istio.service.external.retry" -}}
{{- if (hasKey .Values.istio.virtualService "externalHttp") -}}
{{- if (and (hasKey .Values.istio.virtualService.externalHttp "retries") (.Values.istio.virtualService.externalHttp.retries.enable)) -}}
retries:
  attempts: {{ .Values.istio.virtualService.externalHttp.retries.attempts | default 0 }}
  {{- if .Values.istio.virtualService.http.retries.perTryTimeout }}
  perTryTimeout: {{ .Values.istio.virtualService.http.retries.perTryTimeout }}
  {{- end }}
  {{- if .Values.istio.virtualService.externalHttp.retries.retryOn }}
  retryOn: {{ .Values.istio.virtualService.externalHttp.retries.retryOn }}
  {{- end -}}
{{- else if (and (hasKey .Values.istio.virtualService.externalHttp "retries") (not .Values.istio.virtualService.externalHttp.retries.enable)) -}}
retries:
  attempts: 0
{{- end -}}
{{- else if (hasKey .Values.istio.virtualService "http") -}}
{{- if (and (hasKey .Values.istio.virtualService.http "retries") (.Values.istio.virtualService.http.retries.enable)) -}}
retries:
  attempts: {{ .Values.istio.virtualService.http.retries.attempts | default 0 }}
  {{- if .Values.istio.virtualService.http.retries.perTryTimeout }}
  perTryTimeout: {{ .Values.istio.virtualService.http.retries.perTryTimeout }}
  {{- end }}
  {{- if .Values.istio.virtualService.http.retries.retryOn -}}
  retryOn: {{ .Values.istio.virtualService.http.retries.retryOn }}
  {{- end -}}
{{- else if (and (hasKey .Values.istio.virtualService.http "retries") (not .Values.istio.virtualService.http.retries.enable)) -}}
retries:
  attempts: 0
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
generate subset name from subset
*/}}
{{- define "istio.generate-subset-name" -}}
{{- if (and (hasKey . "fhirProfile") (hasKey . "fhirProfileVersion") ) }}
{{- $profileVersionNames := regexSplit "_" .fhirProfileVersion -1 }}
{{- $profileVersionName := regexReplaceAll "(\\.|_)+" (first $profileVersionNames) "-" }}
{{- $profileVersionSuffix := "" }}
{{- if ( gt (len $profileVersionNames) 1 ) }}
{{- $profileVersionSuffix = join "" (rest (regexSplit "_" .fhirProfileVersion -1)) | sha256sum | trunc 10 }}
{{- end }}
{{- printf "%s-%s-%s-%s" (regexReplaceAll "(\\.|_)+" .version "-") (regexReplaceAll "(\\.|_)+" .fhirProfile "-") $profileVersionName $profileVersionSuffix | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- regexReplaceAll "(\\.|_)+" .version "-" | trunc 63 | trimSuffix "-" }}
{{- end}}
{{- end -}}