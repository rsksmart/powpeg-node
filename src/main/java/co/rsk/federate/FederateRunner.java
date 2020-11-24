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
package co.rsk.federate;

import co.rsk.NodeRunner;
import co.rsk.RskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The entrypoint for the RSK federate node
 */
public class FederateRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(FederateRunner.class);

    public static void main(String[] args) {
        RskContext ctx = new FedNodeContext(args);

        NodeRunner runner = ctx.getNodeRunner();
        try {
            runner.run();
            Runtime.getRuntime().addShutdownHook(new Thread(runner::stop));
        } catch (Exception e) {
            runner.stop();
            LOGGER.error("The RSK federate node main thread failed, closing program", e);
            System.exit(1);

        }
    }
}
