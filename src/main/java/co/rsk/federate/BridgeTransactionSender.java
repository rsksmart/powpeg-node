package co.rsk.federate;

import co.rsk.core.Coin;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.PendingState;
import co.rsk.federate.config.PowpegNodeSystemProperties;
import co.rsk.federate.gas.GasPriceProviderFactory;
import co.rsk.federate.gas.IGasPriceProvider;
import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.hsm.message.SignerMessageV1;
import co.rsk.federate.signing.hsm.SignerException;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Ethereum;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

import static co.rsk.federate.signing.PowPegNodeKeyId.RSK;

public class BridgeTransactionSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeTransactionSender.class);

    private final Ethereum ethereum;
    private final Blockchain blockchain;
    private final TransactionPool transactionPool;
    private final ReversibleTransactionExecutor reversibleTransactionExecutor;
    private final Coin gasPrice;
    private final PowpegNodeSystemProperties config;
    private final IGasPriceProvider gasPriceProvider;

    public BridgeTransactionSender(Ethereum ethereum,
                                   Blockchain blockchain,
                                   TransactionPool transactionPool,
                                   ReversibleTransactionExecutor reversibleTransactionExecutor,
                                   PowpegNodeSystemProperties config) {
        this.ethereum = ethereum;
        this.blockchain = blockchain;
        this.transactionPool = transactionPool;
        this.reversibleTransactionExecutor = reversibleTransactionExecutor;
        this.config = config;
        this.gasPrice = Coin.valueOf(config.federatorGasPrice());
        this.gasPriceProvider = GasPriceProviderFactory.get(config.gasPriceProviderConfig(), this.blockchain);
    }

    public <T> T callTx(RskAddress federatorAddress, CallTransaction.Function function) {
        return callTx(federatorAddress, function, null);
    }

    public <T> T callTx(RskAddress federatorAddress, CallTransaction.Function function, Object[] params) {
        logBridgeInteraction("call", function, params);
        params = params != null ? params : new Object[]{};
        Block bestBlock = blockchain.getBestBlock();
        byte[] longMaxValue = longToByteArray(Long.MAX_VALUE);
        ProgramResult res = reversibleTransactionExecutor.executeTransaction(
                bestBlock,
                bestBlock.getCoinbase(),
                longMaxValue,
                longMaxValue,
                PrecompiledContracts.BRIDGE_ADDR.getBytes(),
                longToByteArray(0L),
                function.encode(params),
                federatorAddress
        );
        T[] result = (T[]) function.decodeResult(res.getHReturn());
        return result[0];
    }

    public synchronized void sendRskTx(RskAddress federatorAddress,
                                       ECDSASigner signer,
                                       CallTransaction.Function function,
                                       Object... functionArgs) {
        logBridgeInteraction("send tx", function, functionArgs);

            PendingState pendingState = transactionPool.getPendingState();
            Block block = blockchain.getBestBlock();
            // First, calculate how much gas is needed
            long gasNeeded = reversibleTransactionExecutor.executeTransaction(
                    block,
                    block.getCoinbase(),
                    longToByteArray(gasPrice.asBigInteger().longValue()),
                    longToByteArray(Long.MAX_VALUE),
                    PrecompiledContracts.BRIDGE_ADDR.getBytes(),
                    longToByteArray(0L),
                    function.encode(functionArgs),
                    federatorAddress
            ).getGasUsed();

        synchronized (transactionPool) {
            Coin federatorRskBalance = pendingState.getBalance(federatorAddress);
            Coin minGasPrice = this.gasPriceProvider.get();
            Coin finalGasPrice = gasPrice.compareTo(minGasPrice) < 0 ? minGasPrice : gasPrice;

            // See if we can afford this (just useful after txs to the bridge are no longer gas free)
            Coin txCost = finalGasPrice.multiply(BigInteger.valueOf(gasNeeded));
            if (federatorRskBalance.compareTo(txCost) >= 0) {
                long nonce = pendingState.getNonce(federatorAddress).longValue();
                Transaction rskTx = CallTransaction.createCallTransaction(
                        nonce,
                        finalGasPrice.asBigInteger().longValue(),
                        gasNeeded,
                        PrecompiledContracts.BRIDGE_ADDR,
                        0,
                        function,
                        config.getNetworkConstants().getChainId(),
                        functionArgs);
                try {
                    SignerMessageV1 messageToSign = new SignerMessageV1(rskTx.getRawHash().getBytes());
                    ECKey.ECDSASignature txSignature = signer.sign(RSK.getKeyId(), messageToSign);
                    rskTx.setSignature(txSignature);
                    LOGGER.debug("[tx={} | nonce={} | method={}] Submit to Bridge", rskTx.getHash(), nonce, function.name);
                    ethereum.submitTransaction(rskTx);
                } catch (SignerException e) {
                    LOGGER.error("[tx={} | nonce={} | method={}] Could not sign RSK tx. {}", rskTx.getHash(), nonce, function.name, e);
                }
            } else {
                LOGGER.warn(
                        "[method={}] Not enough balance. Required: {}, Balance: {}",
                        function.name,
                        txCost,
                        federatorRskBalance
                );
            }
        }
    }

    private void logBridgeInteraction(String action, CallTransaction.Function function, Object... functionArgs) {
        if (LOGGER.isInfoEnabled()) {
            StringBuilder loggingMessage = new StringBuilder("Bridge ");
            loggingMessage.append(action);
            loggingMessage.append(" - method: {}");
            if (functionArgs != null && functionArgs.length != 0) {
                loggingMessage.append(", params: {}");
            }
            LOGGER.trace(loggingMessage.toString(), function.name, functionArgs);
        }
    }

    private static byte[] longToByteArray(long val) {
        return BigInteger.valueOf(val).toByteArray();
    }
}
