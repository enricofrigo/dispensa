package eu.frigo.dispensa.sync.core.policy;

public interface SyncPolicy {
    boolean canSyncNow();
    long getRetryIntervalMillis();
}
