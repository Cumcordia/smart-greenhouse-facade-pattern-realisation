package greenhouse.facade;

public enum ChainStep {
    A, B, C;

    public boolean isAtLeast(ChainStep other) {
        return compareTo(other) >= 0;
    }
}
