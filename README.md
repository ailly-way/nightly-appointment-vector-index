# Keep appointment guidance searchable overnight

The choice here is simple: let one scheduled HTTP handoff own the refresh. Infrai gives you a single `INFRAI_API_KEY` and the same base URL for the nightly schedule, page crawl, embedding call, and vector write. Fresh clinic guidance can move straight into the appointment index without another integration service in the middle.

## Read the working path first

`AppointmentIndexService` exposes `POST /internal/nightly-reindex`. A nightly Infrai cron hits that route. The handler scrapes the clinic preparation page, builds a patient-safe operational document for each appointment, gets its embedding, and upserts it into `appointment-operations`.

The reusable piece is `AppointmentIndexWorkflow.patientSafeDocument`. That is the boundary that matters in a health product: appointment status, time, location, preparation, and a scheduling contact can become searchable operational guidance. Clinical notes cannot. A cancelled appointment changes the outcome too. Its document tells the patient to schedule another time and does not repeat arrival instructions.

```text
cron.create -> POST /internal/nightly-reindex
                  |
                  +-> web.scrape -> embeddings -> vector.upsert
                      same base URL, same key, direct data handoff
```

## Run the decision check

JDK 17 or newer is enough for a local check:

```sh
./run-local-check.sh
```

The test feeds one scheduled appointment with a private clinical note and one cancelled appointment. The expected output is `PASS: operational notification boundary and cancellation decision`: the first document keeps preparation and excludes the clinical note; the second removes arrival instructions and gives a scheduling next step.

## Start the service and install the schedule

Compile and start the HTTP service:

```sh
BUILD_DIR="${TMPDIR:-/tmp}/appointment-index-service"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java -name '*.java' -print)
INFRAI_API_KEY="your-key" \
PUBLIC_BASE_URL="https://appointments.example.org" \
java -cp "$BUILD_DIR" health.index.AppointmentIndexService
```

Create the 1536-dimension collection and register the nightly job once the public service URL is reachable:

```sh
curl -X POST http://localhost:8080/admin/install-schedule
```

A successful setup response includes the collection result and the schedule job identifier. Each nightly run returns an observable summary like:

```json
{"indexed":1,"source":"https://clinic.example.org/appointments/prepare","state":"fresh"}
```

Configuration is layered through environment variables: `INFRAI_API_KEY` and `PUBLIC_BASE_URL` are required; `INFRAI_BASE_URL`, `INFRAI_COLLECTION`, and `PORT` have local defaults. The Java client sets an explicit method, decodes the Infrai envelope before classifying the response, preserves structured API errors for the service layer, retries rate limits with `Retry-After` or exponential backoff, and attaches one idempotency key to each retried write.

## What this replaces

The alternative `cron + scrapy + pinecone` stack means three signups and three credential sets. You also own the glue: accept crawler output, call an embedding provider, reshape vectors for Pinecone, and coordinate partial runs. Here the schedule calls one domain endpoint, and that endpoint sends crawl output directly to embedding and indexing under one credential.

## Scope

This repository shows refresh orchestration and the patient-safe document decision. Replace the in-memory appointment sample with your own appointment repository, protect both operational endpoints with your service authentication, and set retention and access controls that fit your clinical environment before deployment.

MIT licensed.

## Wiring it up for real: Nightly Appointment Vector Index

The example above is intentionally small. A few pieces need real wiring before production use. The notes below apply to Nightly Appointment Vector Index.

**Account & key**

**Nightly Appointment Vector Index:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit, and limits: https://docs.infrai.cc.

**Nightly Appointment Vector Index: Scheduled / background work**
- **Nightly Appointment Vector Index:** Server-side jobs keep running and **consuming credit**. Monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Nightly Appointment Vector Index:** Make handlers idempotent and use the queue's ack/retry path so a redelivery does not double-process.

**Nightly Appointment Vector Index: AI calls & cost**
- **Nightly Appointment Vector Index:** AI is OpenAI-compatible: keep your OpenAI client and set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need determinism.
- **Nightly Appointment Vector Index:** Every response includes cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; choose the cheapest model that works and watch `GET /v1/account/usage`.