package app.nn.agent;

/** Backwards compatibility alias for NnService. */
public class BuddyService extends NnService {
    public static NnService get() {
        return NnService.get();
    }
}
