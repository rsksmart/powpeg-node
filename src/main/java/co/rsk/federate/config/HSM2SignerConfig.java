package co.rsk.federate.config;

/**
 * Represents the configuration for a signer.
 * Mainly has an identifier for the signer, the
 * type of signer and additional configuration options.
 *
 * @author Pamela Gonzalez
 */

import co.rsk.bitcoinj.core.NetworkParameters;

import java.math.BigInteger;

public class HSM2SignerConfig {

    public static final BigInteger DIFFICULTY_CAP_MAINNET = new BigInteger("7000000000000000000000");
    public static final BigInteger DIFFICULTY_CAP_TESTNET = BigInteger.valueOf(560_000_000L);
    public static final BigInteger DIFFICULTY_CAP_REGTEST = BigInteger.valueOf(20);
    private BigInteger difficultyCap;
    private BigInteger difficultyTarget = BigInteger.valueOf(3);
    private int maxAmountBlockHeaders = 7;
    private long informerInterval = 2_000;
    private boolean stopBookkeepingScheduler = false;
    private int maxChunkSizeToHsm = 10;

    public HSM2SignerConfig(SignerConfig signerConfig, String networkParameter) {
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

        switch (networkParameter) {
            case NetworkParameters.ID_MAINNET:
                this.difficultyCap = DIFFICULTY_CAP_MAINNET;
                break;
            case NetworkParameters.ID_TESTNET:
                this.difficultyCap = DIFFICULTY_CAP_TESTNET;
                break;
            case NetworkParameters.ID_REGTEST:
                this.difficultyCap = DIFFICULTY_CAP_REGTEST;
                break;
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

    public BigInteger getDifficultyCap() {
        return difficultyCap;
    }
}
