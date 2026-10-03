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

{{- define "chronos.bitwardenSecrets" -}}
- bwSecretId: {{ required "secrets.db.password is required for provider: bitwarden" .Values.secrets.db.password }}
  secretKeyName: database_password
- bwSecretId: {{ required "secrets.backend.clientId is required for provider: bitwarden" .Values.secrets.backend.clientId }}
  secretKeyName: backend_client_id
- bwSecretId: {{ required "secrets.backend.clientSecret is required for provider: bitwarden" .Values.secrets.backend.clientSecret }}
  secretKeyName: backend_client_secret
- bwSecretId: {{ required "secrets.frontend.clientId is required for provider: bitwarden" .Values.secrets.frontend.clientId }}
  secretKeyName: frontend_client_id
- bwSecretId: {{ required "secrets.frontend.sentryAuthToken is required for provider: bitwarden" .Values.secrets.frontend.sentryAuthToken }}
  secretKeyName: frontend_sentry_auth_token
- bwSecretId: {{ required "secrets.frontend.sentryDsn is required for provider: bitwarden" .Values.secrets.frontend.sentryDsn }}
  secretKeyName: frontend_sentry_dsn
{{- if .Values.secrets.imagePullSecret }}
- bwSecretId: {{ .Values.secrets.imagePullSecret }}
  secretKeyName: container_registry_secret
{{- end }}
- bwSecretId: {{ required "secrets.vapid.publicKey is required for provider: bitwarden" .Values.secrets.vapid.publicKey }}
  secretKeyName: vapid_public_key
- bwSecretId: {{ required "secrets.vapid.privateKey is required for provider: bitwarden" .Values.secrets.vapid.privateKey }}
  secretKeyName: vapid_private_key
- bwSecretId: {{ required "secrets.vapid.mailto is required for provider: bitwarden" .Values.secrets.vapid.mailto }}
  secretKeyName: vapid_mailto
{{- if .Values.secrets.backup }}
- bwSecretId: {{ required "secrets.backup.s3.bucket is required for provider: bitwarden" .Values.secrets.backup.s3.bucket }}
  secretKeyName: backup_bucket
- bwSecretId: {{ required "secrets.backup.s3.access_key_id is required for provider: bitwarden" .Values.secrets.backup.s3.access_key_id }}
  secretKeyName: backup_access_key_id
- bwSecretId: {{ required "secrets.backup.s3.secret_access_key is required for provider: bitwarden" .Values.secrets.backup.s3.secret_access_key }}
  secretKeyName: backup_secret_access_key
- bwSecretId: {{ required "secrets.backup.s3.region is required for provider: bitwarden" .Values.secrets.backup.s3.region }}
  secretKeyName: backup_region
- bwSecretId: {{ required "secrets.backup.s3.endpoint_url is required for provider: bitwarden" .Values.secrets.backup.s3.endpoint_url }}
  secretKeyName: backup_endpoint_url
{{- end }}
{{- end -}}

{{- define "chronos.valuesSecrets" -}}
database_password: {{ required "secrets.values.database_password is required for provider: values" .Values.secrets.values.database_password | quote }}
backend_client_id: {{ required "secrets.values.backend_client_id is required for provider: values" .Values.secrets.values.backend_client_id | quote }}
backend_client_secret: {{ required "secrets.values.backend_client_secret is required for provider: values" .Values.secrets.values.backend_client_secret | quote }}
frontend_client_id: {{ required "secrets.values.frontend_client_id is required for provider: values" .Values.secrets.values.frontend_client_id | quote }}
frontend_sentry_auth_token: {{ required "secrets.values.frontend_sentry_auth_token is required for provider: values" .Values.secrets.values.frontend_sentry_auth_token | quote }}
frontend_sentry_dsn: {{ required "secrets.values.frontend_sentry_dsn is required for provider: values" .Values.secrets.values.frontend_sentry_dsn | quote }}
vapid_public_key: {{ required "secrets.values.vapid_public_key is required for provider: values" .Values.secrets.values.vapid_public_key | quote }}
vapid_private_key: {{ required "secrets.values.vapid_private_key is required for provider: values" .Values.secrets.values.vapid_private_key | quote }}
vapid_mailto: {{ required "secrets.values.vapid_mailto is required for provider: values" .Values.secrets.values.vapid_mailto | quote }}
{{- if .Values.secrets.backup }}
backup_bucket: {{ required "secrets.values.backup_bucket is required for provider: values" .Values.secrets.values.backup_bucket | quote }}
backup_access_key_id: {{ required "secrets.values.backup_access_key_id is required for provider: values" .Values.secrets.values.backup_access_key_id | quote }}
backup_secret_access_key: {{ required "secrets.values.backup_secret_access_key is required for provider: values" .Values.secrets.values.backup_secret_access_key | quote }}
backup_region: {{ required "secrets.values.backup_region is required for provider: values" .Values.secrets.values.backup_region | quote }}
backup_endpoint_url: {{ required "secrets.values.backup_endpoint_url is required for provider: values" .Values.secrets.values.backup_endpoint_url | quote }}
{{- end }}
{{- end -}}
