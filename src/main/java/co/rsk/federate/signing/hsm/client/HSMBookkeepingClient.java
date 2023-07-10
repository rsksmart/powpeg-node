package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.message.AdvanceBlockchainMessage;
import co.rsk.federate.signing.hsm.message.PowHSMState;
import co.rsk.federate.signing.hsm.message.PowHSMBlockchainParameters;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;

public interface HSMBookkeepingClient {
    int getVersion() throws HSMClientException;

    void updateAncestorBlock(UpdateAncestorBlockMessage updateAncestorBlockMessage) throws HSMClientException;

    void advanceBlockchain(AdvanceBlockchainMessage advanceBlockchainMessage) throws HSMClientException;

    PowHSMState getHSMPointer() throws HSMClientException;

    void resetAdvanceBlockchain() throws HSMClientException;

    void setMaxChunkSizeToHsm(int maxChunkSizeToHsm);

    void setStopSending();

    PowHSMBlockchainParameters getBlockchainParameters() throws HSMClientException;
}
