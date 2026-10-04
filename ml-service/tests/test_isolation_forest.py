import numpy as np
from service.isolation_forest import IsolationForest, average_path_length


def make_data():
    rng = np.random.RandomState(42)
    normal = rng.randn(1000, 2)
    outliers = np.array([[8, 8], [-8, 8], [8, -8], [-8, -8], [10, 0]])
    return np.vstack([normal, outliers])


def test_c_function():
    assert average_path_length(1) == 0.0
    assert average_path_length(2) == 1.0
    assert abs(average_path_length(256) - 10.24) < 0.01


def test_outliers_score_higher_than_normal():
    data = make_data()
    s = IsolationForest(100, 256, random_state=42).fit(data).anomaly_score(data)
    assert s[1000:].min() > s[:1000].mean()


def test_scores_between_0_and_1():
    data = make_data()
    s = IsolationForest(50, 256, random_state=1).fit(data).anomaly_score(data[:100])
    assert ((s > 0) & (s < 1)).all()


def test_same_seed_same_scores():
    data = make_data()
    a = IsolationForest(20, 256, random_state=7).fit(data).anomaly_score(data[:30])
    b = IsolationForest(20, 256, random_state=7).fit(data).anomaly_score(data[:30])
    assert np.allclose(a, b)


def test_height_limit():
    assert IsolationForest(10, 256).max_height == 8