import os

import numpy as np
import pandas as pd
import joblib
from fastapi import FastAPI
from pydantic import BaseModel

from service.isolation_forest import IsolationForest  # noqa: F401 (needed to load the IF)
from service.features import build_features

app = FastAPI(title="Fraud ML Service")

HERE = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR = os.path.join(HERE, "models")

iso_model = joblib.load(os.path.join(MODELS_DIR, "isolation_forest.joblib"))
xgb_model = joblib.load(os.path.join(MODELS_DIR, "xgboost_hybrid.joblib"))
recipe = joblib.load(os.path.join(MODELS_DIR, "feature_recipe.joblib"))
CATEGORY_LIST = recipe["category_list"]
print(f"Models loaded. {len(CATEGORY_LIST)} categories, "
      f"{len(recipe['feature_columns'])} features.")


class PredictRequest(BaseModel):
    # Raw transaction + cardholder fields. build_features() converts these into
    # the exact numeric features the model was trained on.
    trans_date_trans_time: str   # "2020-06-21 12:14:25"
    dob: str                     # cardholder date of birth, "1988-03-09"
    amt: float
    category: str                # one of the trained merchant categories
    lat: float                   # cardholder home latitude
    long: float                  # cardholder home longitude
    merch_lat: float             # merchant latitude
    merch_long: float            # merchant longitude
    gender: str                  # "M" or "F"
    city_pop: int


class PredictResponse(BaseModel):
    isolation_forest_score: float
    xgboost_probability: float
    fraud_score: float
    risk_level: str
    decision: str


def risk_from_score(p: float):
    if p < 0.50:
        return "GREEN", "APPROVED"
    elif p < 0.80:
        return "YELLOW", "APPROVED"
    elif p < 0.95:
        return "ORANGE", "OTP_REQUIRED"
    else:
        return "RED", "DECLINED"


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    # Build a 1-row DataFrame with the columns build_features expects, then
    # reuse the EXACT training-time feature logic.
    row = pd.DataFrame([{
        "trans_date_trans_time": req.trans_date_trans_time,
        "dob": req.dob,
        "amt": req.amt,
        "category": req.category,
        "lat": req.lat,
        "long": req.long,
        "merch_lat": req.merch_lat,
        "merch_long": req.merch_long,
        "gender": req.gender,
        "city_pop": req.city_pop,
    }])

    X = build_features(row, CATEGORY_LIST).values          # (1, n_features)
    if_score = float(iso_model.anomaly_score(X)[0])
    X_hybrid = np.column_stack([X, [[if_score]]])          # add IF score column
    proba = float(xgb_model.predict_proba(X_hybrid)[0, 1])

    risk, decision = risk_from_score(proba)
    return PredictResponse(
        isolation_forest_score=round(if_score, 4),
        xgboost_probability=round(proba, 4),
        fraud_score=round(proba, 4),
        risk_level=risk,
        decision=decision,
    )