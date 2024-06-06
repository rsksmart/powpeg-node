package co.rsk.federate.config;

import co.rsk.bitcoinj.core.NetworkParameters;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PowHSMBookkeepingConfig {

    private static final Logger logger = LoggerFactory.getLogger(PowHSMBookkeepingConfig.class);

    enum NetworkDifficultyCap {
        MAINNET(new BigInteger("7000000000000000000000")),
        TESTNET(BigInteger.valueOf(1000000000000000L)),
        REGTEST(BigInteger.valueOf(20L));

        private final BigInteger difficultyCap;

        NetworkDifficultyCap(BigInteger difficultyCap) {
            this.difficultyCap = difficultyCap;
        }

        BigInteger getDifficultyCap() {
            return difficultyCap;
        }
    }

    private static final BigInteger DEFAULT_DIFFICULTY_TARGET = BigInteger.valueOf(3);
    private static final boolean DEFAULT_STOP_BOOKKEPING_SCHEDULER = false;
    private static final int DEFAULT_MAX_AMOUNT_BLOCK_HEADERS = 7;
    private static final int DEFAULT_MAX_CHUNK_SIZE = 10;
    private static final long DEFAULT_INFORMER_INTERVAL = 2_000;

    private final SignerConfig signerConfig;
    private final String networkParameter;

    public PowHSMBookkeepingConfig(SignerConfig signerConfig, String networkParameter) {
        this.signerConfig = signerConfig;
        this.networkParameter = networkParameter;
    }

    public BigInteger getDifficultyTarget() {
        return signerConfig.getConfig().hasPath("bookkeeping.difficultyTarget")
            ? new BigInteger(signerConfig.getConfig().getString("bookkeeping.difficultyTarget"))
            : DEFAULT_DIFFICULTY_TARGET;
    }

    public int getMaxAmountBlockHeaders() {
        return signerConfig.getConfig().hasPath("bookkeeping.maxAmountBlockHeaders")
            ? signerConfig.getConfig().getInt("bookkeeping.maxAmountBlockHeaders")
            : DEFAULT_MAX_AMOUNT_BLOCK_HEADERS;
    }

    public long getInformerInterval() {
        return signerConfig.getConfig().hasPath("bookkeeping.informerInterval")
            ? signerConfig.getConfig().getLong("bookkeeping.informerInterval")
            : DEFAULT_INFORMER_INTERVAL;
    }

    public boolean isStopBookkeepingScheduler() {
        return signerConfig.getConfig().hasPath("bookkeeping.stopBookkeepingScheduler")
            ? signerConfig.getConfig().getBoolean("bookkeeping.stopBookkeepingScheduler")
            : DEFAULT_STOP_BOOKKEPING_SCHEDULER;
    }

    public int getMaxChunkSizeToHsm() {
        return signerConfig.getConfig().hasPath("bookkeeping.maxChunkSizeToHsm")
            ? signerConfig.getConfig().getInt("bookkeeping.maxChunkSizeToHsm")
            : DEFAULT_MAX_CHUNK_SIZE;
    }

    public BigInteger getDifficultyCap() {
        switch (networkParameter) {
            case NetworkParameters.ID_MAINNET:
                return NetworkDifficultyCap.MAINNET.getDifficultyCap();
            case NetworkParameters.ID_TESTNET:
                return NetworkDifficultyCap.TESTNET.getDifficultyCap();
            case NetworkParameters.ID_REGTEST:
                return NetworkDifficultyCap.REGTEST.getDifficultyCap();
            default:
                String message = "Invalid network specified for the Bookkeeping Config: " + networkParameter;
                logger.error(message);
                throw new IllegalArgumentException(message);
        }
    }
}
