package co.rsk.federate.config;

import co.rsk.bitcoinj.core.NetworkParameters;
import com.typesafe.config.Config;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PowHSMBookkeepingConfig {

    public enum NetworkDifficultyCap {
        MAINNET(new BigInteger("7000000000000000000000")),
        TESTNET(BigInteger.valueOf(1000000000000000L)),
        REGTEST(BigInteger.valueOf(20L));

        private final BigInteger difficultyCap;

        NetworkDifficultyCap(BigInteger difficultyCap) {
            this.difficultyCap = difficultyCap;
        }

        public BigInteger getDifficultyCap() {
            return difficultyCap;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(PowHSMBookkeepingConfig.class);

    private static final String DIFFICULTY_TARGET_PATH = "bookkeeping.difficultyTarget";
    private static final BigInteger DIFFICULTY_TARGET_DEFAULT = BigInteger.valueOf(3);

    private static final String MAX_AMOUNT_BLOCK_HEADERS_PATH = "bookkeeping.maxAmountBlockHeaders";
    private static final int MAX_AMOUNT_BLOCK_HEADERS_DEFAULT = 7;

    private static final String MAX_CHUNK_SIZE_TO_HSM_PATH = "bookkeeping.maxChunkSizeToHsm";
    private static final int MAX_CHUNK_SIZE_TO_HSM_DEFAULT = 10;

    private static final String INFORMER_INTERVAL_PATH = "bookkeeping.informerInterval";
    private static final long INFORMER_INTERVAL_DEFAULT = 2_000;

    private static final String STOP_BOOKKEPING_SCHEDULER_PATH = "bookkeeping.stopBookkeepingScheduler";
    private static final boolean STOP_BOOKKEPING_SCHEDULER_DEFAULT = false;

    private final Config signerConfig;
    private final String networkParameter;

    public PowHSMBookkeepingConfig(SignerConfig signerConfig, String networkParameter) {
        this.signerConfig = signerConfig.getConfig();
        this.networkParameter = networkParameter;
    }

    public BigInteger getDifficultyTarget() {
        return signerConfig.hasPath(DIFFICULTY_TARGET_PATH)
            ? new BigInteger(signerConfig.getString(DIFFICULTY_TARGET_PATH))
            : DIFFICULTY_TARGET_DEFAULT;
    }

    public int getMaxAmountBlockHeaders() {
        return signerConfig.hasPath(MAX_AMOUNT_BLOCK_HEADERS_PATH)
            ? signerConfig.getInt(MAX_AMOUNT_BLOCK_HEADERS_PATH)
            : MAX_AMOUNT_BLOCK_HEADERS_DEFAULT;
    }

    public long getInformerInterval() {
        return signerConfig.hasPath(INFORMER_INTERVAL_PATH)
            ? signerConfig.getLong(INFORMER_INTERVAL_PATH)
            : INFORMER_INTERVAL_DEFAULT;
    }

    public boolean isStopBookkeepingScheduler() {
        return signerConfig.hasPath(STOP_BOOKKEPING_SCHEDULER_PATH)
            ? signerConfig.getBoolean(STOP_BOOKKEPING_SCHEDULER_PATH)
            : STOP_BOOKKEPING_SCHEDULER_DEFAULT;
    }

    public int getMaxChunkSizeToHsm() {
        return signerConfig.hasPath(MAX_CHUNK_SIZE_TO_HSM_PATH)
            ? signerConfig.getInt(MAX_CHUNK_SIZE_TO_HSM_PATH)
            : MAX_CHUNK_SIZE_TO_HSM_DEFAULT;
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
