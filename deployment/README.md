# Chronos Deployment

Dieses Deployment nutzt GitHub Actions um den helm chart im Cluster zu aktualisieren

Damit das möglich ist, müssen die Cluster Zugangsdaten hinterlegt werden:

```bash
# 1. Service Account erstellen
kubectl apply -f github-actions-rbac.yaml

# 2. Secrets extrahieren
kubectl get secret github-actions-deployer-token -n chronos-prod -o jsonpath='{.data.token}' | base64 -d
# => KUBE_TOKEN

kubectl get secret github-actions-deployer-token -n chronos-prod -o jsonpath='{.data.ca\.crt}' | base64 -d  
# => KUBE_CA_CERT

kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}'
# => KUBE_SERVER
```

Diese Variablen müssen als Actions Secret im Repo angelegt werden