"""
Transaction generator / simulator.

Replays real rows from the Kaggle dataset through the LIVE system:
  log in to Spring -> POST /api/transactions -> Spring merges the cardholder
  profile, calls the ML service, returns a decision -> we compare that decision
  to the real is_fraud label and print a running scoreboard.

This fills the database with realistic scored transactions (for the dashboard)
and gives a live stream to show during a demo.

Run it with Spring on :8080 AND the ML service on :8000:
    python generate_transactions.py
"""

import os
import time

import pandas as pd
import requests

# ---- Config ----
SPRING_URL = "http://localhost:8080"
EMAIL = "test@example.com"
PASSWORD = "password123"
ACCOUNT_ID = 1

# The demo cardholder's home — MUST match the seeded user_profiles row.
# Each replayed purchase is shifted to keep its distance/direction from THIS
# home equal to the row's original distance/direction from its real home.
HOME_LAT = 27.7172
HOME_LON = 85.3240

# Real fraud rate is ~0.58%, so we oversample frauds here only so the stream
# visibly contains some. (Say this honestly in the demo — it's for visibility.)
N_FRAUD = 15
N_LEGIT = 85
DELAY_SECONDS = 0.4    # small pause so it streams like a live feed

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data", "fraudTest.csv")


def channel_for(category: str) -> str:
    if category.endswith("_net"):
        return "ONLINE"
    if category.endswith("_pos"):
        return "POS"
    return "APP"


def to_request(row):
    # Shift merchant location so its offset from our demo home equals the row's
    # offset from its real home -> the "distance from home" signal is preserved.
    merch_lat = HOME_LAT + (row["merch_lat"] - row["lat"])
    merch_lon = HOME_LON + (row["merch_long"] - row["long"])

    ts = str(row["trans_date_trans_time"]).replace(" ", "T") + "Z"

    return {
        "accountId": ACCOUNT_ID,
        "amount": float(row["amt"]),
        "merchant": str(row["merchant"]).replace("fraud_", ""),
        "merchantCategory": str(row["category"]),
        "channel": channel_for(str(row["category"])),
        "merchLat": round(float(merch_lat), 6),
        "merchLon": round(float(merch_lon), 6),
        "deviceId": "sim-device-001",
        "transactionTime": ts,
    }


def main():
    print("Loading dataset...")
    df = pd.read_csv(DATA)

    frauds = df[df["is_fraud"] == 1].sample(n=N_FRAUD, random_state=7)
    legit = df[df["is_fraud"] == 0].sample(n=N_LEGIT, random_state=7)
    stream = pd.concat([frauds, legit]).sample(frac=1, random_state=7)  # shuffle
    print(f"Prepared {len(stream)} transactions "
          f"({N_FRAUD} fraud, {N_LEGIT} legit), shuffled.\n")

    # 1. Log in once, reuse the token for every transaction.
    r = requests.post(f"{SPRING_URL}/api/auth/login",
                      json={"email": EMAIL, "password": PASSWORD})
    r.raise_for_status()
    token = r.json()["token"]
    headers = {"Authorization": f"Bearer {token}"}
    print("Logged in. Streaming transactions...\n")

    fraud_caught = fraud_missed = legit_ok = legit_flagged = errors = 0
    FLAGGED = {"ORANGE", "RED"}   # step-up or block both count as "flagged"

    for i, (_, row) in enumerate(stream.iterrows(), start=1):
        payload = to_request(row)
        actually_fraud = bool(row["is_fraud"])
        try:
            resp = requests.post(f"{SPRING_URL}/api/transactions",
                                 json=payload, headers=headers, timeout=15)
            if resp.status_code >= 300:
                errors += 1
                print(f"[{i:3}] ERROR {resp.status_code}: {resp.text[:120]}")
                time.sleep(DELAY_SECONDS)
                continue

            body = resp.json()
            risk = body["riskLevel"]
            flagged = risk in FLAGGED

            if actually_fraud and flagged:
                fraud_caught += 1
            elif actually_fraud:
                fraud_missed += 1
            elif not flagged:
                legit_ok += 1
            else:
                legit_flagged += 1

            label = "FRAUD" if actually_fraud else "legit"
            mark = "OK " if (actually_fraud == flagged) else ".. "
            print(f"[{i:3}] {mark} actual={label:5} "
                  f"amt={payload['amount']:8.2f} {payload['merchantCategory']:14} "
                  f"-> {risk:6} / {body['decision']}")
        except requests.RequestException as e:
            errors += 1
            print(f"[{i:3}] request failed: {e}")

        time.sleep(DELAY_SECONDS)

    total_fraud = fraud_caught + fraud_missed
    total_legit = legit_ok + legit_flagged
    print("\n==================  SUMMARY  ==================")
    print(f"  Frauds caught (ORANGE/RED):   {fraud_caught}/{total_fraud}")
    print(f"  Frauds missed (GREEN/YELLOW): {fraud_missed}/{total_fraud}")
    print(f"  Legit approved cleanly:       {legit_ok}/{total_legit}")
    print(f"  Legit false-flagged:          {legit_flagged}/{total_legit}")
    if errors:
        print(f"  Errors:                       {errors}")
    if total_fraud:
        print(f"  Fraud catch rate (recall):    {fraud_caught / total_fraud:.0%}")
    print("===============================================")
    print("\nAll transactions are now saved in the database — ready for the dashboard.")


if __name__ == "__main__":
    main()