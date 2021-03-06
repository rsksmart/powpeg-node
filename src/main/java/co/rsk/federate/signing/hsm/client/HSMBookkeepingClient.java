package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.message.AdvanceBlockchainMessage;
import co.rsk.federate.signing.hsm.message.HSM2State;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import org.ethereum.core.BlockHeader;

public interface HSMBookkeepingClient extends HSMClient {
    void updateAncestorBlock(UpdateAncestorBlockMessage updateAncestorBlockMessage) throws HSMClientException;

    void advanceBlockchain(AdvanceBlockchainMessage advanceBlockchainMessage) throws HSMClientException;

    HSM2State getHSMPointer() throws HSMClientException;

    void resetAdvanceBlockchain() throws HSMClientException;

    void setMaxChunkSizeToHsm(int maxChunkSizeToHsm);

    void setStopSending();
}
