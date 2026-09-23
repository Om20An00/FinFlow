<div align="center">

# 💳 Event-Driven Distributed Payment Platform

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:4A2E1F,50:8B5E3C,100:E8C4A2&height=220&section=header&text=FinFlow&fontSize=42&fontColor=ffffff&animation=fadeIn&fontAlignY=35&desc=Java%2021%20%7C%20Spring%20Boot%203%20%7C%20gRPC%20%7C%20Kafka%20%7C%20Kubernetes&descAlignY=55&descSize=18" />

<a href="https://github.com/Om20An00/FinFlow">
  <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=22&pause=1000&color=8B5E3C&center=true&vCenter=true&width=650&lines=Event-Driven+Distributed+Payment+Platform;Transactional+Outbox+%2B+Idempotent+gRPC+Transfers;Kafka-Driven+Audit+%26+Notifications;Kubernetes-Ready+with+HPA+%26+Observability" alt="Typing SVG" />
</a>

<br/>

![Java](https://img.shields.io/badge/Java-21-4A2E1F?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3-6B4226?style=for-the-badge&logo=springboot&logoColor=white)
![gRPC](https://img.shields.io/badge/gRPC-Internal_RPC-8B5E3C?style=for-the-badge&logo=google&logoColor=white)
![Kafka](https://img.shields.io/badge/Kafka-Event_Bus-A9714D?style=for-the-badge&logo=apachekafka&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database_per_Service-C68863?style=for-the-badge&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-Cache_%2F_Idempotency-D9A579?style=for-the-badge&logo=redis&logoColor=white)
![Keycloak](https://img.shields.io/badge/Keycloak-OAuth2%2FOIDC-8B5E3C?style=for-the-badge&logo=keycloak&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-6B4226?style=for-the-badge&logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-HPA_%2F_Probes-4A2E1F?style=for-the-badge&logo=kubernetes&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-Dashboards-A9714D?style=for-the-badge&logo=grafana&logoColor=white)

![GitHub repo size](https://img.shields.io/github/repo-size/Om20An00/FinFlow?style=flat-square&color=8B5E3C)
![GitHub last commit](https://img.shields.io/github/last-commit/Om20An00/FinFlow?style=flat-square&color=8B5E3C)
![GitHub stars](https://img.shields.io/github/stars/Om20An00/FinFlow?style=flat-square&color=C68863)
![License](https://img.shields.io/badge/license-MIT-4A2E1F?style=flat-square)

### 🔗 [**Demo UI — localhost:3000**](http://localhost:3000) &nbsp;·&nbsp; [**Grafana — localhost:3001**](http://localhost:3001) &nbsp;·&nbsp; run it with one command below ⬇️

</div>

---

## 📖 About This Project

**FinFlow** is a production-style fintech backend built to demonstrate real distributed systems engineering. It's a full payment & wallet platform split across **seven Spring Boot microservices**, talking to each other over **gRPC** and **Kafka**, coordinated through a **transactional outbox → Kafka** pipeline, secured with **OAuth2/OIDC (Keycloak)**, and deployable to **Kubernetes** with autoscaling and health probes.

> 🧠 **Author's note:** Every service — gateway, user, wallet, payment, notification, audit, and analytics — the outbox relay, the idempotency layer, the gRPC contract, and the observability stack were designed and built end to end as a hands-on deep dive into how real payment infrastructure handles consistency, failure, and scale.

---

## 🏗️ Architecture

```mermaid
flowchart TB
  subgraph EDGE["Edge Layer"]
    direction LR
    UI["Browser UI (OAuth2 + PKCE)"]
    KC[("Keycloak — OIDC Provider")]
  end

  UI -->|JWT bearer token| GW

  subgraph GATEWAY["API Gateway"]
    GW["Spring Cloud Gateway — JWT / RBAC / Rate Limit"]
  end

  KC -. JWKS: each service validates JWT .-> GW

  subgraph CORE["Core Domain Services"]
    direction LR
    US["User Service (users_db)"]
    WS["Wallet Service (wallet_db, Redis)"]
    PS["Payment Service (payment_db, Redis)"]
  end

  subgraph DOWNSTREAM["Downstream Services"]
    direction LR
    NS["Notification Service (Redis)"]
    AS["Audit Service (audit_db)"]
    AN["Analytics Service (Redis)"]
  end

  GW --> US
  GW --> WS
  GW --> PS
  GW -.REST reads.-> NS
  GW -.REST reads.-> AS
  GW -.REST reads.-> AN
  PS ==>|gRPC Transfer| WS

  subgraph BUS["Event Backbone"]
    K{{"Kafka"}}
  end

  PS -.outbox: payments.-> K
  WS -.outbox: wallet-events.-> K
  US -.outbox: user-events.-> K
  K -.user-events.-> WS

  K --> NS
  K --> AS
  K --> AN
  AS -.dead letters.-> DLT[["*.DLT"]]

  subgraph OBS["Observability"]
    direction LR
    P["Prometheus"] --> G["Grafana"]
  end

  GW -.metrics.-> P
  CORE -.metrics.-> P
  DOWNSTREAM -.metrics.-> P
```

> **Reading the diagram:** the gateway proxies REST calls to **all seven** services (not just the three domain services) notification, audit and analytics each expose their own `/api/v1/...` read endpoints in addition to consuming Kafka. gRPC is used for exactly one call: Payment → Wallet `Transfer`. Every service not only the gateway independently validates the Keycloak JWT via JWKS (defense in depth), and every service exposes `/actuator/prometheus` for scraping.

---

## 📸 Demo

<div align="center">

| Landing Page | User Dashboard |
|:---:|:---:|
| <img src="https://github.com/Om20An00/FinFlow/blob/db49bba518b5fb8e2ce351c07ae834cb7155fac3/Demo%20Pics/0.png" width="420"/> | <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/3.png" width="420"/> |

| Live Notifications & Wallet Ledger | Payment History |
|:---:|:---:|
| <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/4.png" width="420"/> | <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/5.png" width="420"/> |

| Demo Lab — Chaos & Resilience Tests | Demo Lab — Live Console Output |
|:---:|:---:|
| <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/7.png" width="420"/> | <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/10.png" width="420"/> |

| Admin & Audit — Freeze / Trace / DLQ | Real-Time Analytics Dashboard |
|:---:|:---:|
| <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/14.png" width="420"/> | <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/13.png" width="420"/> |

| In-App Architecture View | Prometheus — Service Discovery |
|:---:|:---:|
| <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/6.png" width="420"/> | <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/15.png" width="420"/> |

| Grafana — Payments & Latency | Grafana — Resilience & Outbox Metrics |
|:---:|:---:|
| <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/17.png" width="420"/> | <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/16.png" width="420"/> |

| Kafka UI — Topics & Dead Letter Queues | Kafka UI — Consumer Groups |
|:---:|:---:|
| <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/21.png" width="420"/> | <img src="https://raw.githubusercontent.com/Om20An00/FinFlow/c971905b45dca8b2efbbaa21ce4604130658a12a/Demo%20Pics/23.png" width="420"/> |

</div>

---

## ✨ Features

| Category | What's Implemented |
|---|---|
| **Distributed transactions** | Transactional outbox pattern (`OutboxService`, `OutboxRelay` with `FOR UPDATE SKIP LOCKED`) — no dual write between Postgres and Kafka |
| **Service-to-service RPC** | gRPC with deadlines and correlation-id propagation, used for the Payment → Wallet `Transfer` call (`wallet.proto`, `WalletClient`) — REST/JSON everywhere else, including at the browser edge |
| **Idempotency** | Redis fast-path cache + unique DB constraint on payment creation (`PaymentService`), a `transfer_log` table on wallet transfers (`WalletService`), idempotent Kafka consumers keyed on `event_id` |
| **Concurrency control** | Optimistic locking (`@Version`) on `Wallet` and `Payment` rows, with jittered retry (up to 8 attempts) on conflict |
| **Reconciliation** | Scheduled job retries `PENDING` payments left over from an ambiguous gRPC failure — safe because `Transfer` is idempotent on payment id |
| **Kafka resilience** | 3 retries → dead-letter topics (`<topic>.DLT`), manual ack, DLQ archiving in the audit consumer |
| **AuthN/AuthZ** | OAuth2/OIDC via Keycloak; JWT validated independently at every service (the gateway is not the only trust boundary); RBAC via `@PreAuthorize` and gateway route rules |
| **Rate limiting** | Redis token-bucket limiter per authenticated user, applied per route at the API gateway |
| **Observability** | Actuator + Micrometer custom metrics (`payment_success_total`, `wallet_operation_latency`, `kafka_dead_letters_total`, `outbox_published_total`, …) from every service, scraped by Prometheus, visualized in a provisioned Grafana dashboard, correlation id traced across HTTP → gRPC → Kafka |
| **Database per service** | Isolated Postgres databases/logins for user, wallet, payment and audit services, each with its own Flyway migration (`V1__init.sql`) |
| **Deployment** | Docker Compose for local dev; Kubernetes manifests with Deployments, readiness/liveness probes, resource limits, HPA on the horizontally-scaled services (gateway, wallet, payment, notification), and a headless Service for gRPC client-side load balancing |

---

## 🛠️ Tech Stack

<div align="center">

![Java](https://skillicons.dev/icons?i=java)
![Spring](https://skillicons.dev/icons?i=spring)
![Postgres](https://skillicons.dev/icons?i=postgres)
![Redis](https://skillicons.dev/icons?i=redis)
![Kafka](https://skillicons.dev/icons?i=kafka)
![Docker](https://skillicons.dev/icons?i=docker)
![Kubernetes](https://skillicons.dev/icons?i=kubernetes)
![Grafana](https://skillicons.dev/icons?i=grafana)
![Prometheus](https://skillicons.dev/icons?i=prometheus)

</div>

---

## 🚀 Run It (One Command)

**Requirements:** Docker Desktop (or Docker Engine + compose plugin), ~8 GB RAM free. Nothing else — Maven and JDK 21 run inside Docker.

```bash
git clone https://github.com/Om20An00/FinFlow.git
cd FinFlow
./start.sh            # or: docker compose up --build -d
```

First build takes ~5–10 minutes (Maven downloads dependencies once, then it's cached). Then open **http://localhost:3000**.

| What | URL | Login |
|---|---|---|
| 🖥️ Demo UI | http://localhost:3000 | `alice/alice123` · `bob/bob123` · `merchant/merchant123` · `admin/admin123` |
| 🌐 API gateway | http://localhost:8080 | Bearer JWT |
| 🔐 Keycloak console | http://localhost:8180 | `admin/admin` |
| 📊 Grafana | http://localhost:3001 | anonymous dashboard *FinFlow · Platform Overview* |
| 📨 Kafka UI | http://localhost:8090 | – |
| 📈 Prometheus | http://localhost:9090 | – |

Stop and wipe everything: `./stop.sh` &nbsp;·&nbsp; end-to-end smoke test: `./scripts/smoke-test.sh`

---

## 🎬 Demo Script (≈4 minutes)

1. **Sign in** as `alice` — note the redirect to the real Keycloak login page (OIDC + PKCE).
2. **Send money** to Bob — watch the notification land (Kafka → Redis) and the ledger update. Badges show whether each read was served from Redis or Postgres.
3. **Demo Lab → Idempotent payments**: fire the same request twice with the same `Idempotency-Key`, get charged once. **Concurrent transfers**: 6 parallel ₹10 payments from the same wallet all settle correctly, guarded by `@Version` optimistic locking.
4. **Demo Lab → Rate limiting**: 30 parallel requests trigger `429`s from the gateway's Redis token bucket. **RBAC**: `403` as Alice on an admin action. **No token**: `401`.
5. Send a payment with `#poison` in the note → sign in as `admin` → **Admin & Audit**: inspect the dead-lettered message, freeze Bob's wallet, trace the correlation id across services.
6. Open **Grafana** (payments/min, gRPC latency, consumer lag, DLQ counter) and **Kafka UI** (topics, consumer groups, `payments.DLT`).
7. `docker compose ps` — everything healthy. On Kubernetes: `kubectl scale deployment payment-service --replicas=4` and watch it stay there (or get reconciled back by the HPA once CPU load settles).

---

## 🧭 Feature → Code Map

| Topic | Implementation |
|---|---|
| OAuth2 / OIDC, JWT, RBAC | `infra/keycloak/finflow-realm.json`, `common/.../SecurityConfig.java`, `gateway/.../GatewaySecurityConfig.java`, `@PreAuthorize` on controllers |
| gRPC | `proto/src/main/proto/wallet.proto`, `wallet-service/.../grpc/*`, `payment-service/.../grpc/WalletClient.java` |
| Transactional outbox | `outbox/` module — `OutboxService`, `OutboxRelay` (`FOR UPDATE SKIP LOCKED`) |
| Idempotency | `PaymentService.create` (Redis + unique constraint), `WalletService.transfer` (`transfer_log`), idempotent Kafka consumers |
| Optimistic locking | `Wallet.@Version`, `Payment.@Version`, retry loop in `WalletGrpcService` |
| Reconciliation | `PaymentService.reconcilePending` |
| Kafka retry / DLQ | `KafkaCommonConfig` (3 retries, `<topic>.DLT`), `NotificationConsumer`, `AuditConsumer.onDeadLetter` |
| Database per service + Flyway | `*/src/main/resources/db/migration/V1__init.sql`, `infra/postgres/init.sql` |
| Redis | Wallet read cache (15s TTL), payment idempotency cache, gateway rate limiter, notifications feed, analytics counters |
| Rate limiting | Gateway `RequestRateLimiter` — Redis token bucket per user |
| Observability | Actuator + Micrometer on every service, custom metrics, Grafana dashboard, correlation id across HTTP → gRPC → Kafka |
| Docker / Kubernetes | `Dockerfile`, `docker-compose.yml`, `k8s/` — Deployments, probes, resources, HPA, headless Service for gRPC LB |

---

## ☸️ Kubernetes (Optional)

```bash
kind create cluster --name finflow     # or: minikube start --cpus 4 --memory 8192
./k8s/deploy.sh
kubectl -n finflow port-forward svc/ui 3000:80 &
kubectl -n finflow port-forward svc/gateway 8080:8080 &
kubectl -n finflow port-forward svc/keycloak 8180:8080 &
```

`deploy.sh` builds the images locally and applies the manifests via `kubectl` — there is currently no automated CD pipeline; GitHub Actions runs build/test and Docker image builds only (`push: false`).

---

## 🌱 Design Decisions & Trade-offs (Interview Material)

- **Why gRPC only for Payment → Wallet?** Binary, typed, HTTP/2 deadlines for the one latency-sensitive, high-volume internal call; REST/JSON is simpler everywhere else, including at the browser edge.
- **Why an outbox?** A DB transaction can't span PostgreSQL and Kafka. Writing the event in the same transaction and relaying it later gives at-least-once delivery without dual-write bugs — consumers are idempotent, so duplicates are harmless.
- **Why is the gRPC call outside the DB transaction?** Holding a connection/locks during a network call is an availability risk. The trade-off is the `PENDING` state plus the reconciler.
- **Why optimistic locking?** Wallet contention is low; `@Version` avoids row locks and deadlocks, and conflicts retry with jittered backoff.
- **Why does every service re-validate the JWT?** The gateway isn't a trust boundary you should rely on alone.
- **Known simplifications:** one Postgres container hosting four databases (separate logins), single-broker Kafka, Keycloak in dev mode, HTTP instead of TLS, no service mesh/circuit breaker (Resilience4j is the natural next step), 15s wallet cache TTL, no automated deployment pipeline (image build/push is CI-only; cluster deploy is manual via `k8s/deploy.sh`). Each is called out because you should be able to explain what you'd change for production.

---

## 🧪 Troubleshooting

- `docker compose logs -f <service>` (e.g. `payment-service`). Services wait on Postgres/Kafka/Redis health checks, so the first minute of "restarts" is really just slow startup.
- **Login redirect fails:** open the UI at exactly `http://localhost:3000` (the Keycloak redirect URI) with Keycloak at `http://localhost:8180`.
- **401 after login:** token issuer must be `http://localhost:8180/realms/finflow` (`KC_HOSTNAME_URL`) — don't change the Keycloak port mapping without updating `ISSUER` and `ui/public/config.js`.
- **Port already in use:** change the left side of the `ports:` mappings in `docker-compose.yml` (and `ui/public/config.js`).
- **Out of memory:** give Docker at least 6–8 GB (Settings → Resources).

---

## 🔮 Roadmap

- [ ] Resilience4j circuit breakers on the gRPC client
- [ ] mTLS between services (currently plaintext HTTP/gRPC internally)
- [ ] Multi-broker Kafka cluster
- [ ] Automated deployment (CD) to Kubernetes from GitHub Actions
- [ ] Saga-style multi-hop transfers (beyond single wallet-to-wallet)
- [ ] Contract tests for the gRPC + Kafka event schemas

---

## 👤 Author

**Om** — [@Om20An00](https://github.com/Om20An00)

Every service, the outbox relay, the idempotency layer, and the observability stack in this repository were designed and built end-to-end as an independent, hands-on project.

<div align="center">
