package co.rsk.federate.signing.hsm.config;

import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.*;

import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.federate.signing.config.SignerConfig;
import co.rsk.federate.signing.config.SignerType;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import com.typesafe.config.Config;
import java.math.BigInteger;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PowHSMConfig {

  private static final Logger logger = LoggerFactory.getLogger(PowHSMConfig.class);

  private final Config config;

  public PowHSMConfig(SignerConfig signerConfig) throws SignerException {
    if (signerConfig == null || signerConfig.getSignerType() != SignerType.HSM) {
      throw new SignerException("Signer config is not for PowHSM");
    }

    this.config = signerConfig.getConfig();
  }

  public String getHost() {
    return config.hasPath(HOST.getPath())
        ? config.getString(HOST.getPath())
        : HOST.getDefaultValue(Function.identity());
  }

  public int getPort() {
    return config.hasPath(PORT.getPath())
        ? config.getInt(PORT.getPath())
        : PORT.getDefaultValue(Integer::parseInt);
  }

  public int getMaxAttempts() {
    return config.hasPath(MAX_ATTEMPTS.getPath())
        ? config.getInt(MAX_ATTEMPTS.getPath())
        : MAX_ATTEMPTS.getDefaultValue(Integer::parseInt);
  }

  public int getIntervalBetweenAttempts() {
    return config.hasPath(INTERVAL_BETWEEN_ATTEMPTS.getPath())
        ? config.getInt(INTERVAL_BETWEEN_ATTEMPTS.getPath())
        : INTERVAL_BETWEEN_ATTEMPTS.getDefaultValue(Integer::parseInt);
  }

  public int getSocketTimeout() {
    return config.hasPath(SOCKET_TIMEOUT.getPath())
        ? config.getInt(SOCKET_TIMEOUT.getPath())
        : SOCKET_TIMEOUT.getDefaultValue(Integer::parseInt);
  }

  public int getMaxAmountBlockHeaders() {
    return config.hasPath(MAX_AMOUNT_BLOCK_HEADERS.getPath())
        ? config.getInt(MAX_AMOUNT_BLOCK_HEADERS.getPath())
        : MAX_AMOUNT_BLOCK_HEADERS.getDefaultValue(Integer::parseInt);
  }

  public long getInformerInterval() {
    return config.hasPath(INFORMER_INTERVAL.getPath())
        ? config.getLong(INFORMER_INTERVAL.getPath())
        : INFORMER_INTERVAL.getDefaultValue(Long::parseLong);
  }

  public boolean isStopBookkeepingScheduler() {
    return config.hasPath(STOP_BOOKKEEPING_SCHEDULER.getPath())
        ? config.getBoolean(STOP_BOOKKEEPING_SCHEDULER.getPath())
        : STOP_BOOKKEEPING_SCHEDULER.getDefaultValue(Boolean::parseBoolean);
  }

  public int getMaxChunkSizeToHsm() {
    return config.hasPath(MAX_CHUNK_SIZE_TO_HSM.getPath())
        ? config.getInt(MAX_CHUNK_SIZE_TO_HSM.getPath())
        : MAX_CHUNK_SIZE_TO_HSM.getDefaultValue(Integer::parseInt);
  }

  /**
   * Retrieves the difficulty target from either the PowHSM or the configuration
   * file. The value represents the minimum cumulative block difficulty to
   * consider a block sufficiently confirmed.
   * 
   * <p>
   * This method first attempts to retrieve the difficulty target from the PowHSM
   * using the provided {@link HSMBookkeepingClient}. If the PowHSM version is
   * unsupported, it falls back to retrieving the difficulty target from the
   * configuration file.
   * </p>
   *
   * @param hsmClient The client used to communicate with the PowHSM.
   * @return The difficulty target as a {@link BigInteger}.
   * @throws HSMClientException If there is an error communicating with the
   *                            PowHSM.
   */
  public BigInteger getDifficultyTarget(HSMBookkeepingClient hsmClient) throws HSMClientException {
    try {
      logger.trace("[getDifficultyTarget] Retrieve minimum difficulty target from the PowHSM");

      return hsmClient.getBlockchainParameters().getMinimumDifficulty();
    } catch (HSMUnsupportedTypeException e) {
      logger.trace(
          "[getDifficultyTarget] Unsupported PowHSM version, retrieve difficulty target from config file", e);

      return new BigInteger(config.getString(DIFFICULTY_TARGET.getPath()));
    }
  }

  /**
   * Retrieves the difficulty cap for a given network parameter.
   * The difficulty cap is determined based on the network type (mainnet, testnet,
   * or regtest). This value will be used to determine the maximum allowed
   * difficulty for any given block.
   * 
   * @param networkParameter the network parameter identifier, which can be one of
   *                         the following:
   *                         <ul>
   *                         <li>{@link NetworkParameters#ID_MAINNET} - Mainnet
   *                         network</li>
   *                         <li>{@link NetworkParameters#ID_TESTNET} - Testnet
   *                         network</li>
   *                         <li>{@link NetworkParameters#ID_REGTEST} - Regtest
   *                         network</li>
   *                         </ul>
   * @return the difficulty cap as a {@link BigInteger} for the specified network.
   * @throws IllegalArgumentException if the provided network parameter is
   *                                  invalid.
   */
  public BigInteger getDifficultyCap(String networkParameter) {
    switch (networkParameter) {
      case NetworkParameters.ID_MAINNET:
        return NetworkDifficultyCap.MAINNET.getDifficultyCap();
      case NetworkParameters.ID_TESTNET:
        return NetworkDifficultyCap.TESTNET.getDifficultyCap();
      case NetworkParameters.ID_REGTEST:
        return NetworkDifficultyCap.REGTEST.getDifficultyCap();
      default:
        String message = "Invalid network provided: " + networkParameter;
        logger.error(message);
        throw new IllegalArgumentException(message);
    }
  }
}
