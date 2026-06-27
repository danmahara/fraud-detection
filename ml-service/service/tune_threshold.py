import os
import numpy as np
import pandas as pd
import joblib
from sklearn.metrics import precision_score, recall_score

from service.isolation_forest import IsolationForest  # noqa: F401  (needed to load the IF)
from service.features import build_features

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
MODELS_DIR = os.path.join(ROOT, "models")

iso = joblib.load(os.path.join(MODELS_DIR, "isolation_forest.joblib"))
hybrid = joblib.load(os.path.join(MODELS_DIR, "xgboost_hybrid.joblib"))
recipe = joblib.load(os.path.join(MODELS_DIR, "feature_recipe.joblib"))

print("Loading + scoring a test sample...")
test_df = pd.read_csv(os.path.join(ROOT, "data", "fraudTest.csv")).sample(n=40000, random_state=1)
X = build_features(test_df, recipe["category_list"]).values
y = test_df["is_fraud"].values

if_score = iso.anomaly_score(X)
proba = hybrid.predict_proba(np.column_stack([X, if_score]))[:, 1]

print(f"\nTest rows: {len(y)}, frauds: {int(y.sum())}\n")
print("threshold  precision  recall   flagged")
for t in [0.30, 0.50, 0.70, 0.80, 0.90, 0.95, 0.99]:
    pred = (proba >= t).astype(int)
    p = precision_score(y, pred, zero_division=0)
    r = recall_score(y, pred, zero_division=0)
    print(f"   {t:.2f}      {p:.3f}     {r:.3f}     {int(pred.sum())}")