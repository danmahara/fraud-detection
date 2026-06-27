import os
import time
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest as SkIsolationForest
from sklearn.metrics import roc_auc_score
from scipy.stats import spearmanr

from isolation_forest import IsolationForest  # our from-scratch version


# Locate the dataset relative to THIS file, so it runs from any directory.
HERE = os.path.dirname(os.path.abspath(__file__))
DATA_PATH = os.path.join(HERE, "..", "data", "creditcard.csv")

print("Loading dataset...")
df = pd.read_csv(DATA_PATH)
print("Shape:", df.shape)

# Drop Time. Features = V1..V28 + Amount; label = Class.
X_all = df.drop(columns=["Time", "Class"]).values
y_all = df["Class"].values
print("Fraud cases:", int(y_all.sum()), "of", len(y_all))

rng = np.random.RandomState(42)

# Training sample (fitting is cheap — each tree only uses 256 points anyway).
train_idx = rng.choice(len(X_all), size=50000, replace=False)
X_train = X_all[train_idx]

# Evaluation set: ALL frauds + a sample of normals. Includes every fraud so
# the AUC is meaningful, but stays small enough to score quickly.
fraud_idx = np.where(y_all == 1)[0]
normal_idx = np.where(y_all == 0)[0]
normal_sample = rng.choice(normal_idx, size=5000, replace=False)
eval_idx = np.concatenate([fraud_idx, normal_sample])
X_eval = X_all[eval_idx]
y_eval = y_all[eval_idx]
print("Eval set:", len(y_eval), "rows,", int(y_eval.sum()), "frauds")

# === Our from-scratch Isolation Forest ===
print("\nFitting OUR isolation forest...")
t0 = time.time()
ours = IsolationForest(n_trees=100, sample_size=256, random_state=42).fit(X_train)
print(f"  fit took {time.time() - t0:.1f}s")

print("Scoring with OUR forest (slow, pure-Python part — give it a minute)...")
t0 = time.time()
our_scores = ours.anomaly_score(X_eval)
print(f"  scoring took {time.time() - t0:.1f}s")
our_auc = roc_auc_score(y_eval, our_scores)

# === sklearn's Isolation Forest, same settings ===
print("\nFitting sklearn isolation forest...")
sk = SkIsolationForest(n_estimators=100, max_samples=256, random_state=42)
sk.fit(X_train)
# sklearn: higher score_samples = more NORMAL, so negate to get an anomaly score.
sk_scores = -sk.score_samples(X_eval)
sk_auc = roc_auc_score(y_eval, sk_scores)

# === Compare ===
rho, _ = spearmanr(our_scores, sk_scores)

print("\n================  RESULTS  ================")
print(f"  Our ROC-AUC vs labels:     {our_auc:.4f}")
print(f"  sklearn ROC-AUC vs labels: {sk_auc:.4f}")
print(f"  Rank correlation (ours vs sklearn): {rho:.4f}")
print("===========================================")