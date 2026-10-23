package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.utils.TestUtils;
import java.util.Collections;

import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.ReceiptStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SignerMessageBuilderFactoryTest {

    private SignerMessageBuilderFactory factory;
    private static final NetworkParameters btcMainnetParams = BridgeMainNetConstants.getInstance().getBtcParams();

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
    void buildFromHSMVersion1() throws HSMClientException, SignerMessageBuilderException {
        BtcTransaction tx = createP2shP2wshTx();
        ReleaseCreationInformation releaseCreationInformation = mock(ReleaseCreationInformation.class);
        when(releaseCreationInformation.getPegoutBtcTx()).thenReturn(tx);

        SignerMessageBuilder signerMessageVersion1 = factory.buildFromConfig(
            1,
            releaseCreationInformation
        );
        assertInstanceOf(SignerMessageBuilderV1.class, signerMessageVersion1);
    }

    @ParameterizedTest
    @EnumSource(value = HSMVersion.class, names = {"V5", "V6"})
    void buildFromConfig_ok(HSMVersion hsmVersion) throws HSMClientException, SignerMessageBuilderException {
        BtcTransaction tx = createP2shP2wshTx();

        SignerMessageBuilder messageBuilder = factory.buildFromConfig(
            hsmVersion.getNumber(),
            buildReleaseCreationInformation(tx)
        );
        assertInstanceOf(PowHSMSignerMessageBuilder.class, messageBuilder);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 6})
    void buildFromConfig_whenTxHasLegacyInput_throwsSignerMessageBuilderException(int version) {
        BtcTransaction tx = BitcoinTestUtils.createTxFromP2shMultiSig(btcMainnetParams);
        ReleaseCreationInformation releaseCreationInformation = buildReleaseCreationInformation(tx);

        assertThrows(SignerMessageBuilderException.class, () -> factory.buildFromConfig(
            version,
            releaseCreationInformation
        ));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 6})
    void buildFromConfig_whenTxHasSegwitAndLegacyInputs_throwsSignerMessageBuilderException(int version) {
        BtcTransaction tx = createP2shP2wshTx();
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        ReleaseCreationInformation releaseCreationInformation = buildReleaseCreationInformation(tx);

        assertThrows(SignerMessageBuilderException.class, () -> factory.buildFromConfig(
            version,
            releaseCreationInformation
        ));
    }

    private BtcTransaction createP2shP2wshTx() {
        Federation federation = TestUtils.createP2shP2wshErpFederation(btcMainnetParams, 9);
        return TestUtils.createBtcTransaction(btcMainnetParams, federation);
    }

    private ReleaseCreationInformation buildReleaseCreationInformation(BtcTransaction tx) {
        BlockHeaderBuilder blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));
        Block block = new Block(
            blockHeaderBuilder.setNumber(1).build(),
            Collections.emptyList(),
            Collections.emptyList(),
            true,
            true
        );

        return new ReleaseCreationInformation(
            block,
            mock(TransactionReceipt.class),
            Keccak256.ZERO_HASH,
            tx,
            Collections.emptyList()
        );
    }
}
