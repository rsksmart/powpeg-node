package co.rsk.federate.log;

import co.rsk.federate.bitcoin.BitcoinWrapper;

public class BtcLogMonitor {

  private final BitcoinWrapper bitcoinWrapper;
  private final FederateLogger monitoringLogger;

  public BtcLogMonitor(BitcoinWrapper bitcoinWrapper, FederateLogger monitoringLogger) {
    this.bitcoinWrapper = bitcoinWrapper;
    this.monitoringLogger = monitoringLogger;
  }

  public void start() {
    bitcoinWrapper.addNewBlockListener(monitoringLogger::setCurrentBtcBestBlock);
  }
}
