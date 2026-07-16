# Connected Vehicle Telemetry Platform — Kubernetes Manifests

Kubernetes manifests for the event-driven Spring Boot telemetry platform.
Everything runs in the `connected-vehicle` namespace.

## Contents

| File | Objects |
| --- | --- |
| `00-namespace.yaml` | Namespace `connected-vehicle` |
| `01-configmap.yaml` | ConfigMaps `platform-config`, `postgres-init`, `prometheus-config` |
| `02-secret.yaml` | Secret `platform-secrets` |
| `10-postgres.yaml` | Postgres Deployment + Service + PVC |
| `11-kafka.yaml` | Kafka (KRaft) Deployment + Service |
| `20-can-ingestion.yaml` | can-ingestion-service Deployment + Service |
| `21-dbc-decoder.yaml` | dbc-decoder-service Deployment + Service |
| `22-telemetry-storage.yaml` | telemetry-storage-service Deployment + Service |
| `23-predictive-diagnostics.yaml` | predictive-diagnostics-service Deployment + Service |
| `24-fleet-analytics.yaml` | fleet-analytics-service Deployment + Service |
| `25-api-gateway.yaml` | api-gateway-service Deployment + Service (LoadBalancer) |
| `30-prometheus.yaml` | Prometheus Deployment + Service |
| `31-grafana.yaml` | Grafana Deployment + Service (LoadBalancer) |

## 1. Build the service images

The manifests use `imagePullPolicy: IfNotPresent` with locally-tagged images
(`connected-vehicle/<service>:1.0.0`), so the images must exist in the cluster's
container runtime. They are not pushed to a registry.

Build everything with Docker Compose (recommended — it already knows each
service's Dockerfile and tag):

```bash
# from the repo root
docker compose build
```

Or build a single service by hand:

```bash
docker build \
  -t connected-vehicle/can-ingestion-service:1.0.0 \
  -f can-ingestion-service/Dockerfile \
  can-ingestion-service
```

Repeat for `dbc-decoder-service`, `telemetry-storage-service`,
`predictive-diagnostics-service`, `fleet-analytics-service`, and
`api-gateway-service`.

## 2. Load the images into your local cluster

Local clusters do not see your Docker daemon's images automatically.

**kind:**

```bash
for svc in can-ingestion-service dbc-decoder-service telemetry-storage-service \
           predictive-diagnostics-service fleet-analytics-service api-gateway-service; do
  kind load docker-image "connected-vehicle/${svc}:1.0.0"
done
```

**minikube:**

```bash
for svc in can-ingestion-service dbc-decoder-service telemetry-storage-service \
           predictive-diagnostics-service fleet-analytics-service api-gateway-service; do
  minikube image load "connected-vehicle/${svc}:1.0.0"
done
```

The public images (kafka, postgres, prometheus, grafana) are pulled from
Docker Hub automatically.

## 3. Apply the manifests

`kubectl apply -f k8s/` applies every file in the directory. It does not
guarantee ordering, so apply the foundational objects (namespace, config,
secret) first to be safe:

```bash
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-configmap.yaml
kubectl apply -f k8s/02-secret.yaml

# then the rest
kubectl apply -f k8s/
```

Watch the rollout:

```bash
kubectl -n connected-vehicle get pods -w
```

Postgres and Kafka come up first; the Spring services have generous
`initialDelaySeconds` on their probes to tolerate the startup ordering
(they retry their Kafka/Postgres connections). It is normal for the app
pods to restart a couple of times until Kafka is ready.

## 4. Access the platform

Both the API gateway and Grafana are exposed as `LoadBalancer` services.
On a cloud cluster an external IP is provisioned automatically. On a local
cluster (kind/minikube) use port-forwarding:

```bash
# API gateway -> http://localhost:8080
kubectl -n connected-vehicle port-forward svc/api-gateway-service 8080:8080

# Grafana -> http://localhost:3000  (admin / admin)
kubectl -n connected-vehicle port-forward svc/grafana 3000:3000

# Prometheus -> http://localhost:9090
kubectl -n connected-vehicle port-forward svc/prometheus 9090:9090
```

## 5. Switch the CAN ingestion mode

`CAN_MODE` (and the simulator tuning `SIM_VEHICLE_COUNT` / `SIM_RATE_HZ`)
lives in the `platform-config` ConfigMap. Edit it and restart ingestion:

```bash
kubectl -n connected-vehicle edit configmap platform-config
# change CAN_MODE (e.g. simulator -> replay), save

kubectl -n connected-vehicle rollout restart deployment/can-ingestion-service
```

(Pods pick up ConfigMap changes only on restart, since the values are
injected via `envFrom`.)

## 6. Scale a stateless consumer

The decoder, storage, diagnostics and analytics services are stateless
Kafka consumers and can be scaled horizontally — Kafka distributes
partitions across the consumer group:

```bash
kubectl -n connected-vehicle scale deployment/dbc-decoder-service --replicas=3
kubectl -n connected-vehicle scale deployment/telemetry-storage-service --replicas=2
```

Do **not** scale `postgres` or `kafka` (single-node stateful components).

## 7. Troubleshooting

```bash
# Overall status
kubectl -n connected-vehicle get pods,svc

# Logs for a service (follow)
kubectl -n connected-vehicle logs -f deployment/api-gateway-service

# Describe a crash-looping pod (events + probe failures)
kubectl -n connected-vehicle describe pod <pod-name>
```

Common issues:

- **App pods CrashLoopBackOff early on** — usually Kafka or Postgres is not
  ready yet. Confirm `kafka` and `postgres` pods are `Running` and `READY 1/1`,
  then the app pods recover. Check Kafka readiness:
  `kubectl -n connected-vehicle get pod -l app=kafka`.
- **`ImagePullBackOff` / `ErrImageNeverPull`** — the local image was not loaded
  into the cluster. Re-run step 2 (`kind load` / `minikube image load`).
- **Postgres schema missing** — the `postgres-init` ConfigMap only runs on an
  empty data directory. Delete the `postgres-data` PVC and redeploy to
  re-initialize: `kubectl -n connected-vehicle delete pvc postgres-data`.
- **No metrics in Prometheus** — verify targets are UP at
  `http://localhost:9090/targets` after port-forwarding.
