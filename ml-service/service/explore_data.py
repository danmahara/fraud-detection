import os
import pandas as pd

HERE = os.path.dirname(os.path.abspath(__file__))
df = pd.read_csv(os.path.join(HERE, "..", "data", "fraudTrain.csv"))

print("Shape:", df.shape)
print("\nColumns:")
print(list(df.columns))

print("\nFraud distribution:")
print(df["is_fraud"].value_counts())
print(f"Fraud rate: {df['is_fraud'].mean() * 100:.3f}%")

print("\nMerchant categories:")
print(df["category"].unique())

print("\nAmount stats:")
print(df["amt"].describe())

print("\nFirst 2 rows (transposed so every column is readable):")
print(df.head(2).T)