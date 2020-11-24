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
import co.rsk.config.ConfigLoader;
import co.rsk.config.RskSystemProperties;
import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.log.FederateLogger;
import co.rsk.federate.log.RskLogMonitor;
import co.rsk.federate.rpc.Web3FederateImpl;
import co.rsk.federate.solidity.DummySolidityCompiler;
import org.ethereum.rpc.Web3;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Creates the federate node initial object graph.
 */
public class FedNodeContext extends RskContext {

    private FedNodeSystemProperties fedNodeSystemProperties;
    private BtcToRskClient.Factory btcToRskClientFactory;
    private BtcToRskClient btcToRskClientActive;
    private BtcToRskClient btcToRskClientRetiring;
    private FederatorSupport federatorSupport;
    private FederationWatcher federationWatcher;
    private FederateLogger federateLogger;

    public FedNodeContext(String[] args) {
        super(args);
    }

    @Override
    public NodeRunner buildNodeRunner() {
        return new FedNodeRunner(
                getBtcToRskClientActive(),
                getBtcToRskClientRetiring(),
                new BtcReleaseClient(
                        getRsk(),
                        getFederatorSupport(),
                        getFedNodeSystemProperties(),
                        getNodeBlockProcessor()
                ),
                getFederationWatcher(),
                getFederatorSupport(),
                getFederateLogger(),
                new RskLogMonitor(getRsk(), getFederateLogger()),
                super.buildNodeRunner(),
                getFedNodeSystemProperties(),
                this
        );
    }

    @Override
    public RskSystemProperties getRskSystemProperties() {
        return getFedNodeSystemProperties();
    }

    @Override
    public Web3 buildWeb3() {
        return new Web3FederateImpl(
                getRsk(),
                getBlockchain(),
                getRskSystemProperties(),
                getMinerClient(),
                getMinerServer(),
                getPersonalModule(),
                getEthModule(),
                getEvmModule(),
                getTxPoolModule(),
                getMnrModule(),
                getDebugModule(),
                getTraceModule(),
                getRskModule(),
                getBtcToRskClientActive(),
                getBtcToRskClientRetiring(),
                getChannelManager(),
                getPeerScoringManager(),
                getNetworkStateExporter(),
                getBlockStore(),
                getReceiptStore(),
                getPeerServer(),
                getNodeBlockProcessor(),
                getHashRateCalculator(),
                getConfigCapabilities(),
                getBuildInfo(),
                getBlocksBloomStore(),
                getWeb3InformationRetriever());
    }

    @Override
    public SolidityCompiler buildSolidityCompiler() {
        return new DummySolidityCompiler(null);
    }

    private BtcToRskClient getBtcToRskClientActive() {
        if (btcToRskClientActive == null) {
            btcToRskClientActive = getBtcToRskClientFactory().build();
        }

        return btcToRskClientActive;
    }

    private BtcToRskClient getBtcToRskClientRetiring() {
        if (btcToRskClientRetiring == null) {
            btcToRskClientRetiring = getBtcToRskClientFactory().build();
        }

        return btcToRskClientRetiring;
    }

    private BtcToRskClient.Factory getBtcToRskClientFactory() {
        if (btcToRskClientFactory == null) {
            btcToRskClientFactory = new BtcToRskClient.Factory(
                    getFederatorSupport(),
                    getNodeBlockProcessor(),
                    getBlockchain()
            );
        }

        return btcToRskClientFactory;
    }

    private FederationWatcher getFederationWatcher() {
        if (federationWatcher == null) {
            federationWatcher = new FederationWatcher(getRsk());
        }

        return federationWatcher;
    }

    private FederateLogger getFederateLogger() {
        if (federateLogger == null) {
            federateLogger = new FederateLogger(
                    getFederatorSupport(),
                    System::currentTimeMillis,
                    () -> LoggerFactory.getLogger(FederateLogger.class),
                    TimeUnit.MINUTES.toMillis(1),
                    6
            );
        }

        return federateLogger;
    }

    private FederatorSupport getFederatorSupport() {
        if (federatorSupport == null) {
            BridgeTransactionSender bridgeTransactionSender = new BridgeTransactionSender(
                    getRsk(),
                    getBlockchain(),
                    getTransactionPool(),
                    getReversibleTransactionExecutor(),
                    getFedNodeSystemProperties());
            federatorSupport = new FederatorSupport(
                    getBlockchain(),
                    getFedNodeSystemProperties(),
                    bridgeTransactionSender
            );
        }

        return federatorSupport;
    }

    private FedNodeSystemProperties getFedNodeSystemProperties() {
        if (fedNodeSystemProperties == null) {
            fedNodeSystemProperties = new FedNodeSystemProperties(new ConfigLoader(getCliArgs()));
        }

        return fedNodeSystemProperties;
    }
}
