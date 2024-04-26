/*
 * This file is part of RskJ
 * Copyright (C) 2020-2024 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.federate.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import co.rsk.federate.FederatorSupport;
import co.rsk.federate.util.LoggerProvider;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.bitcoinj.core.StoredBlock;
import org.ethereum.core.Block;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

class FederateLoggerTest {

  @Test
  void logOk() {
    FederatorSupport federatorSupport = mock(FederatorSupport.class);
    AtomicLong currentTime = new AtomicLong(1);
    Logger logger = mock(Logger.class);
    LoggerProvider loggerProvider = () -> logger;
    FederateLogger monitoringLogger =
        new FederateLogger(federatorSupport, currentTime::get, loggerProvider, 0, 0);

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
    assertEquals(rskBestBlockHeight, loggerArguments.get(0));
    assertEquals((long) btcBestBlockHeight, loggerArguments.get(1));
    assertEquals(bridgeBtcHeight, loggerArguments.get(2));
  }

  @Test
  void dontLogOnEveryBlock() {
    FederatorSupport federatorSupport = mock(FederatorSupport.class);
    AtomicLong currentTime = new AtomicLong(1);
    Logger logger = mock(Logger.class);
    LoggerProvider loggerProvider = () -> logger;
    FederateLogger monitoringLogger =
        new FederateLogger(federatorSupport, currentTime::get, loggerProvider, 0, 1);

    long rskBestBlockHeight = 1L;
    Block currentRskBestBlock = mock(Block.class);
    doReturn(rskBestBlockHeight).when(currentRskBestBlock).getNumber();
    monitoringLogger.setCurrentRskBestBlock(currentRskBestBlock);

    monitoringLogger.log();

    verifyNoInteractions(logger);
  }

  @Test
  void dontFloodLogger() {
    FederatorSupport federatorSupport = mock(FederatorSupport.class);
    AtomicLong currentTime = new AtomicLong(1);
    Logger logger = mock(Logger.class);
    LoggerProvider loggerProvider = () -> logger;
    FederateLogger monitoringLogger =
        new FederateLogger(federatorSupport, currentTime::get, loggerProvider, 1, 0);

    monitoringLogger.log();

    verifyNoInteractions(logger);
  }
}
