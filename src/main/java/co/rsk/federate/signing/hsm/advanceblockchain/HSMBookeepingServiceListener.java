package co.rsk.federate.signing.hsm.advanceblockchain;

public interface HSMBookeepingServiceListener {
    void onIrrecoverableError(Throwable e);
}
