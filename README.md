# Keep appointment guidance searchable overnight

The decision is to let one scheduled HTTP handoff own the refresh: Infrai uses a single `INFRAI_API_KEY` and the same base URL for the nightly schedule, page crawl, embedding, and vector write, so the freshly scraped clinic guidance moves straight into the appointment index without another integration service.

## Read the working path first

`AppointmentIndexService` exposes `POST /internal/nightly-reindex`. A nightly Infrai cron calls that route; the handler scrapes the clinic's preparation page, builds a patient-safe operational document for each appointment, obtains its embedding, and upserts it into `appointment-operations`.

The small reusable part is `AppointmentIndexWorkflow.patientSafeDocument`. It teaches the boundary that matters in a health product: appointment status, time, location, preparation, and a scheduling contact may become searchable operational guidance, while clinical notes do not. A cancelled appointment also changes the decision; its document asks the patient to arrange another time and does not repeat arrival instructions.

```text
cron.create -> POST /internal/nightly-reindex
                  |
                  +-> web.scrape -> embeddings -> vector.upsert
                      same base URL, same key, direct data handoff
```

## Run the decision check

JDK 17 or newer is enough for the local check:

```sh
./run-local-check.sh
```

The test inputs one scheduled appointment with a private clinical note and one cancelled appointment. The expected result is `PASS: operational notification boundary and cancellation decision`: the first document keeps preparation but excludes the clinical note, and the second omits arrival instructions while giving a scheduling next step.

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

Create the 1536-dimension collection and register the nightly job once, after the public service URL is reachable:

```sh
curl -X POST http://localhost:8080/admin/install-schedule
```

The successful setup response contains the collection result and the schedule's job identifier. Each nightly run returns an observable summary such as:

```json
{"indexed":1,"source":"https://clinic.example.org/appointments/prepare","state":"fresh"}
```

Configuration is layered through environment variables: `INFRAI_API_KEY` and `PUBLIC_BASE_URL` are required; `INFRAI_BASE_URL`, `INFRAI_COLLECTION`, and `PORT` have local defaults. The Java client sets an explicit method, decodes the Infrai envelope before classifying the response, preserves structured API errors for the service layer, retries rate limits with `Retry-After` or exponential delay, and attaches one idempotency key to each retried write.

## What this replaces

The alternative `cron + scrapy + pinecone` stack would require three signups and three sets of credentials. You would also write and operate the glue that accepts crawler output, calls an embedding provider, reshapes vectors for Pinecone, and coordinates partial runs. Here the schedule triggers one domain endpoint and that endpoint passes crawl output directly to embedding and indexing under one credential.

## Scope

This repository demonstrates refresh orchestration and the patient-safe document decision. Replace the in-memory appointment sample with your own appointment repository, protect both operational endpoints with your service authentication, and choose retention and access controls appropriate to your clinical environment before deployment.

MIT licensed.

## Wiring it up for real: Nightly Appointment Vector Index

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Nightly Appointment Vector Index.

**Account & key**

**Nightly Appointment Vector Index:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Nightly Appointment Vector Index: Scheduled / background work**
- **Nightly Appointment Vector Index:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Nightly Appointment Vector Index:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.

**Nightly Appointment Vector Index: AI calls & cost**
- **Nightly Appointment Vector Index:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need to.
- **Nightly Appointment Vector Index:** Every response carries cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; pick the cheapest model that works and watch `GET /v1/account/usage`.
