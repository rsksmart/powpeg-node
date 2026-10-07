package co.rsk.federate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import co.rsk.core.Coin;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.ReversibleTransactionExecutor.ReversibleTransactionParams;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.PendingState;
import co.rsk.federate.config.PowpegNodeSystemProperties;
import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.peg.Bridge;
import java.math.BigInteger;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Ethereum;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BridgeTransactionSenderTest {
    private static final RskAddress FEDERATOR_ADDRESS = new RskAddress("0x0000000000000000000000000000000000000123");
    private static final RskAddress COINBASE = new RskAddress("0x0000000000000000000000000000000000000456");
    private static final long FEDERATOR_GAS_PRICE = 60_000_000L;
    private static final long GAS_NEEDED = 48_000L;
    private static final byte[] LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE).toByteArray();
    private static final byte[] ZERO = BigInteger.ZERO.toByteArray();

    private Ethereum ethereum;
    private TransactionPool transactionPool;
    private ReversibleTransactionExecutor reversibleTransactionExecutor;
    private Block bestBlock;
    private BridgeTransactionSender bridgeTransactionSender;

    @BeforeEach
    void setUp() {
        ethereum = mock(Ethereum.class);
        transactionPool = mock(TransactionPool.class);
        reversibleTransactionExecutor = mock(ReversibleTransactionExecutor.class);

        bestBlock = mock(Block.class);
        when(bestBlock.getCoinbase()).thenReturn(COINBASE);
        when(bestBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(1));
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBestBlock()).thenReturn(bestBlock);

        PowpegNodeSystemProperties config = mock(PowpegNodeSystemProperties.class);
        when(config.federatorGasPrice()).thenReturn(FEDERATOR_GAS_PRICE);
        when(config.getNetworkConstants()).thenReturn(Constants.regtest());

        bridgeTransactionSender = new BridgeTransactionSender(
            ethereum,
            blockchain,
            transactionPool,
            reversibleTransactionExecutor,
            config
        );
    }

    @Test
    void callTx_executesLegacyBridgeCallAtBestBlock_andDecodesResult() {
        CallTransaction.Function function = Bridge.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT;
        ProgramResult programResult = new ProgramResult();
        programResult.setHReturn(DataWord.valueOf(42).getData());
        when(reversibleTransactionExecutor.executeTransactionAtBlock(eq(bestBlock), eq(COINBASE), any()))
            .thenReturn(programResult);

        BigInteger result = bridgeTransactionSender.callTx(FEDERATOR_ADDRESS, function);

        assertEquals(BigInteger.valueOf(42), result);
        ReversibleTransactionParams params = captureExecutedParams();
        assertLegacyBridgeCall(params, function.encode());
        assertArrayEquals(LONG_MAX_VALUE, params.gasPrice());
        assertArrayEquals(LONG_MAX_VALUE, params.gasLimit());
    }

    @Test
    void sendRskTx_estimatesGasWithLegacyBridgeCall_andSubmitsTxWithEstimatedGas() throws Exception {
        CallTransaction.Function function = Bridge.UPDATE_COLLECTIONS;
        arrangeGasEstimate(GAS_NEEDED);
        arrangeFederatorBalance(Coin.valueOf(Long.MAX_VALUE));
        ECDSASigner signer = signerReturningValidSignature();

        bridgeTransactionSender.sendRskTx(FEDERATOR_ADDRESS, signer, function);

        ReversibleTransactionParams params = captureExecutedParams();
        assertLegacyBridgeCall(params, function.encode());
        assertArrayEquals(BigInteger.valueOf(FEDERATOR_GAS_PRICE).toByteArray(), params.gasPrice());
        assertArrayEquals(LONG_MAX_VALUE, params.gasLimit());

        Transaction submittedTx = captureSubmittedTx();
        assertEquals(BigInteger.valueOf(GAS_NEEDED), new BigInteger(1, submittedTx.getGasLimit()));
        assertEquals(PrecompiledContracts.BRIDGE_ADDR, submittedTx.getReceiveAddress());
    }

    @Test
    void sendRskTx_whenFederatorGasPriceIsAboveMinimum_usesFederatorGasPrice() throws Exception {
        arrangeGasEstimate(GAS_NEEDED);
        arrangeFederatorBalance(Coin.valueOf(Long.MAX_VALUE));

        bridgeTransactionSender.sendRskTx(FEDERATOR_ADDRESS, signerReturningValidSignature(), Bridge.UPDATE_COLLECTIONS);

        assertEquals(Coin.valueOf(FEDERATOR_GAS_PRICE), captureSubmittedTx().getGasPrice());
    }

    @Test
    void sendRskTx_whenMinimumGasPriceIsAboveFederatorGasPrice_usesMinimumGasPriceWithGap() throws Exception {
        // The default gas price provider adds a 10% gap over the best block minimum gas price
        when(bestBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(100_000_000L));
        arrangeGasEstimate(GAS_NEEDED);
        arrangeFederatorBalance(Coin.valueOf(Long.MAX_VALUE));

        bridgeTransactionSender.sendRskTx(FEDERATOR_ADDRESS, signerReturningValidSignature(), Bridge.UPDATE_COLLECTIONS);

        assertEquals(Coin.valueOf(110_000_000L), captureSubmittedTx().getGasPrice());
    }

    @Test
    void sendRskTx_whenBalanceCoversExactTxCost_submitsTx() throws Exception {
        arrangeGasEstimate(GAS_NEEDED);
        arrangeFederatorBalance(Coin.valueOf(FEDERATOR_GAS_PRICE * GAS_NEEDED));

        bridgeTransactionSender.sendRskTx(FEDERATOR_ADDRESS, signerReturningValidSignature(), Bridge.UPDATE_COLLECTIONS);

        verify(ethereum).submitTransaction(any());
    }

    @Test
    void sendRskTx_whenBalanceIsNotEnough_doesNotSignNorSubmitTx() throws Exception {
        arrangeGasEstimate(GAS_NEEDED);
        arrangeFederatorBalance(Coin.valueOf(FEDERATOR_GAS_PRICE * GAS_NEEDED - 1));
        ECDSASigner signer = signerReturningValidSignature();

        bridgeTransactionSender.sendRskTx(FEDERATOR_ADDRESS, signer, Bridge.UPDATE_COLLECTIONS);

        verify(signer, never()).sign(any(), any());
        verify(ethereum, never()).submitTransaction(any());
    }

    @Test
    void sendRskTx_whenSigningFails_doesNotSubmitTx() throws Exception {
        arrangeGasEstimate(GAS_NEEDED);
        arrangeFederatorBalance(Coin.valueOf(Long.MAX_VALUE));
        ECDSASigner signer = mock(ECDSASigner.class);
        when(signer.sign(any(), any())).thenThrow(new SignerException("signer unavailable"));

        assertDoesNotThrow(() -> bridgeTransactionSender.sendRskTx(FEDERATOR_ADDRESS, signer, Bridge.UPDATE_COLLECTIONS));

        verify(signer).sign(any(), any());
        verify(ethereum, never()).submitTransaction(any());
    }

    private void arrangeGasEstimate(long gasNeeded) {
        ProgramResult programResult = new ProgramResult();
        programResult.spendGas(gasNeeded);
        when(reversibleTransactionExecutor.executeTransactionAtBlock(eq(bestBlock), eq(COINBASE), any()))
            .thenReturn(programResult);
    }

    private void arrangeFederatorBalance(Coin balance) {
        PendingState pendingState = mock(PendingState.class);
        when(pendingState.getBalance(FEDERATOR_ADDRESS)).thenReturn(balance);
        when(pendingState.getNonce(FEDERATOR_ADDRESS)).thenReturn(BigInteger.ZERO);
        when(transactionPool.getPendingState()).thenReturn(pendingState);
    }

    private static ECDSASigner signerReturningValidSignature() throws SignerException {
        ECDSASigner signer = mock(ECDSASigner.class);
        when(signer.sign(any(), any())).thenReturn(ECKey.fromPrivate(BigInteger.TEN).sign(new byte[32]));
        return signer;
    }

    private Transaction captureSubmittedTx() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(ethereum).submitTransaction(captor.capture());
        return captor.getValue();
    }

    private ReversibleTransactionParams captureExecutedParams() {
        ArgumentCaptor<ReversibleTransactionParams> captor = ArgumentCaptor.forClass(ReversibleTransactionParams.class);
        verify(reversibleTransactionExecutor).executeTransactionAtBlock(eq(bestBlock), eq(COINBASE), captor.capture());
        return captor.getValue();
    }

    private static void assertLegacyBridgeCall(ReversibleTransactionParams params, byte[] expectedData) {
        assertEquals(TransactionType.LEGACY, params.type());
        assertArrayEquals(PrecompiledContracts.BRIDGE_ADDR.getBytes(), params.toAddress());
        assertArrayEquals(ZERO, params.value());
        assertArrayEquals(expectedData, params.data());
        assertEquals(FEDERATOR_ADDRESS, params.fromAddress());
        assertEquals(0, params.chainId());
        assertNull(params.authorizationList());
        assertNull(params.accessListBytes());
        assertNull(params.maxPriorityFeePerGas());
        assertNull(params.maxFeePerGas());
    }
}
