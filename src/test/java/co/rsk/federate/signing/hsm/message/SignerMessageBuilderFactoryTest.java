package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.ECDSAHSMSigner;
import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.client.HSMClient;
import co.rsk.federate.signing.hsm.client.HSMClientVersion2BTC;
import co.rsk.federate.signing.hsm.client.HSMClientVersion2RskMst;
import co.rsk.federate.signing.utils.TestUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SignerMessageBuilderFactoryTest {
    private SignerMessageBuilderFactory factory;
    private co.rsk.bitcoinj.core.NetworkParameters params = RegTestParams.get();

    @Before
    public void createFactory() {
        factory = new SignerMessageBuilderFactory(mock(ReceiptStore.class));
    }

    @Test(expected = HSMUnsupportedVersionException.class)
    public void buildWithWrongVersion() throws HSMClientException {
        factory.buildFromConfig(0, mock(ReleaseCreationInformation.class));
    }

    @Test
    public void buildFromHSMVersion1() throws HSMClientException {
        SignerMessageBuilder sigMessVersion1 = factory.buildFromConfig(1, mock(ReleaseCreationInformation.class));
        assertTrue(sigMessVersion1 instanceof SignerMessageBuilderVersion1);
    }

    @Test
    public void buildFromConfig_hsm_2_ok() throws HSMClientException {
        SignerMessageBuilder messageBuilder = factory.buildFromConfig(
                2,
                new ReleaseCreationInformation(
                    TestUtils.mockBlock(1),
                    mock(TransactionReceipt.class),
                    Keccak256.ZERO_HASH,
                    mock(BtcTransaction.class),
                    createHash(1)
                )
        );
        assertTrue(messageBuilder instanceof SignerMessageBuilderVersion2);
    }
}
