/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.federate.gas;

import co.rsk.core.Coin;
import org.ethereum.core.Blockchain;

import java.math.BigInteger;

/**
 * Gas provider that adds a gap percentage to the min gas price calculation.
 *
 * @author Jose Dahlquist
 */
public class BestBlockMinGasPriceWithGapProvider extends BestBlockMinGasPriceProvider {

    public final BigInteger gap;

    public BestBlockMinGasPriceWithGapProvider(Blockchain blockchain, long gapToUse){
        super(blockchain);
        this.gap = BigInteger.valueOf(gapToUse);
    }

    @Override
    public Coin get(){
        Coin minGasPrice = super.get();
        if (this.gap.longValue() == 0) {
            return minGasPrice;
        }
        return minGasPrice.add(minGasPrice.multiply(this.gap).divide(BigInteger.valueOf(100)));
    }
}
