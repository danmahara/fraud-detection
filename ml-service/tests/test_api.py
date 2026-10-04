from fastapi.testclient import TestClient
from main import app, risk_from_score

client = TestClient(app)

PAYLOAD = {
    "trans_date_trans_time": "2020-06-21 12:14:25", "dob": "1988-03-09",
    "amt": 45.5, "category": "grocery_pos", "lat": 36.0, "long": -81.2,
    "merch_lat": 36.01, "merch_long": -81.2, "gender": "M", "city_pop": 3495,
}


def test_health():
    assert client.get("/health").json() == {"status": "ok"}


def test_predict_returns_all_fields():
    r = client.post("/predict", json=PAYLOAD)
    assert r.status_code == 200
    body = r.json()
    for k in ["isolation_forest_score", "xgboost_probability",
              "fraud_score", "risk_level", "decision"]:
        assert k in body


def test_missing_field_rejected():
    bad = dict(PAYLOAD)
    del bad["amt"]
    assert client.post("/predict", json=bad).status_code == 422


def test_risk_boundaries():
    assert risk_from_score(0.49)[0] == "GREEN"
    assert risk_from_score(0.50)[0] == "YELLOW"
    assert risk_from_score(0.80)[0] == "ORANGE"
    assert risk_from_score(0.95)[0] == "RED"