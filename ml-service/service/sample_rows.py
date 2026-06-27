import os
import json
import pandas as pd

HERE = os.path.dirname(os.path.abspath(__file__))
df = pd.read_csv(os.path.join(HERE, "..", "data", "fraudTest.csv"))


def to_payload(row):
    return {
        "trans_date_trans_time": str(row["trans_date_trans_time"]),
        "dob": str(row["dob"]),
        "amt": float(row["amt"]),
        "category": str(row["category"]),
        "lat": float(row["lat"]),
        "long": float(row["long"]),
        "merch_lat": float(row["merch_lat"]),
        "merch_long": float(row["merch_long"]),
        "gender": str(row["gender"]),
        "city_pop": int(row["city_pop"]),
    }


print("===== 3 FRAUD examples =====")
for _, row in df[df["is_fraud"] == 1].head(3).iterrows():
    print(json.dumps(to_payload(row)))
    print()

print("===== 2 LEGIT examples =====")
for _, row in df[df["is_fraud"] == 0].head(2).iterrows():
    print(json.dumps(to_payload(row)))
    print()