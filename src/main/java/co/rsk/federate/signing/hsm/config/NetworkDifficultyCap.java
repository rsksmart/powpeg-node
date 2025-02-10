package co.rsk.federate.signing.hsm.config;

import java.math.BigInteger;

public enum NetworkDifficultyCap {
  MAINNET(new BigInteger("7000000000000000000000")),
  TESTNET(BigInteger.valueOf(3000000000L)),
  REGTEST(BigInteger.valueOf(20L));

  private final BigInteger difficultyCap;

  NetworkDifficultyCap(BigInteger difficultyCap) {
    this.difficultyCap = difficultyCap;
  }

  public BigInteger getDifficultyCap() {
    return difficultyCap;
  }
}
