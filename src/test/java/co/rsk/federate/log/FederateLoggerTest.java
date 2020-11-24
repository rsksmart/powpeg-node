package co.rsk.federate.log;

import co.rsk.federate.FederatorSupport;
import co.rsk.federate.util.LoggerProvider;
import org.bitcoinj.core.StoredBlock;
import org.ethereum.core.Block;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class FederateLoggerTest {

    @Test
    public void logOk() {
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        AtomicLong currentTime = new AtomicLong(1);
        Logger logger = mock(Logger.class);
        LoggerProvider loggerProvider = () -> logger;
        FederateLogger monitoringLogger = new FederateLogger(
                federatorSupport,
            currentTime::get,
            loggerProvider,
            0,
            0
        );

        int bridgeBtcHeight = 1;
        doReturn(bridgeBtcHeight).when(federatorSupport).getBtcBlockchainBestChainHeight();

        long rskBestBlockHeight = 1L;
        Block currentRskBestBlock = mock(Block.class);
        doReturn(rskBestBlockHeight).when(currentRskBestBlock).getNumber();
        monitoringLogger.setCurrentRskBestBlock(currentRskBestBlock);

        int btcBestBlockHeight = 1;
        StoredBlock currentBtcBestBlock = mock(StoredBlock.class);
        doReturn(btcBestBlockHeight).when(currentBtcBestBlock).getHeight();
        monitoringLogger.setCurrentBtcBestBlock(currentBtcBestBlock);

        monitoringLogger.log();

        ArgumentCaptor<Object> loggerArgumentsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(logger, times(3)).info(anyString(), loggerArgumentsCaptor.capture());
        List<Object> loggerArguments = loggerArgumentsCaptor.getAllValues();
        assertThat(loggerArguments.get(0), is(rskBestBlockHeight));
        assertThat(loggerArguments.get(1), is((long) btcBestBlockHeight));
        assertThat(loggerArguments.get(2), is(bridgeBtcHeight));
    }

    @Test
    public void dontLogOnEveryBlock() {
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        AtomicLong currentTime = new AtomicLong(1);
        Logger logger = mock(Logger.class);
        LoggerProvider loggerProvider = () -> logger;
        FederateLogger monitoringLogger = new FederateLogger(
                federatorSupport,
                currentTime::get,
                loggerProvider,
                0,
                1
        );

        long rskBestBlockHeight = 1L;
        Block currentRskBestBlock = mock(Block.class);
        doReturn(rskBestBlockHeight).when(currentRskBestBlock).getNumber();
        monitoringLogger.setCurrentRskBestBlock(currentRskBestBlock);

        monitoringLogger.log();

        verifyZeroInteractions(logger);
    }

    @Test
    public void dontFloodLogger() {
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        AtomicLong currentTime = new AtomicLong(1);
        Logger logger = mock(Logger.class);
        LoggerProvider loggerProvider = () -> logger;
        FederateLogger monitoringLogger = new FederateLogger(
                federatorSupport,
                currentTime::get,
                loggerProvider,
                1,
                0
        );

        monitoringLogger.log();

        verifyZeroInteractions(logger);
    }
}