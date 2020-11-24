package co.rsk.federate.config;

/**
 * Represents the configuration for a signer.
 * Mainly has an identifier for the signer, the
 * type of signer and additional configuration options.
 *
 * @author Pamela Gonzalez
 */

import java.math.BigInteger;

public class HSM2SignerConfig {

    private BigInteger difficultyTarget = BigInteger.valueOf(3);
    private int maxAmountBlockHeaders = 7;
    private long informerInterval = 2_000;
    private boolean stopBookkeepingScheduler = false;
    private int maxChunkSizeToHsm = 10;

    public HSM2SignerConfig(SignerConfig signerConfig)
    {
        if (signerConfig.getConfig().hasPath("bookkeeping.difficultyTarget")) {
            this.difficultyTarget = new BigInteger(signerConfig.getConfig().getString("bookkeeping.difficultyTarget"));
        }
        if (signerConfig.getConfig().hasPath("bookkeeping.maxAmountBlockHeaders")) {
            this.maxAmountBlockHeaders = signerConfig.getConfig().getInt("bookkeeping.maxAmountBlockHeaders");
        }
        if (signerConfig.getConfig().hasPath("bookkeeping.informerInterval")) {
            this.informerInterval = signerConfig.getConfig().getLong("bookkeeping.informerInterval");
        }
        if (signerConfig.getConfig().hasPath("bookkeeping.stopBookkeepingScheduler")) {
            this.stopBookkeepingScheduler = signerConfig.getConfig().getBoolean("bookkeeping.stopBookkeepingScheduler");
        }
        if (signerConfig.getConfig().hasPath("bookkeeping.maxChunkSizeToHsm")) {
            this.maxChunkSizeToHsm = signerConfig.getConfig().getInt("bookkeeping.maxChunkSizeToHsm");
        }
    }

    public BigInteger getDifficultyTarget() {
        return difficultyTarget;
    }

    public int getMaxAmountBlockHeaders() {
        return maxAmountBlockHeaders;
    }

    public long getInformerInterval() {
        return informerInterval;
    }

    public boolean isStopBookkeepingScheduler() {
        return stopBookkeepingScheduler;
    }

    public int getMaxChunkSizeToHsm() {
        return maxChunkSizeToHsm;
    }
}
