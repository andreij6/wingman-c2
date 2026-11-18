# Wingman C2 — High-Level Architectural Design

**Target Employer Alignment:** Machina Cognita Technologies (*Software Engineer - Android*, Vienna VA): native Android in Kotlin/Java, ObjectBox/SQLite edge persistence, Docker-ready backends, proof-of-concept demos for UAS / LLM / simulation work.  
**Designs:** [`design/wingman-c2.figcraft`](../design/wingman-c2.figcraft) · renders in [`design/renders/`](../design/renders/) · regenerate with `zsh design/tools/build.sh`  
**SBIR Concept Synthesis:**
- **NOVA** (*Natural-language Ontologies for battlespace Visualizations and Analytics*) — schema-less local C2 data fusion, entity/relationship knowledge graph, and natural-language querying.
- **SMARTS** (*State-based Machine Aided Real Time Strategy*) — plain-English commander intent translation into ranked, explainable Courses of Action (COAs) and human-on-the-loop UAS tasking.
- **DDIL Edge Resilience** — offline-first mobile persistence via **ObjectBox** with an in-process fake backend, simulated Cloud Mirror, and toggleable Denied, Disrupted, Intermittent, and Limited (DDIL) network conditions.

---

## 1. System Principles & Constraints

1. **Offline-First Edge Truth:** The Android device's **ObjectBox** store is the primary read/write source of truth for the UI. Network availability only governs background replication and external scenario sync.
2. **Swappable Fake Backend:** Every remote capability sits behind the `core/domain` ports: `OntologyRepository`, `FocusRepository`, `AssetRegistry` (OntologyPorts), `MissionPlannerService`, `MissionRepository`, `TelemetryStreamSource` (MissionPorts), `LinkStateRepository`, `SyncRepository` (SyncPorts) and `ScenarioController` (ScenarioPorts). The demo runs 100% self-contained on-device via the deterministic coroutine simulators in `core/simulation`, with a clean swap path to Dockerized FastAPI / PostgreSQL / PX4 SITL containers.
3. **Explainable Autonomy (XAI) by Design:** Every AI recommendation (COA selection, threat alert, or autonomous replan) carries structured provenance, risk sub-scores, and human-readable doctrine rationales—never opaque black-box outputs.
4. **Demo-Ready Scenario Control:** The **Sync** screen lets an interviewer flip network connectivity, inject conflicting spot reports, or spawn pop-up RF threats in one tap.
5. **Link State Is Global:** One `LinkState` `StateFlow` drives the link chip in every top app bar and a DDIL banner on every screen, so an operator never has to open Sync to know they are disconnected.

---

## 2. Layered Clean Architecture & Module Structure

```mermaid
flowchart TB
    subgraph APP["app"]
        WA["WingmanApp: Scaffold + WingmanNavigationBar + NavDisplay per tab"]
        AVM["AppViewModel (LinkState for LocalLinkState)"]
        AM["AppModule: dispatchers · ApplicationScope · Clock"]
    end

    subgraph UI["feature/*/presentation (Jetpack Compose + Material 3)"]
        S1["Map · BattlespaceScreen / BattlespaceViewModel"]
        S2["Intel · IntelScreen / IntelViewModel"]
        S3["Plan · PlanScreen / PlanViewModel"]
        S4["Mission · MissionScreen / MissionViewModel"]
        S5["Sync · SyncScreen / SyncViewModel"]
        CUI["core/ui: WingmanScreen · LinkChip · DdilBanner · TacticalMap · EntityGraphView · AffiliationSymbol · UserMessageHost"]
    end

    subgraph DOMAIN["feature/*/domain + core/domain (pure Kotlin)"]
        UCF["Shared: FocusEntityUseCase · ObserveFocusUseCase · QueryOntologyUseCase · ObserveLinkStateUseCase"]
        UCB["Battlespace: ObserveBattlespace · ObserveEntityDetail · ObserveLiveAsset"]
        UCI["Intel: ObserveEntityGraph · ObserveLineage"]
        UCP["Plan: ObservePlanningTarget · ParseIntent · ResolveIntentTarget · GenerateExplainableCoas · DispatchWingmanMission"]
        UCM["Mission: ObserveMission · ObserveTelemetry · ObserveFlownTrack · IssueMissionCommand · ReplanMission"]
        UCS["Sync: ObserveSyncDashboard · SetLinkMode · ResolveConflict · InjectScenario"]
        IF["Ports: OntologyPorts · MissionPorts · SyncPorts · ScenarioPorts"]
    end

    subgraph DATA["core/data (adapters)"]
        RT["WingmanRuntime: ScenarioBootstrap · feed → IngestionPipeline · SyncEngine · MissionRuntime"]
        MR["MissionRuntime · TelemetryHub · MissionJournal · OnDeviceMissionPlanner (LRU)"]
        OR["OfflineFirstOntologyRepository · OntologyQueryEngine · EntityIndex · MirrorSyncRepository"]
    end

    subgraph CORE["Engines & storage"]
        OB[("core/database: ObjectBox\nEntities · Edges · HNSW vectors · Outbox · Telemetry")]
        SIM["core/simulation\nUasPhysicsEngine · MessyFeedSimulator · SimulatedCloudMirror · LinkSimulator"]
        ALG["core/algorithms\nIntentParser · QueryParser · CoaPlanner (grid A*) · EntityResolver · FeatureHasher"]
    end

    APP --> UI --> DOMAIN
    DOMAIN --> IF
    DATA -. implements .-> IF
    DATA --> OB
    DATA --> SIM
    DATA --> ALG
    SIM -. "DDIL toggle (Online / Degraded / Denied)" .-> DATA
```

### Module Layout (`com.wingmanc2`)
* `app` — `MainActivity`, `WingmanApp` (scaffold, navigation bar, per-tab back stacks via `TopLevelNavigator`), `AppViewModel`, `di/AppModule`.
* `core/model` — Pure data: ontology, planning, mission, sync, `RawFeedMessage`, `EntityAttributes`, `ScenarioProfile`.
* `core/common` — `Clock` + `ticks`, dispatcher and `ApplicationScope` qualifiers, `formatClock` / `formatTPlus` / `formatAge`.
* `core/algorithms` — Allocation-conscious engines: geo projection and bearings, `IntentParser`, `QueryParser`, `CoaPlanner` (cost grid, A*, smoothing, radar horizons, orbit/racetrack loiter), `EntityResolver` (spatial hash + band veto + embedding similarity), `FeatureHasher`, `Mix64` / `SplitMix64`.
* `core/domain` — Ports (`OntologyPorts`, `MissionPorts`, `SyncPorts`, `ScenarioPorts`) and the shared use cases.
* `core/simulation` — Deterministic fake backend:
  * `UasPhysicsEngine` + `FlightSession` (5 Hz telemetry `Flow` stamped in sim time; follows COA waypoints, loops the loiter orbit, detects pop-up threats, enforces the reserve floor).
  * `MessyFeedSimulator` / `FeedComposer` (AIS/ADS-B JSON, RF pings, free-text spot reports with missing or conflicting fields).
  * `SimulatedCloudMirror` and `LinkSimulator` (latency, loss, denied link, scripted version conflicts).
  * `ScenarioSeed` (seed entities, graph and records).
* `core/database` — ObjectBox `BoxStore`, records, mappers, `BinaryCodec`, stores (`EntityStore`, `EdgeStore`, `ObservationStore`, `OutboxStore`, `MissionStore`, `TelemetryStore`, `DecisionStore`).
* `core/data` — Port implementations and runtime: `WingmanRuntime`, `IngestionPipeline`, `EntityIndex`, `MissionRuntime`, `TelemetryHub`, `MissionJournal`, `OnDeviceMissionPlanner`, `SyncEngine`, `MirrorSyncRepository`, `SimulatedScenarioController`.
* `core/designsystem` — `WingmanTheme` tokens (color, type, spacing, shapes), icons and model-free components.
* `core/ui` — Model-aware shared UI: `WingmanScreen`, `LinkChip`, `DdilBanner`, `AffiliationSymbol`, `StatusTone`, `TacticalMap`, `MiniRoutePreview`, `EntityGraphView`, `UserMessageHost`, `launchAction`, label formatters.
* `core/navigation` — Top-level routes and `Navigator`.
* `feature/battlespace` — **Map**: NOVA search bar, affiliation filter chips, map canvas, entity bottom sheet (→ Intel / → Plan).
* `feature/intel` — **Intel**: NL query with parsed-predicate chips, entity graph, source lineage (raw inputs → fused ObjectBox entity).
* `feature/plan` — **Plan**: commander's intent + constraint chips, 3-route preview map, ranked COA cards, approve & dispatch.
* `feature/mission` — **Mission**: live route map, telemetry strip, explainable autonomy timeline, Hold / Replan / RTB overrides.
* `feature/sync` — **Sync**: link-condition segmented control, edge-vs-cloud version tiles, outbox with conflict resolve, scenario injects.

---

## 3. UI System (`core/ui`)

The redesign replaces per-card neon outlines with neutral surfaces and reserves color for meaning. Tokens live in one `WingmanColors` object and map 1:1 to the FigCraft foundations sheet (`design/renders/00-foundations.png`).

**Navigation.** Five `NavigationBar` destinations, one per screen: Map · Intel · Plan · Mission · Sync. The demo flow runs left to right: pick TRK-07 on the map → check its lineage → plan the recon → fly it → show sync under DDIL.

**Color roles.**

| Role | Used for | Never used for |
|---|---|---|
| `primary` #8AB4FF | Interactive elements, selection, primary buttons | Status |
| `ai` #B79CFF | Machine output: NOVA query bar, parsed predicates, inferred edges | Buttons |
| `ok` / `caution` / `critical` | Link and sync status, recommended COA, conflicts, RTB | Decoration |
| Affiliation: friendly #4FC3F7 · hostile #FF6B6B · unknown #F7D154 · neutral #6EDB8F | Map symbols and graph nodes only (MIL-STD-2525-inspired frames: rectangle / diamond / quatrefoil / square; air assets use the dome frame) | Anything outside the battlespace picture |

**Type.** Inter for UI (Title 22 · Headline 18 · Card title 15 · Body 14 · Label 12 · Overline 11 caps). Monospace only for data the operator compares: telemetry, IDs, coordinates, raw payloads.

**Shared composables.** `WingmanScreen`, `LinkChip`, `DdilBanner`, `AffiliationSymbol`, `MapTag`, `EntitySheet`, `PredicateChip`, `CoaCard` (expanded/collapsed), `TelemetryStrip`, `DecisionTimeline`, `OutboxRow`. Icons are Material Symbols.

**Map.** v1 draws on a Compose `Canvas` over a bundled, dimmed terrain raster with a 1 km grid; symbols, threat rings, zones and routes are vector overlays. Upgrade path: MapLibre Native with offline MBTiles (see §6).

---

## 4. ObjectBox Edge Data Model & Vector/Graph Schema

ObjectBox is used natively (no ORM impedance mismatch) for **relational entity linking**, **HNSW semantic search**, and **transactional sync queuing**:

```mermaid
erDiagram
    OntologyEntity ||--o{ OntologyEdge : "source / target"
    OntologyEntity ||--o{ RawObservation : "corroborated by"
    OntologyEntity ||--o{ MissionPlan : "target of"
    MissionPlan ||--|{ CourseOfAction : "evaluates"
    MissionPlan ||--o{ TelemetryFrame : "records"
    MissionPlan ||--o{ AutonomyDecision : "explains"
    SyncOutboxEvent }o--|| OntologyEntity : "replicates"

    OntologyEntity {
        long id PK
        string externalKey
        string kind "TRACK | EMITTER | ZONE | ASSET | REPORT"
        string affiliation "FRIEND | HOSTILE | UNKNOWN | NEUTRAL"
        string label
        double lat
        double lon
        float confidence
        string attributesJson "Schema-less KV bag"
        float_array embedding "HNSW vector (64-d)"
        long updatedAtEpochMs
        long version "edge-vs-cloud conflict detection"
        string syncState "SYNCED | PENDING | CONFLICT"
    }

    OntologyEdge {
        long id PK
        string relationType "DETECTED_IN | NEAR | CORROBORATES | TASKED_TO"
        float weight
        string provenance
    }

    RawObservation {
        long id PK
        string sourceFormat "AIS_JSON | RF_PING | SPOT_TEXT"
        string rawPayload
        long ingestedAtMs
    }

    MissionPlan {
        long id PK
        string commanderIntentText
        string parsedConstraintsJson
        string status "DRAFT | ACTIVE | LOITER | RTB"
    }

    CourseOfAction {
        long id PK
        string code "COA-A | COA-B | COA-C"
        string title
        float matchScore
        float riskScore
        int etaSeconds
        int projectedBatteryPct
        string waypointsJson
        string rationaleBulletsJson
        string tradeOffText "e.g. 38 s in S-band lobe"
    }

    AutonomyDecision {
        long id PK
        long tPlusMs
        string kind "APPROVAL | REROUTE | CAPTURE | HOLD | RTB"
        string title
        string rationale
        string provenanceJson
    }

    SyncOutboxEvent {
        long id PK
        string entityType
        long localEntityId
        string op "UPSERT | DELETE"
        string payloadJson
        int retryCount
        string status "PENDING | IN_FLIGHT | SYNCED | CONFLICT"
    }
```

---

## 5. Fake Backend & DDIL Simulation Mechanics

Instead of hardcoding static UI lists, the fake backend behaves like a real distributed system:

| Simulated Component | Real-World Counterpart | How It Works Locally (Zero Server Required) |
|---|---|---|
| **UAS Telemetry Stream** | MAVLink / PX4 over gRPC or WebSocket | Kotlin `flow { while (active) { stepPhysics(); emit(frame); delay(200) } }` advancing drone position along chosen COA waypoints, computing wind/battery drain and radar line-of-sight. |
| **Messy Sensor Ingestion** | Kafka / TAK / Aegis data bus | Bundled scenario packs emit staggered, noisy JSON/text observations (e.g., mismatched coordinate formats, missing altitude, free-text spot reports) normalized into `OntologyEntity` + `OntologyEdge` in ObjectBox. |
| **Natural Language Query** | NOVA Ontology + LLM/BERT Service | Deterministic query parser extracts temporal (`last 15m`), spatial (`near Sector 4`), and status (`unverified`) predicates, combined with **ObjectBox HNSW vector similarity** over pre-embedded concept vectors. Returns a `ParsedQuery` (predicates + latency) that Intel shows as chips, so the interpretation is visible and correctable. |
| **Explainable COA Engine** | SMARTS RL/Doctrine Planner | Intent tokenizer maps phrases (`"200m standoff"`, `"avoid radar"`, `"30% battery"`) to cost-function weights; a fast 2D grid A* planner generates 3 distinct trajectories (Stealth/Masked, Direct/Fast, Endurance/Loiter) with explicit trade-off rationales. |
| **Cloud Mirror & Outbox** | Dockerized FastAPI + Postgres/Neo4j | Writes always commit to ObjectBox + `SyncOutboxEvent`. When `LinkMode == ONLINE`, a background worker drains the outbox to `SimulatedCloudMirror` (with 150–400ms simulated RTT). When `LinkMode == DDIL_OFFLINE`, events queue locally and reconcile automatically on reconnect—including scripted version conflicts for live demo resolution. |

---

## 6. Production Upgrade Path (Post-Demo)

When transitioning from POC to a fielded pilot or Dockerized evaluation environment:
1. **Swap `SyncRepository`:** Replace `MirrorSyncRepository` + `SimulatedCloudMirror` with `Retrofit`/`OkHttp` (or gRPC) pointing at a Dockerized FastAPI + PostgreSQL (`pgvector`) service.
2. **Swap `MissionPlannerService`:** Route intent strings to a server-hosted SMARTS RL/LLM inference container while keeping the on-device A* planner as the **DDIL disconnected fallback**.
3. **Swap `TelemetryStreamSource`:** Bind to real MAVLink/ATAK CoT (Cursor on Target) UDP/TCP streams without touching any ViewModel or Compose screen.
4. **Swap the map renderer:** Replace the Canvas + bundled raster with MapLibre Native and offline MBTiles; overlays keep the same `AffiliationSymbol` / route models.
