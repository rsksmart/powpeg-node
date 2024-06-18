package co.rsk.federate.config;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
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

    public static final String DIFFICULTY_TARGET_PATH = "bookkeeping.difficultyTarget";

    public static final String MAX_AMOUNT_BLOCK_HEADERS_PATH = "bookkeeping.maxAmountBlockHeaders";
    public static final int MAX_AMOUNT_BLOCK_HEADERS_DEFAULT = 100;

    public static final String MAX_CHUNK_SIZE_TO_HSM_PATH = "bookkeeping.maxChunkSizeToHsm";
    public static final int MAX_CHUNK_SIZE_TO_HSM_DEFAULT = 100;

    public static final String INFORMER_INTERVAL_PATH = "bookkeeping.informerInterval";
    public static final long INFORMER_INTERVAL_DEFAULT = 6 * 60 * 1000L; // 6 minutes in milliseconds

    public static final String STOP_BOOKKEPING_SCHEDULER_PATH = "bookkeeping.stopBookkeepingScheduler";
    public static final boolean STOP_BOOKKEPING_SCHEDULER_DEFAULT = false;

    private final Config signerConfig;
    private final String networkParameter;

    public PowHSMBookkeepingConfig(SignerConfig signerConfig, String networkParameter) {
        this.signerConfig = signerConfig.getConfig();
        this.networkParameter = networkParameter;
    }

    /**
     * Retrieves the difficulty target from either the PowHSM or the configuration file.
     * <p>
     * This method first attempts to retrieve the difficulty target from the PowHSM using the provided
     * {@link HSMBookkeepingClient}. If the PowHSM version is unsupported, it falls back to retrieving
     * the difficulty target from the configuration file.
     * </p>
     *
     * @param hsmClient The client used to communicate with the PowHSM.
     * @return The difficulty target as a {@link BigInteger}.
     * @throws HSMClientException If there is an error communicating with the PowHSM.
     */
    public BigInteger getDifficultyTarget(HSMBookkeepingClient hsmClient) throws HSMClientException {
      try {
          logger.trace("[getDifficultyTarget] Retrieve minimum difficulty target from the PowHSM");

          return hsmClient.getBlockchainParameters().getMinimumDifficulty();
      } catch (HSMUnsupportedTypeException e) {
          logger.trace(
              "[getDifficultyTarget] Unsupported PowHSM version, retrieve difficulty target from config file", e);

          return new BigInteger(signerConfig.getString(DIFFICULTY_TARGET_PATH));
      }
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
