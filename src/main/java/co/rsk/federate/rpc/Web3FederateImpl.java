package co.rsk.federate.rpc;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.NetworkStateExporter;
import co.rsk.federate.BtcToRskClient;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.BlockProcessor;
import co.rsk.net.SyncProcessor;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.Web3RskImpl;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.evm.EvmModule;
import co.rsk.rpc.modules.mnr.MnrModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.rsk.RskModule;
import co.rsk.rpc.modules.trace.TraceModule;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.scoring.PeerScoringManager;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.util.BuildInfo;

public class Web3FederateImpl extends Web3RskImpl {

    private final BtcToRskClient btcToRskClientActive;
    private final BtcToRskClient btcToRskClientRetiring;

    public Web3FederateImpl(
            Ethereum eth,
            Blockchain blockchain,
            RskSystemProperties properties,
            MinerClient minerClient,
            MinerServer minerServer,
            PersonalModule personalModule,
            EthModule ethModule,
            EvmModule evmModule,
            TxPoolModule txPoolModule,
            MnrModule mnrModule,
            DebugModule debugModule,
            TraceModule traceModule,
            RskModule rskModule,
            BtcToRskClient btcToRskClientActive,
            BtcToRskClient btcToRskClientRetiring,
            ChannelManager channelManager,
            PeerScoringManager peerScoringManager,
            NetworkStateExporter networkStateExporter,
            BlockStore blockStore,
            ReceiptStore receiptStore,
            PeerServer peerServer,
            BlockProcessor nodeBlockProcessor,
            HashRateCalculator hashRateCalculator,
            ConfigCapabilities configCapabilities,
            BuildInfo buildInfo,
            BlocksBloomStore blocksBloomStore,
            Web3InformationRetriever web3InformationRetriever,
            SyncProcessor syncProcessor) {
        super(
            eth,
            blockchain,
            properties,
            minerClient,
            minerServer,
            personalModule,
            ethModule,
            evmModule,
            txPoolModule,
            mnrModule,
            debugModule,
            traceModule,
            rskModule,
            channelManager,
            peerScoringManager,
            networkStateExporter,
            blockStore,
            receiptStore,
            peerServer,
            nodeBlockProcessor,
            hashRateCalculator,
            configCapabilities,
            buildInfo,
            blocksBloomStore,
            web3InformationRetriever,
            syncProcessor
        );
        this.btcToRskClientActive = btcToRskClientActive;
        this.btcToRskClientRetiring = btcToRskClientRetiring;
    }

    public void fed_updateBridge() {
        btcToRskClientActive.updateBridge();
        btcToRskClientRetiring.updateBridge();
    }
}
