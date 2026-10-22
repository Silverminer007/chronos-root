# Testumgebung: Angular-Frontend + Rust-Backend (Namespace `chronos-angular`)

Nur für das lokale k3s. Enthält PostgreSQL 16, Keycloak 26 (Realm `chronos` aus `chronos-realm.json`, Dev-Modus), das Rust-Backend
(`chronos-rust-backend:parity`) und das Angular-Frontend (`chronos-frontend-angular:test`, Node-BFF). Wegwerf-Zugangsdaten.

## Zugriff (SSH-Tunnel)

Browser und Cluster müssen dieselben Adressen benutzen (Token-Issuer, Cookies). Dafür gelten `keycloak.localhost:8180` und
`chronos.localhost:3300` im Browser (`*.localhost` zeigt auf den lokalen Rechner und gilt als sicherer Kontext, Service Worker laufen)
und im Cluster (`hostAliases` auf die feste ClusterIP des Keycloak-Service). Auf dem Rechner mit dem Browser:

    ssh -N -L 3300:localhost:30300 -L 8180:localhost:30180 vagrant@<VM>

Dann `http://chronos.localhost:3300` öffnen. Testnutzer (Passwort = Name): `parity-user-1`, `parity-user-2`, `parity-admin`.
Keycloak-Admin: `http://keycloak.localhost:8180` (admin/admin).

## Deploy

    docker build -t chronos-frontend-angular:test frontend-angular
    docker save chronos-frontend-angular:test | sudo k3s ctr -n k8s.io images import -
    docker build -t chronos-rust-backend:parity rust-backend && docker save chronos-rust-backend:parity | sudo k3s ctr -n k8s.io images import -
    kubectl create namespace chronos-angular
    kubectl -n chronos-angular create configmap keycloak-realm --from-file=deployment/angular-test/chronos-realm.json
    # VAPID-Schlüssel erzeugen (siehe deployment/parity/README.md) und in chronos-angular.yaml statt der Platzhalter eintragen
    kubectl apply -f deployment/angular-test/chronos-angular.yaml

Abbau: `kubectl delete namespace chronos-angular` (entfernt auch das Volume).
