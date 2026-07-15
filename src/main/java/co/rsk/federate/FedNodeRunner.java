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

import static co.rsk.federate.signing.PowPegNodeKeyId.BTC;
import static co.rsk.federate.signing.PowPegNodeKeyId.MST;
import static co.rsk.federate.signing.PowPegNodeKeyId.RSK;

import co.rsk.NodeRunner;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.core.RskAddress;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.bitcoin.BitcoinWrapper;
import co.rsk.federate.bitcoin.BitcoinWrapperImpl;
import co.rsk.federate.bitcoin.Kit;
import co.rsk.federate.btcreleaseclient.BtcReleaseClient;
import co.rsk.federate.config.PowpegNodeSystemProperties;
import co.rsk.federate.io.BtcToRskClientDirectoryStorageInfo;
import co.rsk.federate.io.BtcToRskClientFileStorage;
import co.rsk.federate.io.BtcToRskClientFileStorageFactory;
import co.rsk.federate.io.DirectoryStorageInfo;
import co.rsk.federate.log.BtcLogMonitor;
import co.rsk.federate.log.FederateLogger;
import co.rsk.federate.log.RskLogMonitor;
import co.rsk.federate.signing.ECDSACompositeSigner;
import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.ECDSASignerFactory;
import co.rsk.federate.signing.KeyId;
import co.rsk.federate.signing.config.SignerConfig;
import co.rsk.federate.signing.config.SignerType;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.advanceblockchain.ConfirmedBlocksProvider;
import co.rsk.federate.signing.hsm.advanceblockchain.HSMBookKeepingClientProvider;
import co.rsk.federate.signing.hsm.advanceblockchain.HSMBookkeepingService;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.hsm.client.HSMClientProtocolFactory;
import co.rsk.federate.signing.hsm.config.PowHSMConfig;
import co.rsk.federate.signing.hsm.message.ReleaseCreationInformationGetter;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderFactory;
import co.rsk.federate.signing.hsm.requirements.AncestorBlockUpdater;
import co.rsk.federate.signing.hsm.requirements.ReleaseRequirementsEnforcer;
import co.rsk.federate.watcher.FederationWatcher;
import co.rsk.federate.watcher.FederationWatcherListener;
import co.rsk.federate.watcher.FederationWatcherListenerImpl;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import java.io.File;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.PreDestroy;
import org.bitcoinj.core.Context;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FedNodeRunner implements NodeRunner {
    private static final Logger logger = LoggerFactory.getLogger(FedNodeRunner.class);
    private final BtcToRskClient btcToRskClientActive;
    private final BtcToRskClient btcToRskClientRetiring;
    private final BtcReleaseClient btcReleaseClient;
    private final FederatorSupport federatorSupport;
    private final FederationWatcher federationWatcher;
    private final FederateLogger federateLogger;
    private final RskLogMonitor rskLogMonitor;
    private final NodeRunner fullNodeRunner;
    private final PowpegNodeSystemProperties config;
    private final FedNodeContext fedNodeContext;
    private final BridgeConstants bridgeConstants;
    private final HSMClientProtocolFactory hsmClientProtocolFactory;
    private final HSMBookKeepingClientProvider hsmBookKeepingClientProvider;

    private DirectoryStorageInfo directoryStorageInfo;
    private BitcoinWrapper bitcoinWrapper;
    private FederationMember member;
    private ECDSASigner signer;
    private HSMBookkeepingClient hsmBookkeepingClient;
    private HSMBookkeepingService hsmBookkeepingService;

    public FedNodeRunner(
        BtcToRskClient btcToRskClientActive,
        BtcToRskClient btcToRskClientRetiring,
        BtcReleaseClient btcReleaseClient,
        FederationWatcher federationWatcher,
        FederatorSupport federatorSupport,
        FederateLogger federateLogger,
        RskLogMonitor rskLogMonitor,
        NodeRunner fullNodeRunner,
        PowpegNodeSystemProperties config,
        HSMClientProtocolFactory hsmClientProtocolFactory,
        HSMBookKeepingClientProvider hsmBookKeepingClientProvider,
        FedNodeContext fedNodeContext
    ) {
        this.btcToRskClientActive = btcToRskClientActive;
        this.btcToRskClientRetiring = btcToRskClientRetiring;
        this.btcReleaseClient = btcReleaseClient;
        this.federationWatcher = federationWatcher;
        this.federatorSupport = federatorSupport;
        this.federateLogger = federateLogger;
        this.rskLogMonitor = rskLogMonitor;
        this.fullNodeRunner = fullNodeRunner;
        this.config = config;
        this.bridgeConstants = config.getNetworkConstants().getBridgeConstants();
        this.hsmClientProtocolFactory = hsmClientProtocolFactory;
        this.hsmBookKeepingClientProvider = hsmBookKeepingClientProvider;
        this.fedNodeContext = fedNodeContext;
    }

    @Override
    public void run() throws Exception {
        logger.debug("[run] Starting RSK PowPeg node");
        signer = buildSigner();

        SignerConfig btcSignerConfig = config.signerConfig(BTC.getId());
        if (btcSignerConfig != null && btcSignerConfig.getSignerType() == SignerType.HSM) {
            startBookkeepingServices();
        }

        if (!this.checkFederateRequirements()) {
            logger.error("[run] Could not validate fed node requirements");
            return;
        }

        configureFederatorSupport();
        fullNodeRunner.run();
        startFederate();

        signer.addListener(l -> {
            logger.error("[run] Signer informed unrecoverable state, shutting down", l);
            this.shutdown();
        });

        RskAddress pegnatoryRskAddress = new RskAddress(this.member.getRskPublicKey().getAddress());
        logger.info("[run] Federated node started");
        logger.info("[run] RSK address: {}", pegnatoryRskAddress);
    }

    private void startBookkeepingServices() throws SignerException, HSMClientException {
        logger.debug("[startBookkeepingServices] Starting bookkeeping services");
        SignerConfig btcSignerConfig = config.signerConfig(BTC.getId());
        PowHSMConfig powHsmConfig = new PowHSMConfig(btcSignerConfig);
        HSMClientProtocol protocol = hsmClientProtocolFactory.buildHSMClientProtocolFromConfig(powHsmConfig);

        HSMVersion hsmVersion = protocol.getVersion();
        logger.debug("[startBookkeepingServices] Using HSM version {}", hsmVersion);

        // Only start bookkeeping services for PoW HSMs. HSM version 1 doesn't support bookkeeping.
        if (!hsmVersion.isPowHSM()) {
            logger.debug("[startBookkeepingServices] HSM version {} does not support bookkeeping. Skipping bookkeeping services.", hsmVersion);
            return;
        }

        hsmBookkeepingClient = buildBookKeepingClient(protocol, powHsmConfig);
        hsmBookkeepingService = buildBookKeepingService(hsmBookkeepingClient, powHsmConfig);
        logger.debug("[startBookkeepingServices] Bookkeeping services initialized");
    }

    private void configureFederatorSupport() throws SignerException {
        BtcECKey btcPublicKey = signer.getPublicKey(BTC.getKeyId()).toBtcKey();
        ECKey rskPublicKey = signer.getPublicKey(RSK.getKeyId()).toEthKey();
        ECKey mstKey = signer.getPublicKey(MST.getKeyId()).toEthKey();

        String rskPublicKeyHex = ByteUtil.toHexString(rskPublicKey.getPubKey(true));
        String mstKeyHex = ByteUtil.toHexString(mstKey.getPubKey(true));
        logger.info(
            "[configureFederatorSupport] BTC public key: {}. RSK public key: {}. MST public key: {}",
            btcPublicKey.getPublicKeyAsHex(),
            rskPublicKeyHex,
            mstKeyHex
        );

        this.member = new FederationMember(btcPublicKey, rskPublicKey, mstKey);
        federatorSupport.setMember(this.member);
        federatorSupport.setSigner(signer);
        logger.debug("[configureFederatorSupport] Federator support configured with member and signer");
    }

    /**
     * Gather signers needed for signing BTC, RSK, MST
     * transactions from the configuration.
     * Then build a composite signer with those.
     */
    private ECDSASigner buildSigner() {
        logger.debug("[buildSigner] Building signers for BTC, RSK and MST");
        ECDSACompositeSigner compositeSigner = new ECDSACompositeSigner();

        Stream.of(BTC, RSK, MST).forEach(keyId -> {
            try {
                ECDSASigner createdSigner = buildSignerFromKey(keyId.getKeyId());
                compositeSigner.addSigner(createdSigner);
                logger.debug("[buildSigner] Signer for key {} created", keyId);
            } catch (SignerException e) {
                logger.error("[buildSigner] Error trying to build signer with key id {}. Detail: {}", keyId, e.getMessage());
            } catch (Exception e) {
                logger.error("[buildSigner] Error creating signer {}. Detail: {}", keyId, e.getMessage());
                throw e;
            }
        });
        logger.debug("[buildSigner] Signers created");

        return compositeSigner;
    }

    private HSMBookkeepingClient buildBookKeepingClient(
        HSMClientProtocol protocol,
        PowHSMConfig powHsmConfig
    ) throws HSMClientException {
        HSMBookkeepingClient bookKeepingClient = hsmBookKeepingClientProvider.getHSMBookKeepingClient(protocol);
        bookKeepingClient.setMaxChunkSizeToHsm(powHsmConfig.getMaxChunkSizeToHsm());
        logger.info("[buildBookKeepingClient] HSMBookkeeping client built for HSM version: {}", bookKeepingClient.getVersion());

        return bookKeepingClient;
    }

    private HSMBookkeepingService buildBookKeepingService(
        HSMBookkeepingClient bookKeepingClient,
        PowHSMConfig powHsmConfig
    ) throws HSMClientException {
        logger.debug("[buildBookKeepingService] Building HSMBookkeeping service for HSM version: {}", bookKeepingClient.getVersion());
        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            powHsmConfig.getDifficultyTarget(bookKeepingClient),
            powHsmConfig.getMaxAmountBlockHeaders(),
            fedNodeContext.getBlockStore(),
            powHsmConfig.getDifficultyCap(bridgeConstants.getBtcParamsString()),
            hsmBookkeepingClient.getVersion()
        );

        HSMBookkeepingService service = new HSMBookkeepingService(
            fedNodeContext.getBlockStore(),
            bookKeepingClient,
            confirmedBlocksProvider,
            fedNodeContext.getNodeBlockProcessor(),
            powHsmConfig.getInformerInterval(),
            powHsmConfig.isStopBookkeepingScheduler()
        );
        logger.info("[buildBookKeepingService] HSMBookkeeping Service built for HSM version: {}", bookKeepingClient.getVersion());

        return service;
    }

    /**
     * Build a signer from a certain configuration key
     * Fallback to using the (old) "keyFile" configuration
     * option if specific key configuration is not found.
     */
    private ECDSASigner buildSignerFromKey(KeyId key) throws SignerException {
        SignerConfig signerConfig = config.signerConfig(key.getId());
        return new ECDSASignerFactory().buildFromConfig(signerConfig);
    }

    private boolean checkFederateRequirements() {
        if (!config.isFederatorEnabled()) {
            logger.warn("[checkFederateRequirements] Federator is disabled, powpeg node will not start.");
            return false;
        }
        int defaultPort = bridgeConstants.getBtcParams().getPort();
        List<String> peers = config.bitcoinPeerAddresses();

        Federator federator = new Federator(
            signer,
            Arrays.asList(BTC.getKeyId(), RSK.getKeyId()),
            new FederatorPeersChecker(
                defaultPort,
                peers,
                ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString())
            )
        );

        boolean isValidFederator = federator.validFederator();
        logger.debug("[checkFederateRequirements] Federate requirements validated: {}", isValidFederator);

        return isValidFederator;
    }

    private void startFederate() throws Exception {
        if (!config.isFederatorEnabled()) {
            logger.warn("[startFederate] Federator is disabled. Skipping federation behaviour.");
            return;
        }

        logger.debug("[startFederate] Starting Federation Behaviour");
        // Set up a federation watcher to trigger starts and stops of the
        // btc to rsk client upon federation changes
        FederationProvider federationProvider = new FederationProviderFromFederatorSupport(
            federatorSupport,
            bridgeConstants.getFederationConstants()
        );

        directoryStorageInfo = new BtcToRskClientDirectoryStorageInfo(config);
        bitcoinWrapper = createAndSetupBitcoinWrapper();

        BtcToRskClientFileStorageFactory fileStorageFactory = new BtcToRskClientFileStorageFactory(directoryStorageInfo);
        BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        BtcToRskClientFileStorage btcToRskActiveClientFileStorage = fileStorageFactory.forActive();
        btcToRskClientActive.setup(
            bitcoinWrapper,
            bridgeConstants,
            btcToRskActiveClientFileStorage,
            btcLockSenderProvider,
            peginInstructionsProvider,
            config
        );
        BtcToRskClientFileStorage btcToRskRetiringClientFileStorage = fileStorageFactory.forRetiring();
        btcToRskClientRetiring.setup(
            bitcoinWrapper,
            bridgeConstants,
            btcToRskRetiringClientFileStorage,
            btcLockSenderProvider,
            peginInstructionsProvider,
            config
        );
        BtcLogMonitor btcLogMonitor = new BtcLogMonitor(bitcoinWrapper, federateLogger);
        btcLogMonitor.start();
        rskLogMonitor.start();
        if (hsmBookkeepingService != null) {
            hsmBookkeepingService.addListener(e -> {
                logger.error("[startFederate] HSM bookkeeping service informed unrecoverable state, shutting down", e);
                this.shutdown();
            });
            hsmBookkeepingService.start();
        }
        federateLogger.log();
        btcReleaseClient.setup(
            signer,
            new SignerMessageBuilderFactory(
                fedNodeContext.getReceiptStore()
            ),
            new ReleaseCreationInformationGetter(
                fedNodeContext.getReceiptStore(),
                fedNodeContext.getBlockStore()
            ),
            new ReleaseRequirementsEnforcer(
                new AncestorBlockUpdater(
                    fedNodeContext.getBlockStore(),
                    hsmBookkeepingClient
                )
            )
        );

        FederationWatcherListener federationWatcherListener = new FederationWatcherListenerImpl(
            btcToRskClientActive,
            btcToRskClientRetiring,
            btcReleaseClient
        );

        federationWatcher.start(federationProvider, federationWatcherListener);
    }

    @PreDestroy
    public void tearDown() {
        logger.debug("[tearDown] FederateRunner tearDown starting...");
        this.stop();
        logger.debug("[tearDown] FederateRunner tearDown finished.");
    }

    private void shutdown() {
        try {
            this.tearDown();
        } catch(Exception e){
            logger.error("[shutdown] FederateRunner teardown failed", e);
        }
        System.exit(-1);
    }

    @Override
    public void stop() {
        logger.info("[stop] Shutting down Federation node");
        if (bitcoinWrapper != null) {
            bitcoinWrapper.stop();
        }
        if (btcToRskClientActive != null) {
            btcToRskClientActive.stop();
        }
        if (btcToRskClientRetiring != null) {
            btcToRskClientRetiring.stop();
        }

        if (hsmBookkeepingService != null) {
            hsmBookkeepingService.stop();
        }

        fullNodeRunner.stop();
        logger.info("[stop] Federation node Shut down.");
    }

    private BitcoinWrapper createAndSetupBitcoinWrapper() throws UnknownHostException {
        final String BTC_TO_RSK_CLIENT_FILE_PREFIX = "BtcToRskClient";
        Context btcContext = new Context(ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString()));
        File pegDirectory = new File(directoryStorageInfo.getPath());
        Kit kit = new Kit(btcContext, pegDirectory, BTC_TO_RSK_CLIENT_FILE_PREFIX);

        BitcoinWrapper wrapper = new BitcoinWrapperImpl(
            btcContext,
            kit
        );
        wrapper.setup(federatorSupport.getBitcoinPeerAddresses());
        wrapper.start(config.getBitcoinWrapperStartupCheckInterval());

        return wrapper;
    }
}
