package eu.frigo.dispensa.sync.core.event;

public abstract class SyncEvent {
    public final long timestamp = System.currentTimeMillis();

    public static class Started extends SyncEvent {}

    public static class Progress extends SyncEvent {
        public final int percent;
        public Progress(int percent) {
            this.percent = percent;
        }
    }

    public static class Success extends SyncEvent {}

    public static class Failure extends SyncEvent {
        public final Throwable error;
        public Failure(Throwable error) {
            this.error = error;
        }
    }
}
