# Pod-Plattform für chronos (Silverminer007/chronos-root)

Dieses Verzeichnis enthält die Konfiguration des Projekts für die Pod-Plattform des Agent Orchestrators (Repo `https://github.com/Silverminer007/agent-orchestrator.git`).
Die Plattform enthält nichts Projektspezifisches; alles Projektspezifische steht hier:

- `project.env`: Konfiguration (Namespaces, Labels, Pfade, Sidecars, Richtlinien). Vorlage mit allen Schlüsseln: `deployment/k8s/project/project.example.env` der Plattform.
- `prompts/<phase>.md`: ersetzt den Standardtext der Phase (implement, review, resolve, conflicts, rules) vollständig; optional.
- `prompts/<phase>.extra.md`: Projektregeln, werden an den Standardtext angehängt (Platzhalter wie {{REPO}}, {{TICKET}}, {{KB_DIR}} sind erlaubt).

## Checkliste

1. **Konfiguration prüfen**: `project.env` durchgehen. Pflicht: PROJECT, REPO. Typisch anzupassen: AREA_LABELS, AREA_DB, SEC_RE, CRED_RE, DB_CMD_RE, KB_DIR, RULES_FILE,
   NIGHTLY_CMD, NIGHTLY_JOBS, DB_TEST_ENV, SIDECARS, IMAGES. Rust-/Node-spezifische Werkzeuge gehören ins Projekt-Image (`IMAGES`, Dockerfile im Projekt).
2. **Labels anlegen** (einmal je Repo; `--force` aktualisiert vorhandene; FORGE=github):

```sh
gh label create "ready-for-agent" -R Silverminer007/chronos-root --force
gh label create "needs-input" -R Silverminer007/chronos-root --force
gh label create "needs-po" -R Silverminer007/chronos-root --force
gh label create "blocked" -R Silverminer007/chronos-root --force
gh label create "follow-up" -R Silverminer007/chronos-root --force
gh label create "epic" -R Silverminer007/chronos-root --force
gh label create "area:rust" -R Silverminer007/chronos-root --force
gh label create "area:java" -R Silverminer007/chronos-root --force
gh label create "area:frontend" -R Silverminer007/chronos-root --force
gh label create "area:deployment" -R Silverminer007/chronos-root --force
gh label create "area:docs" -R Silverminer007/chronos-root --force
```

3. **Secrets anlegen** (Werte nie in Dateien oder ins Repo; `--from-env-file` zeigt auf eine lokale Datei außerhalb des Repos, die nach dem Anlegen gelöscht wird).
   Der Schlüssel `GITHUB_TOKEN` hält das Token der Forge des Projekts (GitHub oder GitLab, Bereich api + write_repository). Liegt die Plattform auf einem
   anderen Host als das Projekt (z. B. Projekt auf GitLab, Plattform privat auf GitHub), kommt `AO_TOKEN` (Lesezugriff auf die Plattform) dazu:

```sh
# Namespaces zuerst anlegen (siehe Schritt 6), dann je Namespace:
kubectl -n chronos-agents  create secret generic agent-credentials    --from-env-file=<Datei mit ANTHROPIC_BASE_URL, ANTHROPIC_CUSTOM_HEADERS, GITHUB_TOKEN>
kubectl -n chronos-nightly create secret generic agent-credentials    --from-env-file=<Datei mit GITHUB_TOKEN (und LiteLLM-Werten, falls der Nightly Modelle ruft)>
kubectl -n chronos-agents  create secret generic agent-credentials-ro --from-env-file=<Datei mit GITHUB_TOKEN, nur Leserecht>
# claude-access (Schlüssel access-token, expires-at in ms): pflegt der Token-Abgleich der Plattform (deployment/k8s/token/), nicht von Hand.
```

4. **Knoten-Label** (Pods laufen nur auf Knoten mit konfiguriertem Registry-Mirror; Wert aus NODE_LABEL):

```sh
kubectl label node <knoten> agent-orchestrator.dev/registry=ready --overwrite
kubectl get nodes -L agent-orchestrator.dev/registry
```

5. **Quoten** prüfen (stehen in `deployment/k8s/manifests/project/quotas.yaml` der Plattform, je Namespace `compute`): chronos-agents 11 CPU / 16Gi Requests, 18 CPU / 52Gi Limits, 14 Pods;
   chronos-nightly 2 CPU / 4Gi Requests, 4 CPU / 9Gi Limits, 2 Pods. Der Scheduler richtet sich nach `SCHED_MAX_IMPLEMENT`, `SCHED_POD_RESERVE` und dem freien Limit-Speicher der Quote.
   Mehrere Projekte im selben Cluster: eigene Namespaces (`NS_AGENTS`, `NS_NIGHTLY`) je Projekt; Build-Namespace und PriorityClasses sind geteilt und nur beim ersten Projekt anzulegen.
6. **Image bauen und Manifeste rendern** (Hash-Tag, nie `latest`):

```sh
bash <plattform>/deployment/k8s/project/render.sh <PROJECT_DIR> <Ausgabeverzeichnis> --image-tag <Hash-Tag>
kubectl apply -f <Ausgabeverzeichnis>/manifests/shared      # nur beim ersten Projekt im Cluster
kubectl apply -f <Ausgabeverzeichnis>/manifests/project/namespaces.yaml
kubectl apply -f <Ausgabeverzeichnis>/manifests/project -R
```

7. **Driver** startet mit `PROJECT_DIR=/state/repos/project` (Klon dieses Repos) und `AO_DIR` (Klon der Plattform, Branch `AO_REF`, Standard main); der Steward ebenso (`manifests/project/control/`).
8. **Rauchtest**: `manifests/pod-templates/smoke-pod.yaml` (gerendert; Platzhalter `__COST_HEADER__` durch die Kosten-Tag-Zeile ersetzen) anwenden und die Ausgabe lesen, bevor das erste Ticket `ready-for-agent` bekommt.
