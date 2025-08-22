package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.message.PowHSMState;
import co.rsk.federate.signing.hsm.message.PowHSMBlockchainParameters;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import org.ethereum.core.Block;

import java.util.List;

public interface HSMBookkeepingClient {
    int getVersionNumber() throws HSMClientException;

    void updateAncestorBlock(UpdateAncestorBlockMessage updateAncestorBlockMessage) throws HSMClientException;

    void advanceBlockchain(List<Block> blocks) throws HSMClientException;

    PowHSMState getHSMPointer() throws HSMClientException;

    void resetAdvanceBlockchain() throws HSMClientException;

    void setMaxChunkSizeToHsm(int maxChunkSizeToHsm);

    void setStopSending();

    PowHSMBlockchainParameters getBlockchainParameters() throws HSMClientException;
}
