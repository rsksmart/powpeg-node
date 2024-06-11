package co.rsk.federate.config;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.message.PowHSMBlockchainParameters;

import com.typesafe.config.Config;
import java.math.BigInteger;
import java.util.Optional;

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
     * Retrieves the difficulty target needed for the internal PowHSM blockchain.
     * <p>
     * The difficulty target can be obtained either from the configuration file or from the PowHSM.
     * If the difficulty target is specified in the configuration file (indicated by the path {@code DIFFICULTY_TARGET_PATH}),
     * and if the HSM client version is less than 3, the difficulty target will be read from the configuration file.
     * Otherwise, the difficulty target will be retrieved from the PowHSM.
     * </p>
     *
     * @param hsmClient the PowHSM client used to retrieve blockchain parameters.
     * @return the difficulty target as a {@link BigInteger}.
     * @throws HSMClientException if there is an error communicating with the PowHSM.
     * @throws IllegalStateException if the difficulty target cannot be read from the configuration file or
     *                               if it cannot be retrieved from the PowHSM.
     */
    public BigInteger getDifficultyTarget(HSMBookkeepingClient hsmClient)
        throws HSMClientException {
    System.out.println(hsmClient);
    System.out.println(hsmClient.getVersion());
      if (hsmClient.getVersion() < 3) {
          return Optional.ofNullable(signerConfig.getString(DIFFICULTY_TARGET_PATH))
              .map(BigInteger::new)
              .orElseThrow(() -> 
                  new IllegalStateException("Unable to read difficulty target from configuration file"));
      }

      logger.trace("[getDifficultyTarget] Retrieve minimum difficulty target from the PowHSM");

      return Optional.ofNullable(hsmClient.getBlockchainParameters())
          .map(PowHSMBlockchainParameters::getMinimumDifficulty)
          .orElseThrow(() ->
              new IllegalStateException("Unable to retrieve difficulty target using PowHSM"));
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
