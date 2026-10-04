import pandas as pd
from service.features import haversine_km, build_features


def test_haversine_zero_distance():
    assert haversine_km(27.7, 85.3, 27.7, 85.3) == 0


def test_haversine_one_degree_latitude():
    assert abs(haversine_km(0, 0, 1, 0) - 111.19) < 0.1


def test_build_features():
    df = pd.DataFrame([{
        "trans_date_trans_time": "2020-06-21 12:14:25", "dob": "1988-03-09",
        "amt": 100.0, "category": "grocery_pos", "lat": 27.7, "long": 85.3,
        "merch_lat": 27.7, "merch_long": 85.3, "gender": "M", "city_pop": 5000,
    }])
    f = build_features(df, ["grocery_pos", "travel"])
    assert f["hour"][0] == 12
    assert abs(f["age"][0] - 32.28) < 0.05
    assert f["gender"][0] == 1
    assert f["cat_grocery_pos"][0] == 1 and f["cat_travel"][0] == 0
    assert f.shape[1] == 6 + 2