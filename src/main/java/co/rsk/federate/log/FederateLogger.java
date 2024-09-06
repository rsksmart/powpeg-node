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

import co.rsk.federate.FederatorSupport;
import co.rsk.federate.util.CurrentTimeProvider;
import co.rsk.federate.util.LoggerProvider;
import org.bitcoinj.core.StoredBlock;
import org.ethereum.core.Block;
import org.slf4j.Logger;

public class FederateLogger {

  private final FederatorSupport federatorSupport;
  private final CurrentTimeProvider currentTimeProvider;
  private final long minTimeBetweenLogs;
  private final long minBlocksBetweenLogs;
  private final Logger monitoringLogger;

  private long lastLogTime = 0;
  private long lastBlockNumberLog = 0;
  private Block currentRskBestBlock;
  private StoredBlock currentBtcBestBlock;

  public FederateLogger(
      FederatorSupport federatorSupport,
      CurrentTimeProvider currentTimeProvider,
      LoggerProvider loggerProvider,
      long minTimeBetweenLogs,
      long minBlocksBetweenLogs) {
    this.federatorSupport = federatorSupport;
    this.currentTimeProvider = currentTimeProvider;
    this.minTimeBetweenLogs = minTimeBetweenLogs;
    this.minBlocksBetweenLogs = minBlocksBetweenLogs;
    this.monitoringLogger = loggerProvider.getLogger();
  }

  public void log() {
    long currentRskBestChainHeight = -1;
    if (currentRskBestBlock != null) {
      currentRskBestChainHeight = currentRskBestBlock.getNumber();
    }
    long currentBtcBestChainHeight = -1;
    if (currentBtcBestBlock != null) {
      currentBtcBestChainHeight = currentBtcBestBlock.getHeight();
    }
    long currentTimeMillis = currentTimeProvider.currentTimeMillis();
    if (currentRskBestChainHeight - lastBlockNumberLog > minBlocksBetweenLogs
        && currentTimeMillis - lastLogTime > minTimeBetweenLogs) {
      monitoringLogger.info("RSK height: {}", currentRskBestChainHeight);
      monitoringLogger.info("BTC height: {}", currentBtcBestChainHeight);
      monitoringLogger.info(
          "Bridge BTC height: {}", federatorSupport.getBtcBlockchainBestChainHeight());
      lastBlockNumberLog = currentRskBestChainHeight;
      lastLogTime = currentTimeMillis;
    }
  }

  public void setCurrentRskBestBlock(Block currentRskBestBlock) {
    this.currentRskBestBlock = currentRskBestBlock;
  }

  public void setCurrentBtcBestBlock(StoredBlock currentBtcBestBlock) {
    this.currentBtcBestBlock = currentBtcBestBlock;
  }
}
