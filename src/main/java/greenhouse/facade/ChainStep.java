package greenhouse.facade;

/**
 * Шаги цепочки в порядке выполнения. Нужен, чтобы определить глубину отката:
 * чем дальше цепочка зашла, тем больше шагов надо отменить (в обратном порядке).
 */
public enum ChainStep {
    A, B, C;

    public boolean isAtLeast(ChainStep other) {
        return compareTo(other) >= 0;
    }
}
