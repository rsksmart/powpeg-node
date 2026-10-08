package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.advanceblockchain.ConfirmedBlocksProvider.ConfirmedBlock;
import co.rsk.federate.signing.hsm.message.PowHSMState;
import co.rsk.federate.signing.hsm.message.PowHSMBlockchainParameters;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;

import java.util.List;

public interface HSMBookkeepingClient {
    HSMVersion getVersion();

    void updateAncestorBlock(UpdateAncestorBlockMessage updateAncestorBlockMessage) throws HSMClientException;

    void advanceBlockchain(List<ConfirmedBlock> confirmedBlocks) throws HSMClientException;

    PowHSMState getHSMPointer() throws HSMClientException;

    void resetAdvanceBlockchain() throws HSMClientException;

    void setMaxChunkSizeToHsm(int maxChunkSizeToHsm);

    void setStopSending();

    PowHSMBlockchainParameters getBlockchainParameters() throws HSMClientException;
}
