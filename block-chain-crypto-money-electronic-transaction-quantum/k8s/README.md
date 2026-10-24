# TekmaChain on Kubernetes

Runs a single self-contained node (embedded **H2** on a PersistentVolume, no database
Pod) in a `tekmachain` namespace. Works on a local cluster (kind / minikube / Docker
Desktop) or any real cluster with a default StorageClass. For a MySQL-backed or
multi-node deployment, see the **MySQL** note below.

## 1. Build the image

```bash
docker build -t tekmachain:latest .
```

Make the image available to your cluster:

- **kind:** `kind load docker-image tekmachain:latest`
- **minikube:** `minikube image load tekmachain:latest`
- **Docker Desktop K8s:** already shares the local Docker images.
- **Real cluster:** retag & push to your registry, then set that image in `app.yaml`.

## 2. Deploy

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/app.yaml
kubectl -n tekmachain rollout status deploy/tekmachain-app
```

## 3. Open it

```bash
kubectl -n tekmachain port-forward svc/tekmachain 8080:8080
```

Then open **http://localhost:8080** (or use the NodePort `30080` on your node IP).

## Notes

- **Default is embedded H2** on a 1Gi PersistentVolumeClaim (`tekmachain-data`) — one writer,
  so the Deployment stays at `replicas: 1` with a `Recreate` rollout strategy.
- **Want MySQL instead?** `kubectl apply -f k8s/mysql.yaml`, then run the app with the `mysql`
  profile by adding `SPRING_PROFILES_ACTIVE=mysql` (plus the `TEKMACHAIN_DB_*` env from the
  ConfigMap/Secret) to the Deployment. Credentials live in the `tekmachain-db` Secret
  (`root`/`root` for the demo — change them).
- **Multi-node P2P mesh:** use `p2p-cluster.yaml` (each pod pairs the node with its own database)
  and connect the nodes via the Network tab.
