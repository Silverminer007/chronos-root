#!/bin/bash
# restore.sh - PostgreSQL Datenbank aus S3-Backup wiederherstellen

set -e

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║        ⚠️   POSTGRESQL DATENBANK RESTORE   ⚠️             ║"
echo "║                                                          ║"
echo "║  Dieser Job löscht die gesamte Datenbank und spielt     ║"
echo "║  das aktuellste S3-Backup ein!                          ║"
echo "║                                                          ║"
echo "║  ALLE AKTUELLEN DATEN GEHEN VERLOREN!                   ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# ── Namespace abfragen ────────────────────────────────────────────────────────
echo "Verfügbare Namespaces:"
kubectl get namespaces --no-headers -o custom-columns=":metadata.name" \
  | grep -E "chronos" \
  | sed 's/^/  - /'
echo ""
read -p "Ziel-Namespace eingeben: " NAMESPACE

if [ -z "$NAMESPACE" ]; then
  echo "❌ Kein Namespace angegeben. Abgebrochen."
  exit 1
fi

# Prüfen ob Namespace existiert
if ! kubectl get namespace "$NAMESPACE" &>/dev/null; then
  echo "❌ Namespace '$NAMESPACE' existiert nicht. Abgebrochen."
  exit 1
fi

# Prüfen ob der CronJob im Namespace existiert
if ! kubectl get cronjob postgres-restore-trigger -n "$NAMESPACE" &>/dev/null; then
  echo "❌ CronJob 'postgres-restore-trigger' nicht gefunden in Namespace '$NAMESPACE'. Abgebrochen."
  exit 1
fi

echo ""
echo "  Namespace: $NAMESPACE"
echo ""

# ── Bestätigung abfragen ──────────────────────────────────────────────────────
read -p "Bestätigung eingeben (YES_I_WANT_TO_RESTORE): " CONFIRM

if [ "$CONFIRM" != "YES_I_WANT_TO_RESTORE" ]; then
  echo "❌ Falsche Bestätigung. Abgebrochen."
  exit 1
fi

# ── Job erstellen ─────────────────────────────────────────────────────────────
JOB_NAME="postgres-restore-$(date +%s)"
echo ""
echo "🚀 Erstelle Job '$JOB_NAME' in Namespace '$NAMESPACE'..."

kubectl create job "$JOB_NAME" \
  --from=cronjob/postgres-restore-trigger \
  -n "$NAMESPACE"

echo "✅ Job '$JOB_NAME' gestartet!"
echo ""
echo "📋 Logs verfolgen:"
echo "  kubectl logs -f job/$JOB_NAME -c s3-download -n $NAMESPACE"
echo "  kubectl logs -f job/$JOB_NAME -c postgres-restore -n $NAMESPACE"
echo ""
echo "📊 Status beobachten:"
echo "  kubectl get pod -n $NAMESPACE -w -l job-name=$JOB_NAME"
echo ""

# ── Optional: Logs direkt verfolgen ──────────────────────────────────────────
read -p "Logs jetzt direkt verfolgen? (j/n): " FOLLOW_LOGS

if [ "$FOLLOW_LOGS" = "j" ]; then
  echo ""
  echo "⏳ Warte auf Pod-Start..."
  kubectl wait --for=condition=Ready pod \
    -l job-name="$JOB_NAME" \
    -n "$NAMESPACE" \
    --timeout=60s 2>/dev/null || true

  echo "── s3-download ───────────────────────────────────────────"
  kubectl logs -f "job/$JOB_NAME" -c s3-download -n "$NAMESPACE" || true

  echo "── postgres-restore ──────────────────────────────────────"
  kubectl logs -f "job/$JOB_NAME" -c postgres-restore -n "$NAMESPACE" || true
fi

echo "⏳ Warte auf Pod-Completion..."
kubectl wait --for=condition=Complete pod \
  -l job-name="$JOB_NAME" \
  -n "$NAMESPACE" \
  --timeout=300s 2>/dev/null || true
kubectl delete job -n "$NAMESPACE" $JOB_NAME
kubectl delete pod -n "$NAMESPACE" -l job-name="$JOB_NAME"