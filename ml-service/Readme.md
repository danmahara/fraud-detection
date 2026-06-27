# Fraud ML Service — Verification Guide

This document explains how to **prove the fraud-detection model is working**, in a
way anyone can reproduce from scratch (including during a project demo/defense).

## The idea

We verify the model along two independent paths and check they agree:

1. **Direct path** — send a transaction straight to the Python ML service
   (`POST /predict` on FastAPI) and read the risk decision.
2. **End-to-end path** — submit the same transaction through the Spring backend
   (`POST /api/transactions`), which calls the ML service internally.

If the **same transaction produces the same `risk_level` and `decision`** on both
paths, then the integration is sound and the model is behaving consistently.

> Part 1 (direct path) works on its own and is enough to prove the model itself
> is correct and reproducible. Part 2 (end-to-end) additionally proves the Spring
> ↔ ML wiring is correct.

---

## Prerequisites

- Python virtual environment created and dependencies installed:
  ```
  cd ml-service
  venv\Scripts\activate           :: Windows
  pip install -r requirements.txt
  ```
- The dataset files present in `ml-service/data/`:
  - `fraudTrain.csv`
  - `fraudTest.csv`
- The trained model artifacts present in `ml-service/models/`:
  - `isolation_forest.joblib`
  - `xgboost_hybrid.joblib`
  - `feature_recipe.joblib`

  If `models/` is empty (e.g. fresh clone — models are git-ignored), regenerate
  them once:

  ```
  python -m service.train
  ```

  Training uses fixed random seeds (`random_state=42`), so the resulting models —
  and therefore every score below — are **deterministic and reproducible**.

---

## Part 1 — Verify the model (direct to the ML service)

### Step 1: Start the ML service

```
cd ml-service
venv\Scripts\activate
uvicorn main:app --reload --port 8000
```

On startup you should see a line like:
`Models loaded. 14 categories, N features.`
Its interactive docs are at: http://localhost:8000/docs

### Step 2: Generate sample transactions from the dataset

In a second terminal (same venv):

```
cd ml-service
venv\Scripts\activate
python service/sample_rows.py
```

This reads real rows from `data/fraudTest.csv` and prints ready-to-send JSON
payloads — 3 known frauds and 2 known legit transactions. Each payload contains
only **human-readable fields** (amount, category, time, locations, etc.).

### Step 3: Send a payload to `/predict`

Open http://localhost:8000/docs, expand **POST /predict**, click **Try it out**,
paste one payload from Step 2 into the request body, and click **Execute**.
Read `risk_level` and `decision` in the response.

(Repeat for each fraud and legit payload.)

### Step 4: Compare against the expected results

Because the model is deterministic, known rows produce known outputs. Use this as
a **golden reference** — if your results match, the model is working correctly.

| Transaction (from `fraudTest.csv`)               | amount | category       | time  | `xgboost_probability` | `risk_level` | `decision` |
| ------------------------------------------------ | ------ | -------------- | ----- | --------------------- | ------------ | ---------- |
| Fraud — online misc purchase, late night         | 780.52 | misc_net       | 22:32 | ~0.9996               | RED          | DECLINED   |
| Fraud — entertainment, late night                | 620.33 | entertainment  | 22:37 | ~0.999                | RED          | DECLINED   |
| Legit — small personal care, midday              | 2.86   | personal_care  | 12:14 | ~0.0000               | GREEN        | APPROVED   |
| Legit — small personal care, midday              | 29.84  | personal_care  | 12:14 | ~0.0031               | GREEN        | APPROVED   |
| Fraud — small everyday charge (a known **miss**) | 24.84  | health_fitness | 22:06 | ~0.2288               | GREEN        | APPROVED   |

Notes:

- The last row is a genuine **false negative**: a small, ordinary-looking charge
  the feature-space model cannot distinguish from normal spending. This is
  expected, and it is exactly the kind of fraud the **behavioral/context layer**
  is designed to catch (e.g. "new device", "many purchases in a short window").
- Tiny differences in the trailing decimals are fine; the `risk_level` and
  `decision` are what must match.

---

## Risk bands

The model outputs a fraud probability in `[0, 1]`, which is mapped to four tiers
applying **escalating friction** (so we catch fraud without blocking the many
false positives a single hard threshold would produce):

| Probability   | Risk level | Decision     | Meaning                            |
| ------------- | ---------- | ------------ | ---------------------------------- |
| `< 0.50`      | GREEN      | APPROVED     | Approve silently                   |
| `0.50 – 0.80` | YELLOW     | APPROVED     | Approve, but log/monitor           |
| `0.80 – 0.95` | ORANGE     | OTP_REQUIRED | Step-up verification (not blocked) |
| `>= 0.95`     | RED        | DECLINED     | Block outright                     |

---

## Part 2 — Verify end-to-end through Spring

> **Status: activates after the Spring ↔ ML wiring is complete.** The Spring
> backend must send the same readable fields the ML service now expects. Once
> that wiring is in place, this section confirms the two paths agree.

The principle: a transaction submitted to Spring should reach the same verdict as
sending it directly to the ML service.

### Step 1: Start both services

- ML service: `uvicorn main:app --reload --port 8000`
- Spring backend: `mvn clean spring-boot:run` (port 8080)

### Step 2: Authenticate with Spring

```
POST http://localhost:8080/api/auth/login
{ "email": "test@example.com", "password": "password123" }
```

Copy the returned `token`. In Spring's Swagger (http://localhost:8080/swagger-ui.html),
click **Authorize** and paste just the raw token.

### Step 3: Submit the transaction through Spring

`POST /api/transactions` with the transaction. (The cardholder's stored profile —
home location, date of birth, gender — supplies the fields the form itself does
not, mirroring how a real bank already knows the customer.)

### Step 4: Compare

The `risk_level` and `decision` returned by Spring **must match** what the same
transaction produced via the direct `/predict` call in Part 1. Matching results
confirm the model and the integration are both working.

---

## Troubleshooting

- **`ModuleNotFoundError: service` when training** — run training as a module from
  the `ml-service` folder: `python -m service.train` (not `python service/train.py`).
- **`/predict` returns 422 with an empty body when called from Spring** — the JDK
  HTTP client must be pinned to HTTP/1.1 in the Spring `MlScoringClient` (the JDK
  default HTTP/2 upgrade drops the request body on cleartext `http://`).
- **Scores differ from the golden table** — the `models/` artifacts were retrained
  on different data or seeds. Re-run `python -m service.train` against the same
  `fraudTrain.csv` to restore the reference numbers.
- **`models/` is empty after a fresh clone** — expected; the `.joblib` files are
  git-ignored. Regenerate with `python -m service.train`.
