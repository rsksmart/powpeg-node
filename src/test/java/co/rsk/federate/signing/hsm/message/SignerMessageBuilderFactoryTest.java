package co.rsk.federate.signing.hsm.message;

import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import java.util.Collections;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.ReceiptStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SignerMessageBuilderFactoryTest {

    private SignerMessageBuilderFactory factory;

    @BeforeEach
    void createFactory() {
        factory = new SignerMessageBuilderFactory(mock(ReceiptStore.class));
    }

    @ParameterizedTest()
    @ValueSource(ints = {-4, -3, -2, -1, 0, 3})
    void buildWithWrongVersion(int wrongVersion) {
        assertThrows(HSMUnsupportedVersionException.class, () -> factory.buildFromConfig(
            wrongVersion,
            mock(ReleaseCreationInformation.class)
        ));

    }

    @Test
    void buildFromHSMVersion1() throws HSMClientException {
        SignerMessageBuilder sigMessVersion1 = factory.buildFromConfig(
            1,
            mock(ReleaseCreationInformation.class)
        );
        assertTrue(sigMessVersion1 instanceof SignerMessageBuilderV1);
    }

    @Test
    void buildFromConfig_hsm_2_ok() throws HSMClientException {
        test_buildFromConfig_hsm(2);
    }

    @Test
    void buildFromConfig_hsm_4_ok() throws HSMClientException {
        test_buildFromConfig_hsm(4);
    }

    void test_buildFromConfig_hsm(int version) throws HSMUnsupportedVersionException {
        BlockHeaderBuilder blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));
        Block block = new Block(
            blockHeaderBuilder.setNumber(1).build(),
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            true,
            true
        );
        SignerMessageBuilder messageBuilder = factory.buildFromConfig(
            version,
            new ReleaseCreationInformation(
                block,
                mock(TransactionReceipt.class),
                Keccak256.ZERO_HASH,
                mock(BtcTransaction.class),
                createHash(1)
            )
        );
        assertTrue(messageBuilder instanceof PowHSMSignerMessageBuilder);
    }
}
