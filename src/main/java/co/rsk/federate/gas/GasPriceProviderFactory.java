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

import co.rsk.config.RskConfigurationException;
import co.rsk.federate.config.GasPriceProviderConfig;
import org.ethereum.core.Blockchain;

public class GasPriceProviderFactory {
    public static final long DEFAULT_GAP = 10;
    public static IGasPriceProvider get(GasPriceProviderConfig config, Blockchain blockchain) throws RskConfigurationException {
        if (config == null) {
            return getGasPriceProviderToUseByDefault(blockchain);
        }
        switch (config.getType()) {
            case "bestBlockWithGap":
                if (!config.getConfig().hasPath("gap")) {
                    throw new RskConfigurationException("You must provide a valid \"gap\" value in order to create a GasPriceWithGapProvider.");
                }
                return new BestBlockMinGasPriceWithGapProvider(blockchain, config.getConfig().getLong("gap"));
            case "bestBlock":
                return new BestBlockMinGasPriceProvider(blockchain);
            default:
                return getGasPriceProviderToUseByDefault(blockchain);
        }
    }

    private static IGasPriceProvider getGasPriceProviderToUseByDefault(Blockchain blockchain) {
        return new BestBlockMinGasPriceWithGapProvider(blockchain, DEFAULT_GAP);
    }
}
