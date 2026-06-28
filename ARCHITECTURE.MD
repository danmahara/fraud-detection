# Real-Time Fraud Detection System

A fraud detection platform that scores card transactions in real time using a
**hybrid machine-learning model** combined with a **rule-based behavioral and
context layer**. The system demonstrates that combining a trained ML model with
user-relative behavioral rules catches fraud that either approach alone would miss.

---

## 1. What the system does

When a customer makes a payment, the system scores it across **three layers** and
returns a decision — approve, request verification (OTP), or block — in real time.

The core thesis: **a machine-learning model trained on a transaction dataset is
blind to "unusual _for this specific user_" signals.** A $500 grocery purchase
looks normal in isolation, but if it happens 12,000 km from where the cardholder
lives, on a device they've never used, at 3 a.m., it should be flagged. The ML
model cannot reliably learn this because it scores transactions in isolation; the
behavioral/context layer scores them _relative to the individual cardholder's
history_. The two together are stronger than either alone.

---

## 2. Architecture overview

The system is built as **microservices** with a clean separation of concerns:

```
┌─────────────────┐     ┌──────────────────────┐     ┌─────────────────────┐
│  React Frontend │────▶│  Spring Boot Backend │────▶│  FastAPI ML Service │
│  (dashboard)    │◀────│  (orchestration +    │◀────│  (stateless scoring │
│                 │     │   business rules)    │     │   engine)           │
└─────────────────┘     └──────────┬───────────┘     └─────────────────────┘
                                   │
                                   ▼
                          ┌─────────────────┐
                          │  PostgreSQL     │
                          │  (Flyway-managed)│
                          └─────────────────┘
```

### Component responsibilities

| Component               | Responsibility                                                                   | Why it lives here                                 |
| ----------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------- |
| **React frontend**      | Admin dashboard (bank view) + customer app (cardholder view)                     | Two role-based experiences from one app           |
| **Spring Boot backend** | Orchestration, business rules, behavioral/context scoring, persistence, security | Owns the data and the decision logic              |
| **FastAPI ML service**  | Stateless ML scoring (Isolation Forest + XGBoost)                                | Pure scoring engine — no business logic, no state |
| **PostgreSQL**          | All persistent data                                                              | Schema owned and versioned by Flyway migrations   |

**Key design decision:** the behavioral/context scoring lives in the **Spring
backend, not the Python service.** The Python service is a stateless scoring
engine — it receives a transaction's features and returns ML scores. The
behavioral layer needs access to the user's history (their average spend, home
location, known devices), which lives in the Spring database. Keeping the rules
in Spring means a clean microservice boundary: **Python = stateless ML, Spring =
stateful orchestration and business decisions.** This is also easier to explain
and defend than burying business rules inside the ML service.

---

## 3. The three-layer scoring model

Every transaction is scored across three layers, which are then blended.

### Layer 1 — Machine Learning (FastAPI service)

A **hybrid model** combining two complementary techniques:

- **Isolation Forest (unsupervised, built from scratch):** detects _anomalies_ —
  transactions that are statistical outliers across all features. Does not need
  fraud labels; it learns what "normal" looks like and flags the unusual.
- **XGBoost (supervised):** trained on labeled fraud examples. The Isolation
  Forest's anomaly score is fed in as an additional feature (a _stacked hybrid_),
  so XGBoost learns from both the raw features and the anomaly signal.

The final ML `fraud_score` is the XGBoost probability. Both models are trained on
the **same feature set**, derived from a shared `build_features()` function used
at **both training and serving time** — so the features can never drift apart
between the two.

**Features used:** transaction amount, hour of day, cardholder age, distance from
home to merchant, gender, city population, and one-hot encoded merchant category
(14 categories).

### Layer 2 & 3 — Behavioral & Context scoring (Spring backend)

This is the **centerpiece** of the project. Five **user-relative** signals, each
normalized to [0, 1], computed against the cardholder's own profile:

| Signal             | What it measures               | Fires when                     |
| ------------------ | ------------------------------ | ------------------------------ |
| **Amount anomaly** | Spend vs. the user's average   | Amount is 3×–15× their normal  |
| **Distance**       | How far from the user's home   | 100 km – 3000 km from home     |
| **New device**     | Is this a known device?        | Device differs from last known |
| **Velocity**       | Transactions in a short window | 2–8 transactions in 5 minutes  |
| **Odd hour**       | Time-of-day anomaly            | Between 1 a.m. and 5 a.m.      |

Each signal is **weighted** and combined into a single context score. A signal
only contributes a human-readable _reason_ (e.g. "Purchase 12,114 km from home")
when it crosses a threshold, so flagged transactions explain themselves.

**Why this matters:** none of these signals are properties of the transaction
_in isolation_ — they are all relative to the _individual cardholder_. This is
exactly what the ML model structurally cannot capture, and it is the project's
core contribution.

### Blending the layers

The final score combines ML and context:

```
finalScore = min(1.0, mlScore + context.score × CONTEXT_BOOST)
```

Plus an **escalation floor** — strong behavioral signals can force a step-up even
when the ML model is calm (mirroring how real risk-based authentication works):

- context ≥ 0.55 → finalScore raised to at least 0.90 (→ ORANGE, verification)
- context ≥ 0.35 → finalScore raised to at least 0.55 (→ YELLOW, monitor)

This means **behavioral red flags can demand verification on their own**, even
when the transaction looks intrinsically normal to the ML model.

### Risk bands → decisions

| Score       | Risk level | Decision                            |
| ----------- | ---------- | ----------------------------------- |
| < 0.50      | GREEN      | APPROVED                            |
| 0.50 – 0.80 | YELLOW     | APPROVED (monitored)                |
| 0.80 – 0.95 | ORANGE     | OTP_REQUIRED (step-up verification) |
| ≥ 0.95      | RED        | DECLINED (blocked)                  |

---

## 4. The two user experiences

The system has **two role-based interfaces** from a single React app:

### Admin / Bank view (light theme)

The fraud-monitoring console for the bank:

- **Overview** — live stats (total / flagged / blocked, today's activity) + recent feed
- **Transactions** — full paginated transaction history with risk, score, decision
- **Users** — cardholder list with per-user detail (profile, accounts, transactions)
- **Analytics** — risk-level distribution and category breakdown charts
- **Merchants** — manage payable merchants (CRUD)

### Customer / Cardholder view (dark theme)

The app a cardholder uses:

- **Onboarding** — set home location (browser geolocation), date of birth, gender;
  captures a device fingerprint as the baseline for the new-device signal
- **Dashboard** — make a payment, see live stats and recent transactions
- **Payment** — pay a merchant (by selection or by phone/email handle); see the
  real-time decision (approved / verification required / declined)
- **Transactions** — full paginated personal transaction history

---

## 5. Key design decisions

These were deliberate choices, each with a defensible rationale:

1. **Behavioral layer in Spring, not Python.** Keeps the ML service stateless and
   the microservice boundary clean; business rules live with the business data.

2. **Merchants carry their own location and category.** A payment references a
   merchant; the merchant supplies the location (for the distance signal) and
   category (for the ML model). This is realistic — merchant location comes from
   the merchant record, not from the cardholder typing coordinates.

3. **Categories normalized into their own table with a foreign key.** The category
   list is seeded to exactly match the ML model's known categories, guaranteeing
   every merchant is model-compatible while preserving referential integrity.

4. **Device identity = UUID + readable label.** A stable UUID is the actual
   comparison identifier (so the new-device signal works); a human-readable label
   ("Chrome on Windows") is stored for notifications.

5. **Registration is minimal; profile is collected at onboarding.** New users
   register with just name/email/phone/password; the scoring-relevant profile
   (home location, DOB, gender) is collected separately, KYC-style.

6. **Home location via browser geolocation with manual fallback.** Realistic, and
   the user always confirms before saving.

7. **Schema owned by Flyway, validated by Hibernate.** Migrations are the single
   source of truth for the database; `ddl-auto=validate` ensures the entities
   match the schema but never modifies it.

---

## 6. Technology stack

| Layer      | Technology                                                                 |
| ---------- | -------------------------------------------------------------------------- |
| Backend    | Spring Boot 4, Java 21, Spring Security 7 (JWT), Hibernate 7               |
| ML service | Python, FastAPI, NumPy/Pandas, custom Isolation Forest, XGBoost            |
| Database   | PostgreSQL, Flyway migrations                                              |
| Frontend   | React (Vite), Tailwind CSS, React Query, React Router, Recharts            |
| Auth       | JWT (stateless), BCrypt password hashing, role-based access (USER / ADMIN) |

---

## 7. Security model

- **Stateless JWT authentication.** The token's subject is the user's email;
  every request is authenticated by a filter that loads the user from it.
- **Role-based authorization.** `/api/admin/**` requires ADMIN; `/api/me/**` and
  other authenticated routes require a valid token; `/api/auth/**` is public.
- **Ownership scoping.** User-facing endpoints derive the user from the JWT, never
  from the request body — a user can only ever see or act on their own data.

---

## 8. Known limitations & honest notes

A mature project acknowledges its limits — these are good to raise proactively:

- **ML scores on manually-entered transactions trend high.** The model was trained
  on a specific dataset distribution; hand-entered transactions are
  out-of-distribution, so the ML score is less reliable for them. This is exactly
  why the behavioral/context layer is essential, and why the demo emphasizes the
  context layer's contribution.
- **`city_pop` is a low-importance feature** inherited from the training data; the
  system uses a representative default rather than asking the user, since users
  don't know their city's population. In production it would be derived from location.
- **OTP step-up is signaled but not yet completed** — ORANGE transactions return
  OTP_REQUIRED, but the verification endpoint is future work.
