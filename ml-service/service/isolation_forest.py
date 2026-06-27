import numpy as np


# ---------------------------------------------------------------------------
# c(n): expected path length of an unsuccessful search in a Binary Search Tree
# of n points. We divide by this to NORMALISE path lengths, so a length is
# judged relative to how many points were in the tree.
#     c(n) = 2*H(n-1) - (2*(n-1)/n),  H(i) ≈ ln(i) + 0.5772156649 (Euler's const)
# ---------------------------------------------------------------------------
def average_path_length(n):
    if n <= 1:
        return 0.0          # a single point can't be split → path length 0
    if n == 2:
        return 1.0          # two points: exactly one split separates them
    euler_mascheroni = 0.5772156649
    harmonic = np.log(n - 1) + euler_mascheroni
    return 2.0 * harmonic - (2.0 * (n - 1) / n)


# ---------------------------------------------------------------------------
# One node of a tree. Either an INTERNAL node (a split: feature + value + two
# children) or an EXTERNAL/leaf node (just how many points landed there).
# ---------------------------------------------------------------------------
class IsolationTreeNode:
    def __init__(self):
        self.split_feature = None   # which feature index we split on
        self.split_value = None     # the random threshold
        self.left = None            # child for values < split_value
        self.right = None           # child for values >= split_value
        self.size = 0               # (leaf only) how many points reached here
        self.is_leaf = False


# ---------------------------------------------------------------------------
# A single isolation tree (iTree).
# ---------------------------------------------------------------------------
class IsolationTree:
    def __init__(self, max_height):
        # Stop growing past this depth; remaining points become a leaf and we
        # estimate their "would-be" extra depth with c(size).
        self.max_height = max_height
        self.root = None

    def fit(self, X):
        self.root = self._build(X, current_height=0)
        return self

    def _build(self, X, current_height):
        node = IsolationTreeNode()
        n_samples = X.shape[0]

        # Stop: too deep, or only one point left → make a leaf.
        if current_height >= self.max_height or n_samples <= 1:
            node.is_leaf = True
            node.size = n_samples
            return node

        n_features = X.shape[1]
        feature = np.random.randint(0, n_features)          # random feature
        feature_min = X[:, feature].min()
        feature_max = X[:, feature].max()

        # Can't split if every value in this feature is identical → leaf.
        if feature_min == feature_max:
            node.is_leaf = True
            node.size = n_samples
            return node

        split_value = np.random.uniform(feature_min, feature_max)  # random cut

        left_mask = X[:, feature] < split_value
        right_mask = ~left_mask

        node.is_leaf = False
        node.split_feature = feature
        node.split_value = split_value
        node.left = self._build(X[left_mask], current_height + 1)
        node.right = self._build(X[right_mask], current_height + 1)
        return node

    def path_length(self, x):
        return self._path_length(x, self.root, current_length=0)

    def _path_length(self, x, node, current_length):
        if node.is_leaf:
            # If the leaf still holds >1 point (we hit max_height), add the
            # expected remaining depth those points would have needed.
            return current_length + average_path_length(node.size)
        if x[node.split_feature] < node.split_value:
            return self._path_length(x, node.left, current_length + 1)
        else:
            return self._path_length(x, node.right, current_length + 1)


# ---------------------------------------------------------------------------
# The forest: many trees, each on a small random subsample. A point's score
# comes from its AVERAGE path length across all trees.
# ---------------------------------------------------------------------------
class IsolationForest:
    def __init__(self, n_trees=100, sample_size=256, random_state=None):
        self.n_trees = n_trees           # paper default: 100
        self.sample_size = sample_size   # paper default: 256
        self.random_state = random_state
        self.trees = []
        # Each tree's max height = ceil(log2(sample_size)). 256 → 8.
        self.max_height = int(np.ceil(np.log2(sample_size)))

    def fit(self, X):
        if self.random_state is not None:
            np.random.seed(self.random_state)
        X = np.asarray(X)
        n_samples = X.shape[0]
        self.trees = []

        for _ in range(self.n_trees):
            # Random subsample (without replacement) for this tree.
            if n_samples > self.sample_size:
                idx = np.random.choice(n_samples, self.sample_size, replace=False)
                X_sub = X[idx]
            else:
                X_sub = X
            self.trees.append(IsolationTree(self.max_height).fit(X_sub))

        # Normalise using the SUBSAMPLE size (not the full dataset).
        self.c = average_path_length(self.sample_size)
        return self

    def anomaly_score(self, X):
        X = np.asarray(X)
        scores = np.zeros(X.shape[0])
        for i in range(X.shape[0]):
            mean_path = np.mean([tree.path_length(X[i]) for tree in self.trees])
            scores[i] = 2.0 ** (-mean_path / self.c)   # s = 2^(-E(h(x))/c(n))
        return scores


# ---------------------------------------------------------------------------
# Quick smoke test: run `python isolation_forest.py`. 1000 normal points in a
# tight cluster + 5 obvious outliers far away. The outliers should score high
# (toward 1), the normal points lower (around 0.5 or below).
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    rng = np.random.RandomState(42)
    normal = rng.randn(1000, 2)
    outliers = np.array([[8, 8], [-8, 8], [8, -8], [-8, -8], [10, 0]])
    data = np.vstack([normal, outliers])

    forest = IsolationForest(n_trees=100, sample_size=256, random_state=42).fit(data)
    scores = forest.anomaly_score(data)

    print("Mean score of normal points:", round(scores[:1000].mean(), 3))
    print("Scores of the 5 outliers:   ", np.round(scores[1000:], 3))