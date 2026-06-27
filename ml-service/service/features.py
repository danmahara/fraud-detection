import numpy as np
import pandas as pd


def haversine_km(lat1, lon1, lat2, lon2):
    # Great-circle distance in km between two lat/long points.
    R = 6371.0
    lat1, lon1, lat2, lon2 = map(np.radians, [lat1, lon1, lat2, lon2])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    a = np.sin(dlat / 2) ** 2 + np.cos(lat1) * np.cos(lat2) * np.sin(dlon / 2) ** 2
    return R * 2 * np.arcsin(np.sqrt(a))


def build_features(df, category_list):
    # The single source of truth for turning raw fields into model features.
    # Training AND serving both call this, so they can never drift apart.
    f = pd.DataFrame()
    f["amt"] = df["amt"].astype(float)

    dt = pd.to_datetime(df["trans_date_trans_time"])
    f["hour"] = dt.dt.hour

    dob = pd.to_datetime(df["dob"])
    f["age"] = (dt - dob).dt.days / 365.25

    f["distance_km"] = haversine_km(
        df["lat"].values, df["long"].values,
        df["merch_lat"].values, df["merch_long"].values,
    )
    f["gender"] = (df["gender"] == "M").astype(int)
    f["city_pop"] = df["city_pop"].astype(float)

    for cat in category_list:
        f[f"cat_{cat}"] = (df["category"] == cat).astype(int)

    return f