from typing import Optional

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="Fraud Detection ML Service")


# What Spring will send us. snake_case is the JSON contract — keep these exact
# names; we'll make the Spring side match in the next step.
class PredictRequest(BaseModel):
    amount: float
    merchant_category: Optional[str] = None
    channel: str
    location_lat: Optional[float] = None
    location_lon: Optional[float] = None
    device_id: Optional[str] = None


# What we send back: the layered scores + final decision.
class PredictResponse(BaseModel):
    isolation_forest_score: float
    xgboost_probability: float
    fraud_score: float
    risk_level: str   # GREEN / YELLOW / ORANGE / RED
    decision: str     # APPROVED / OTP_REQUIRED / DECLINED


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    # TEMPORARY stub. The real hybrid pipeline (from-scratch Isolation Forest +
    # XGBoost + three-layer scoring) replaces this. For now, the same trivial
    # amount rule your Spring placeholder used, so the wiring is testable and
    # behaviour matches what you've already seen.
    amount = req.amount
    if amount < 10000:
        risk, score, decision = "GREEN", 0.10, "APPROVED"
    elif amount < 50000:
        risk, score, decision = "ORANGE", 0.70, "OTP_REQUIRED"
    else:
        risk, score, decision = "RED", 0.95, "DECLINED"

    return PredictResponse(
        isolation_forest_score=score,
        xgboost_probability=score,
        fraud_score=score,
        risk_level=risk,
        decision=decision,
    )