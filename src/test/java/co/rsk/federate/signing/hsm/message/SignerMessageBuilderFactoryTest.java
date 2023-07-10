package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.utils.TestUtils;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.ReceiptStore;
import org.junit.Before;
import org.junit.Test;

import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class SignerMessageBuilderFactoryTest {

    private SignerMessageBuilderFactory factory;

    @Before
    public void createFactory() {
        factory = new SignerMessageBuilderFactory(mock(ReceiptStore.class));
    }

    @Test(expected = HSMUnsupportedVersionException.class)
    public void buildWithWrongVersion() throws HSMClientException {
        factory.buildFromConfig(-5, mock(ReleaseCreationInformation.class));
    }

    @Test
    public void buildFromHSMVersion1() throws HSMClientException {
        SignerMessageBuilder sigMessVersion1 = factory.buildFromConfig(1, mock(ReleaseCreationInformation.class));
        assertTrue(sigMessVersion1 instanceof SignerMessageBuilderV1);
    }

    @Test
    public void buildFromConfig_hsm_2_ok() throws HSMClientException {
        test_buildFromConfig_hsm(2);
    }

    @Test
    public void buildFromConfig_hsm_3_ok() throws HSMClientException {
        test_buildFromConfig_hsm(3);
    }

    @Test
    public void buildFromConfig_hsm_4_ok() throws HSMClientException {
        test_buildFromConfig_hsm(4);
    }

    public void test_buildFromConfig_hsm(int version) throws HSMUnsupportedVersionException {
        SignerMessageBuilder messageBuilder = factory.buildFromConfig(
            version,
            new ReleaseCreationInformation(
                TestUtils.mockBlock(1),
                mock(TransactionReceipt.class),
                Keccak256.ZERO_HASH,
                mock(BtcTransaction.class),
                createHash(1)
            )
        );
        assertTrue(messageBuilder instanceof PowHSMSignerMessageBuilder);
    }
}
