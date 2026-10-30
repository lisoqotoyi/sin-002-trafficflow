# TrafficFlow

## Overview

Urban traffic light control and congestion-based routing.

Domain entities: intersections, districts, signal types.

Every class in this repo lives in a single flat package, `co.wethinkcode.trafficflow`. TrafficFlow is built
as a small set of independent services, following a growth path from simple data
cleanup through synchronous REST calls to asynchronous MQ decoupling and alerting:

1. clean a messy legacy CSV export (`intersections-legacy.csv`) — handled by **IngestionServiceApp**
2. serve it up and act on it, via three REST services calling each other directly
   over HTTP
3. decouple the relevant services with an ActiveMQ topic (`congestion-topic`) instead of
   direct calls — shared broker setup lives in [`common/`](common)
4. raise the alarm on failure — handled by **IntersectionWatchdogApp**

| Service | Folder | Port | Role |
|---|---|---|---|
| IngestionServiceApp | [`ingestion-service/`](ingestion-service) | 7020 | Parses and cleans `intersections-legacy.csv` |
| IntersectionServiceApp | [`intersection-service/`](intersection-service) | 7021 | Validates intersection/district names (source of truth). |
| CongestionServiceApp | [`congestion-service/`](congestion-service) | 7022 | Tracks the city-wide Congestion Level (0-8). |
| RoutingServiceApp | [`routing-service/`](routing-service) | 7023 | Provides estimated travel times based on congestion and intersection. |
| IntersectionWatchdogApp | [`intersection-watchdog/`](intersection-watchdog) | 7024 | cries for help if the Intersection Service crashes, since routes can no longer be validated. |

Plus [`common/`](common) (no port) — the shared ActiveMQ broker and MQ config notes
for `congestion-topic`: Routing Service becomes aware of congestion changes via an ActiveMQ Topic instead of querying Congestion Service directly.

**Status:** Stages 1-2 are implemented: ingestion cleans and serves the CSV, the
intersection and congestion services expose HTTP endpoints, and routing uses those
services to estimate travel time. Stages 3-4 (ActiveMQ topic decoupling and heartbeat
monitoring) are not implemented yet.

## Your task

Build out the four stages below, roughly in order — each stage builds on the last.
Stages 1-2 are the core exercise; stages 3-4 are stretch goals if you have time left.
Exact field names and response shapes are your call throughout — see
"Integration contracts" below for illustrative shapes, not a spec to match exactly.

| Stage | What | Required? | Rough effort |
|---|---|---|---|
| 1 | Clean `intersections-legacy.csv` in **IngestionServiceApp** and expose the cleaned records (see [`ingestion-service/README.md`](ingestion-service) for the specific data issues to handle) | Required | 1-2 hrs |
| 2 | Implement the domain endpoints in **IntersectionServiceApp**, **CongestionServiceApp**, and **RoutingServiceApp**, wired together with direct synchronous REST calls | Required | 2-3 hrs |
| 3 | Decouple Congestion → Routing with the `congestion-topic` ActiveMQ topic instead of a direct REST call (see [`common/README.md`](common)) | Stretch | 1 hr |
| 4 | Add heartbeat/dead-letter alerting in **IntersectionWatchdogApp** so it notices when the Intersection Service goes down | Stretch | 1 hr |

(Effort is a rough guide, not a hard budget — go with what feels right for your pace.)

## Integration contracts

Every place one service calls or messages another, with an illustrative shape.
None of these field names are binding — match the intent, not the exact JSON.

| From → To | Stage | Mechanism | Shape |
|---|---|---|---|
| ingestion-service → intersection-service | 1 | REST, `GET` | `GET /intersections` on ingestion-service (port 7020) → `200 OK` + JSON array of cleaned records, e.g. `[{"id": "INT-1001", "district": "Downtown", "signalType": "4-way", "active": true}, ...]`. intersection-service loads this as its canonical list. |
| routing-service → intersection-service | 2 | REST, `GET` | `GET /intersections/{id}` on intersection-service (port 7021) → `200 OK` with the record, or `404` if the id/district isn't recognized. routing-service calls this to validate a route's endpoints before estimating travel time. |
| routing-service → congestion-service | 2 | REST, `GET` | `GET /congestion` on congestion-service (port 7022) → `200 OK` + `{"level": 0-8}`. routing-service polls this per-request in stage 2. |
| congestion-service → routing-service | 3 | ActiveMQ Topic `congestion-topic` | Once stage 3 is in place, congestion-service publishes `{"level": 0-8}` to `congestion-topic` whenever the level changes, and routing-service subscribes instead of polling `GET /congestion`. Broker URL + topic name come from the shared `MqConfig` class — see [`common/README.md`](common). |
| intersection-service → intersection-watchdog | 4 | ActiveMQ Queue `intersection-heartbeat-queue` | intersection-service publishes a periodic heartbeat message to `intersection-heartbeat-queue`; intersection-watchdog consumes it and raises an alert (e.g. logs, or its own `/alert` state) if a heartbeat is missed or lands in the dead-letter queue. Queue name comes from the shared `MqConfig` class, same pattern as `congestion-topic` — see [`intersection-watchdog/README.md`](intersection-watchdog). |

## Project structure

```
trafficflow/
├── README.md
├── .gitignore
├── ingestion-service/          (port 7020)
│   ├── pom.xml
│   ├── README.md
│   └── src/main/
│       ├── java/co/wethinkcode/trafficflow/IngestionServiceApp.java
│       └── resources/intersections-legacy.csv
├── intersection-service/          (port 7021)
├── congestion-service/          (port 7022)
├── routing-service/          (port 7023)
├── common/
│   ├── docker-compose.yml
│   └── README.md
└── intersection-watchdog/          (port 7024)
```

## Build

Requirements: Java 17+, Maven 3.8+. Docker is only needed when the ActiveMQ-based
stretch stages are implemented.

Every folder here (`ingestion-service/`, each domain service, and `intersection-watchdog/`) is
an **independent** Maven project — there is no parent/aggregator pom. Build one at a
time, e.g.:

```
cd intersection-service
mvn package
```

...or build every module in the repo in one pass from the project root:

```
find . -name pom.xml -execdir mvn -q package \;
```

## Run

Build the modules once from the repository root before starting the services:

```sh
find . -name pom.xml -execdir mvn -q package \;
```

Then open **four separate terminals**, with each terminal's working directory set
to the repository root. Start the services in this order; keep each terminal open
while you use the application:

**Terminal 1 — ingestion service (port 7020):**

```sh
cd ingestion-service
java -jar target/ingestion-service.jar
```

**Terminal 2 — intersection service (port 7021):**

```sh
cd intersection-service
java -jar target/intersection-service.jar
```

**Terminal 3 — congestion service (port 7022):**

```sh
cd congestion-service
java -jar target/congestion-service.jar
```

**Terminal 4 — routing service (port 7023):**

```sh
cd routing-service
java -jar target/routing-service.jar
```

Routing calls the intersection and congestion services, and the intersection
service reads the cleaned records from ingestion. Start those three services
before routing. The watchdog (port 7024) and ActiveMQ broker are not required for
the current Stage 1-2 flow; heartbeat monitoring and MQ integration remain
unfinished.

| Service | Port |
|---|---|
| IngestionServiceApp (`ingestion-service`) | 7020 |
| IntersectionServiceApp (`intersection-service`) | 7021 |
| CongestionServiceApp (`congestion-service`) | 7022 |
| RoutingServiceApp (`routing-service`) | 7023 |
| IntersectionWatchdogApp (`intersection-watchdog`) | 7024 |

## Test

Run the ingestion CSV-cleaning unit tests from the repository root:

```sh
cd ingestion-service
mvn test
```

With the four services running in their terminals, open another terminal at the
repository root and check service health and the main flow:

```sh
curl http://localhost:7020/health
curl http://localhost:7021/health
curl http://localhost:7022/health
curl http://localhost:7023/health

# Cleaned CSV records, intersection lookup, and current congestion level
curl http://localhost:7020/intersections
curl http://localhost:7021/intersections/INT-1005
curl http://localhost:7022/congestion

# Route estimate for two known intersections
curl 'http://localhost:7023/route?origin=INT-1001&destination=INT-1005'
```

Each health endpoint should return `OK`. The data endpoints return JSON. To stop
a service, press `Ctrl+C` in that service's terminal.
