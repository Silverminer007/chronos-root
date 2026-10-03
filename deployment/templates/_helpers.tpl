{{- define "chronos.fullname" -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "chronos.labels" -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- define "chronos.secretName" -}}
{{- if .Values.secrets.existingSecretName -}}
{{- .Values.secrets.existingSecretName -}}
{{- else -}}
chronos-secret
{{- end -}}
{{- end -}}

{{- define "chronos.imagePullSecretName" -}}
{{- if and .Values.imagePullSecret.name (ne .Values.imagePullSecret.name "") -}}
{{- .Values.imagePullSecret.name -}}
{{- else -}}
{{- end -}}
{{- end -}}
