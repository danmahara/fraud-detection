import os
import time
import numpy as np
import pandas as pd
import joblib
from sklearn.metrics import roc_auc_score, average_precision_score, classification_report
from xgboost import XGBClassifier

from service.isolation_forest import IsolationForest

from service.features import build_features


HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DATA_DIR = os.path.join(ROOT, "data")
MODELS_DIR = os.path.join(ROOT, "models")
os.makedirs(MODELS_DIR, exist_ok=True)



print("Loading training data...")
train_df = pd.read_csv(os.path.join(DATA_DIR, "fraudTrain.csv"))

# Fixed category list (sorted) — saved and reused at serving time.
category_list = sorted(train_df["category"].unique().tolist())

# Keep ALL frauds + a sample of legit rows: enough fraud signal, manageable size
# (pure-Python IF scoring is the slow part, so we cap the row count).
fraud = train_df[train_df["is_fraud"] == 1]
legit = train_df[train_df["is_fraud"] == 0].sample(n=200000, random_state=42)
train_sample = pd.concat([fraud, legit]).sample(frac=1, random_state=42)  # shuffle
print(f"Train sample: {len(train_sample)} rows ({int(train_sample['is_fraud'].sum())} fraud)")

X_train = build_features(train_sample, category_list)
feature_columns = list(X_train.columns)
X_train = X_train.values
y_train = train_sample["is_fraud"].values

print("Loading test data...")
test_df = pd.read_csv(os.path.join(DATA_DIR, "fraudTest.csv"))
# Random sample at the natural fraud rate -> honest metrics, faster scoring.
test_sample = test_df.sample(n=150000, random_state=42)
X_test = build_features(test_sample, category_list).values
y_test = test_sample["is_fraud"].values
print(f"Test sample: {len(test_sample)} rows ({int(y_test.sum())} fraud)")

# --- 1. From-scratch Isolation Forest (unsupervised) ---
print("\nFitting Isolation Forest...")
iso = IsolationForest(n_trees=100, sample_size=256, random_state=42).fit(X_train)

print("Scoring with IF (slow pure-Python part, ~1-2 min)...")
t0 = time.time()
if_train = iso.anomaly_score(X_train)
if_test = iso.anomaly_score(X_test)
print(f"  scored in {time.time() - t0:.1f}s")

X_train_hybrid = np.column_stack([X_train, if_train])
X_test_hybrid = np.column_stack([X_test, if_test])

# --- 2. XGBoost: baseline vs hybrid (with IF score) ---
n_pos = int(y_train.sum())
scale_pos_weight = (len(y_train) - n_pos) / n_pos
print(f"\nscale_pos_weight = {scale_pos_weight:.1f}")


def train_xgb(X_tr, y_tr):
    m = XGBClassifier(
        n_estimators=300, max_depth=5, learning_rate=0.1,
        subsample=0.8, colsample_bytree=0.8,
        scale_pos_weight=scale_pos_weight, eval_metric="aucpr",
        random_state=42, n_jobs=-1,
    )
    m.fit(X_tr, y_tr)
    return m


print("Training baseline XGBoost...")
baseline = train_xgb(X_train, y_train)
base_proba = baseline.predict_proba(X_test)[:, 1]

print("Training hybrid XGBoost (+ IF score)...")
hybrid = train_xgb(X_train_hybrid, y_train)
hybrid_proba = hybrid.predict_proba(X_test_hybrid)[:, 1]

# --- 3. Evaluate ---
print("\n================  TEST RESULTS  ================")
print("                          ROC-AUC    PR-AUC")
print(f"  IF alone:               {roc_auc_score(y_test, if_test):.4f}     "
      f"{average_precision_score(y_test, if_test):.4f}")
print(f"  XGBoost (baseline):     {roc_auc_score(y_test, base_proba):.4f}     "
      f"{average_precision_score(y_test, base_proba):.4f}")
print(f"  XGBoost + IF (hybrid):  {roc_auc_score(y_test, hybrid_proba):.4f}     "
      f"{average_precision_score(y_test, hybrid_proba):.4f}")
print("================================================")
print("\nHybrid report @ threshold 0.5:")
print(classification_report(y_test, (hybrid_proba >= 0.5).astype(int), digits=4))

# --- 4. Save models + the feature recipe (serving must reproduce features) ---
joblib.dump(iso, os.path.join(MODELS_DIR, "isolation_forest.joblib"))
joblib.dump(hybrid, os.path.join(MODELS_DIR, "xgboost_hybrid.joblib"))
joblib.dump(
    {"category_list": category_list, "feature_columns": feature_columns},
    os.path.join(MODELS_DIR, "feature_recipe.joblib"),
)
print(f"\nSaved models + feature recipe to {MODELS_DIR}")