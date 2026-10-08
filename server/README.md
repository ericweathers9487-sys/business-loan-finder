# Lead server

The backend the app sends leads to. It runs the **same eligibility engine as the app** (both compile `core/`), so the server's decision always matches what the borrower saw, but it never takes the phone's word for it.

## What it does

1. **Checks every lead again.** `POST /leads` looks up the lender product itself, re-runs the eligibility engine on the borrower's answers, and applies the same routing gate as the app: consent for this exact lender on the current disclosures, complete contact details, and the lender's minimum fit score. Anything that fails is refused and **nothing is stored**.
2. **Stores the lead** with an append-only audit log: created, sent, accepted, rejected, funded, and data deleted, each with a time and who did it.
3. **Alerts the lender** through an optional webhook. The alert holds the anonymized lead card only. Failed alerts are retried every minute for 3 days.
4. **Releases contact details only after the lender accepts.** Before that, lenders see the card: business type, state, revenue range, amount, fit score, and reasons. No name, email, or phone.

## How personal information is protected

| Risk | Protection |
|---|---|
| Someone copies the database file | Name, business name, email, phone, and answers are encrypted (AES-256-GCM) before they're saved. Without `PII_ENCRYPTION_KEY` the file shows only ciphertext. Each record is tied to its lead, so it can't be swapped onto another row. |
| A lender sees more than they should | Each lender has their own API key and sees only their own products' leads. Others' leads answer "not found". Contact details unlock only on accept. |
| Leaked or guessed keys | Keys are 256-bit random values. The server stores only their SHA-256 hash and compares hashes in constant time. |
| Interception | The app refuses any lead endpoint that isn't `https://`. Webhook URLs must be `https://` too. |
| Data kept forever | Contact details and answers are erased automatically after `RETENTION_DAYS` (default 365). The anonymized card and audit log stay. |
| A borrower asks to be deleted | `POST /admin/erase-personal-data` with their email erases every lead they made. Emails are looked up by a keyed hash, never stored readable. |
| Personal data in logs or caches | Request bodies are never logged. Every response is marked `Cache-Control: no-store`. |
| Tampering with the audit trail | The database itself refuses to edit or delete audit events. |
| Spam | Submissions are rate-limited (30 a minute), bodies over 16 KB are refused, and leads for sample lenders are always refused. |
| Unrouted leads | A lead that fails the server check is never saved, so its contact details are never stored. |

## Set it up

### 1. Make the keys

On any computer with Java and this repo:

```
./gradlew :server:run --args=new-encryption-key
./gradlew :server:run --args=new-lender-key      # once per lender, plus once for yourself as admin
```

Or with `openssl` (Mac, Linux, Git Bash on Windows):

```
openssl rand -base64 32                           # PII_ENCRYPTION_KEY
KEY="blf_$(openssl rand -hex 32)"; echo "$KEY"    # an API key
printf '%s' "$KEY" | shasum -a 256                # its apiKeySha256
```

Send each lender their API key privately. Only the hash goes in the server's settings.

**Back up `PII_ENCRYPTION_KEY` somewhere safe.** If it's lost, stored leads can't be read. If it leaks, treat it like a data breach.

### 2. Settings (environment variables)

| Name | Required | What it is |
|---|---|---|
| `PII_ENCRYPTION_KEY` | yes | 32 random bytes, base64. Encrypts borrower data. |
| `LENDERS_JSON` | yes | The lender accounts. See below. |
| `ADMIN_API_KEY_SHA256` | no | Hash of your own admin key. Turns on the deletion endpoint. |
| `RETENTION_DAYS` | no | Days to keep contact details and answers. Default 365. Ask your lawyer: consent records may need to outlive this. |
| `DATABASE_PATH` | no | Default `data/leads.db` (`/data/leads.db` in Docker). |
| `PORT` | no | Default 8080. Most hosts set this for you. |

`LENDERS_JSON` example:

```json
[
  {
    "name": "Pilot Lender A",
    "apiKeySha256": "PASTE_THE_HASH_FROM_new-lender-key",
    "productIds": ["pilot-a-equipment"],
    "webhookUrl": "https://hooks.zapier.com/hooks/catch/123/abc/"
  }
]
```

Each product ID must match one in `core/src/main/kotlin/com/yourco/lending/catalog/SampleProducts.kt`, and must belong to exactly one lender. A Zapier or Make "catch hook" URL works as a webhook if the lender wants alerts by email or Slack.

### 3. Host it

The repo's `Dockerfile` builds the server. Any host that runs Docker works, including Render, Railway, and Fly.io. They all detect the Dockerfile and provide HTTPS.

1. Create a new service from this GitHub repo.
2. **Attach persistent storage mounted at `/data`.** Without it, every redeploy wipes the leads.
3. Add the settings from step 2 as environment variables or secrets.
4. Deploy, then open `https://<your-server>/health`. It should say `ok`.

Run one copy of the server only; it uses a single SQLite file. Turn on your host's disk backups.

### 4. Point the app at it

In the GitHub repo: **Settings → Secrets and variables → Actions → Variables → New repository variable**, name `LEAD_ENDPOINT`, value `https://<your-server>/leads`. The next build uses it. Locally: `./gradlew assembleDebug -PleadEndpoint=https://<your-server>/leads`.

Leads for sample lenders always stay on the phone. Real leads flow once real products are in the catalog and listed in `LENDERS_JSON`.

## For lenders

Every call needs the lender's key: `-H "Authorization: Bearer <api key>"`.

```
GET  /lender/leads                   all your leads, newest first (add ?status=new|accepted|rejected|funded)
GET  /lender/leads/<id>              one lead and its history
POST /lender/leads/<id>/accept       accept it; the response includes contact details and answers
POST /lender/leads/<id>/reject       -d '{"reason": "Outside our service area"}'
POST /lender/leads/<id>/funded       after accepting, once the loan funds
```

Accept and reject are final for a lead: a rejected lead can't be accepted later, and an accepted one can't be rejected.

## Deletion requests

When a borrower asks to be deleted:

```
curl -X POST https://<your-server>/admin/erase-personal-data \
  -H "Authorization: Bearer <your admin key>" -H "Content-Type: application/json" \
  -d '{"email": "borrower@example.com"}'
```

The reply says how many leads were erased. Lenders who already accepted the lead keep their own copy under their own privacy policy.

## Run the tests

```
./gradlew :server:test
```
