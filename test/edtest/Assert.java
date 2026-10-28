package edtest;

public final class Assert {
    private Assert() {
    }

    public static void isTrue(boolean cond, String what) {
        if (!cond) {
            throw new AssertionError(what);
        }
    }

    public static void eq(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void fails(Runnable r, String messagePart) {
        try {
            r.run();
        } catch (RuntimeException e) {
            if (messagePart != null && (e.getMessage() == null || !e.getMessage().contains(messagePart))) {
                throw new AssertionError("expected failure containing <" + messagePart + "> but was <" + e.getMessage() + ">");
            }
            return;
        }
        throw new AssertionError("expected a failure containing <" + messagePart + ">");
    }
}
