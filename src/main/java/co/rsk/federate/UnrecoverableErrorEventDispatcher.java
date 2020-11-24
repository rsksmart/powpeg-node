package co.rsk.federate;

public interface UnrecoverableErrorEventDispatcher {
    void addListener(UnrecoverableErrorEventListener listener);
}
