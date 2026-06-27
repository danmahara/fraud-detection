import os
import time
import numpy as np
import pandas as pd
import joblib
from sklearn.model_selection import train_test_split
from sklearn.metrics import roc_auc_score, average_precision_score, classification_report
from xgboost import XGBClassifier

from service.isolation_forest import IsolationForest


HERE = os.path.dirname(os.path.abspath(__file__))   # .../ml-service/service
ROOT = os.path.dirname(HERE)                         # .../ml-service
DATA_PATH = os.path.join(ROOT, "data", "creditcard.csv")
MODELS_DIR = os.path.join(ROOT, "models")
os.makedirs(MODELS_DIR, exist_ok=True)

print("Loading dataset...")
df = pd.read_csv(DATA_PATH)
X_df = df.drop(columns=["Time", "Class"])     # features: V1..V28, Amount
y = df["Class"].values
feature_names = list(X_df.columns)
X = X_df.values

# Stratified split keeps the same fraud ratio in both halves.
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42, stratify=y
)
print(f"Train: {X_train.shape[0]} rows ({int(y_train.sum())} fraud)")
print(f"Test:  {X_test.shape[0]} rows ({int(y_test.sum())} fraud)")

# --- 1. Fit the from-scratch Isolation Forest (unsupervised — labels unused) ---
print("\nFitting Isolation Forest...")
iso = IsolationForest(n_trees=100, sample_size=256, random_state=42).fit(X_train)

# --- 2. Compute the IF anomaly score as an extra feature for every row ---
#        (pure-Python scoring — the slow part, give it a minute or two)
print("Scoring train set with IF (slow part)...")
t0 = time.time()
if_train = iso.anomaly_score(X_train)
print(f"  train scored in {time.time() - t0:.1f}s")
print("Scoring test set with IF...")
if_test = iso.anomaly_score(X_test)

# Hybrid feature matrix = original features + the IF anomaly score column.
X_train_hybrid = np.column_stack([X_train, if_train])
X_test_hybrid = np.column_stack([X_test, if_test])

# Imbalance: positives (fraud) are ~578x rarer, so weight them up in training.
n_pos = int(y_train.sum())
n_neg = len(y_train) - n_pos
scale_pos_weight = n_neg / n_pos
print(f"\nscale_pos_weight = {scale_pos_weight:.1f}")


def train_xgb(X_tr, y_tr):
    model = XGBClassifier(
        n_estimators=300,
        max_depth=4,
        learning_rate=0.1,
        subsample=0.8,
        colsample_bytree=0.8,
        scale_pos_weight=scale_pos_weight,
        eval_metric="aucpr",
        random_state=42,
        n_jobs=-1,
    )
    model.fit(X_tr, y_tr)
    return model


# --- 3. Baseline (features only) vs Hybrid (features + IF score) ---
print("\nTraining BASELINE XGBoost (features only)...")
baseline = train_xgb(X_train, y_train)
base_proba = baseline.predict_proba(X_test)[:, 1]

print("Training HYBRID XGBoost (features + IF score)...")
hybrid = train_xgb(X_train_hybrid, y_train)
hybrid_proba = hybrid.predict_proba(X_test_hybrid)[:, 1]

# --- 4. Evaluate. PR-AUC is the honest metric at 0.17% fraud. ---
print("\n================  TEST RESULTS  ================")
print("                          ROC-AUC    PR-AUC")
print(f"  IF alone:               {roc_auc_score(y_test, if_test):.4f}     "
      f"{average_precision_score(y_test, if_test):.4f}")
print(f"  XGBoost (baseline):     {roc_auc_score(y_test, base_proba):.4f}     "
      f"{average_precision_score(y_test, base_proba):.4f}")
print(f"  XGBoost + IF (hybrid):  {roc_auc_score(y_test, hybrid_proba):.4f}     "
      f"{average_precision_score(y_test, hybrid_proba):.4f}")
print("================================================")

print("\nHybrid classification report @ threshold 0.5:")
print(classification_report(y_test, (hybrid_proba >= 0.5).astype(int), digits=4))

# --- 5. Save artifacts for the serving side (/predict will load these) ---
joblib.dump(iso, os.path.join(MODELS_DIR, "isolation_forest.joblib"))
joblib.dump(hybrid, os.path.join(MODELS_DIR, "xgboost_hybrid.joblib"))
joblib.dump(feature_names, os.path.join(MODELS_DIR, "feature_names.joblib"))
print(f"\nSaved models to {MODELS_DIR}")