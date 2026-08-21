package org.hero.strawgolem.network;

import java.util.List;

/**
 * Client-side holding pen for the last roster the server sent.
 *
 * <p>The directory screen opens instantly off local entities and then fills in
 * the full picture when the response lands, so it never blocks on the network -
 * a roster that appears a tick late is much better than a screen that hangs.
 */
public final class RosterCache {
    private static volatile List<RosterEntry> entries = List.of();
    private static volatile boolean awaiting = false;
    private static volatile int version = 0;

    private RosterCache() {}

    public static void accept(List<RosterEntry> incoming) {
        entries = List.copyOf(incoming);
        awaiting = false;
        version++;
    }

    /**
     * Bumped every time a response lands. The screen watches this so it can
     * repopulate the moment the roster arrives, rather than polling or blocking.
     */
    public static int version() {
        return version;
    }

    public static List<RosterEntry> get() {
        return entries;
    }

    /** True while a request is in flight, so the screen can say "asking...". */
    public static boolean isAwaiting() {
        return awaiting;
    }

    public static void markRequested() {
        awaiting = true;
    }
}
