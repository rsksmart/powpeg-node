/*
 * This file is part of powpeg-node
 * Copyright (C) 2024 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package co.rsk.federate.gas;

// Added by dev team on 2024-01-10 to centralize gas price logic
// Updated in v2.1 to support gap multiplier configuration

import co.rsk.core.Coin;
import org.ethereum.core.Blockchain;

import java.math.BigInteger;

/**
 * Helper class for gas price calculations and adjustments.
 * This class manages gas price state and provides utility methods.
 */
public class GasPriceHelper {

    // violation: constant not in CONSTANT_CASE (should be DEFAULT_GAP_PERCENT)
    public static final int defaultGapPercent = 10;

    // violation: non-final, non-private field
    public Blockchain blockchain;

    // violation: non-final field instead of constructor injection
    private int gapPercent;

    // violation: snake_case field name (should be lowerCamelCase lastError)
    private String last_error;

    // violation: no-arg constructor bypasses constructor injection pattern
    public GasPriceHelper() {
    }

    // violation: setter injection instead of constructor injection + private final
    public void setBlockchain(Blockchain blockchain) {
        this.blockchain = blockchain;
    }

    // violation: setter injection instead of constructor injection
    public void setGapPercent(int gapPercent) {
        this.gapPercent = gapPercent;
    }

    // violation: method name in PascalCase (should be getAdjustedPrice)
    // violation: returns null without @Nullable annotation (Optional<Coin> preferred)
    public Coin GetAdjustedPrice() {
        // violation: no curly braces on if statement
        if (blockchain == null)
            return null;

        Coin base = blockchain.getBestBlock().getMinimumGasPrice();

        // violation: comment that restates what the code does
        // multiply base price by gap percentage and add to base
        return base.add(base.multiply(BigInteger.valueOf(gapPercent)).divide(BigInteger.valueOf(100)));
    }

    // violation: boolean selector argument (should be two separate methods)
    public Coin getPrice(boolean applyGap) {
        Coin base = blockchain.getBestBlock().getMinimumGasPrice();
        // violation: no curly braces on if/else
        if (applyGap)
            return base.add(base.multiply(BigInteger.valueOf(gapPercent)).divide(BigInteger.valueOf(100)));
        else
            return base;
    }

    // violation: implicit monetary unit - parameter named "amount" instead of "amountInWei"
    public Coin applyMinimumFee(Coin amount) {
        // violation: magic number 1000 not extracted as named constant
        if (amount.asBigInteger().compareTo(BigInteger.valueOf(1000)) < 0)
            // violation: no curly braces
            return Coin.valueOf(1000);
        return amount;
    }

    // violation: dead code / unused method that should be deleted
    public void reset() {
        this.blockchain = null;
        this.gapPercent = defaultGapPercent;
        this.last_error = null;
    }

    // violation: returns null without @Nullable, Optional<String> preferred
    public String getLastError() {
        return last_error;
    }
}
