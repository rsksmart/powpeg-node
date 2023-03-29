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
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.config.BridgeConstants;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.bitcoin.BitcoinWrapper;
import co.rsk.federate.bitcoin.BitcoinWrapperImpl;
import co.rsk.federate.bitcoin.Kit;
import co.rsk.federate.btcreleaseclient.BtcReleaseClient;
import co.rsk.federate.btcreleaseclient.BtcReleaseClientStorageAccessor;
import co.rsk.federate.btcreleaseclient.BtcReleaseClientStorageSynchronizer;
import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.config.PowHSMBookkeepingConfig;
import co.rsk.federate.config.SignerConfig;
import co.rsk.federate.io.*;
import co.rsk.federate.log.BtcLogMonitor;
import co.rsk.federate.log.FederateLogger;
import co.rsk.federate.log.RskLogMonitor;
import co.rsk.federate.signing.*;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.advanceblockchain.ConfirmedBlocksProvider;
import co.rsk.federate.signing.hsm.advanceblockchain.HSMBookKeepingClientProvider;
import co.rsk.federate.signing.hsm.advanceblockchain.HSMBookkeepingService;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.hsm.client.HSMClientProtocolFactory;
import co.rsk.federate.signing.hsm.message.ReleaseCreationInformationGetter;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderFactory;
import co.rsk.federate.signing.hsm.requirements.AncestorBlockUpdater;
import co.rsk.federate.signing.hsm.requirements.PegoutSigningRequirementsEnforcer;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import org.bitcoinj.core.Context;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.io.File;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static co.rsk.federate.signing.PowPegNodeKeyId.*;

/**
 * Created by mario on 31/03/17.
 */

public class FedNodeRunner implements NodeRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(FedNodeRunner.class);
    private final BtcToRskClient btcToRskClientActive;
    private final BtcToRskClient btcToRskClientRetiring;
    private final BtcReleaseClient btcReleaseClient;
    private final FederatorSupport federatorSupport;
    private final FederationWatcher federationWatcher;
    private final FederateLogger federateLogger;
    private final RskLogMonitor rskLogMonitor;
    private final NodeRunner fullNodeRunner;
    private final FedNodeSystemProperties config;
    private final FedNodeContext fedNodeContext;
    private final BridgeConstants bridgeConstants;
    private final HSMClientProtocolFactory hsmClientProtocolFactory;
    private final HSMBookKeepingClientProvider hsmBookKeepingClientProvider;

    private BitcoinWrapper bitcoinWrapper;
    private BtcToRskClientFileStorage btcToRskClientFileStorage;
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
        FedNodeSystemProperties config,
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
        LOGGER.debug("[run] Starting RSK");
        signer = buildSigner();
        SignerConfig signerConfig = this.config.signerConfig(BTC_KEY_ID.getId());
        if (signerConfig != null && "hsm".equals(signerConfig.getType())) {
            PowHSMBookkeepingConfig bookKeepingConfig = new PowHSMBookkeepingConfig(
                signerConfig,
                bridgeConstants.getBtcParamsString()
            );

            HSMClientProtocol protocol = hsmClientProtocolFactory.buildHSMClientProtocolFromConfig(signerConfig);
            int hsmVersion = protocol.getVersion();
            LOGGER.debug("[run] Using HSM version {}", hsmVersion);

            if (hsmVersion >= 2) {
                hsmBookkeepingClient = buildBookKeepingClient(protocol, bookKeepingConfig);
                hsmBookkeepingService = buildBookKeepingService(
                    hsmBookkeepingClient,
                    bookKeepingConfig
                );
            }
        }
        if(!this.checkFederateRequirements()) {
            LOGGER.error("[run] Error validating Fed-Node Requirements");
            return;
        }
        LOGGER.info("[run] Signers: {}", signer.getVersionString());
        configureFederatorSupport();
        fullNodeRunner.run();
        startFederate();

        signer.addListener((l -> {
            LOGGER.error("[run] Signer informed unrecoverable state, shutting down", l);
            this.shutdown();
        }));

        LOGGER.info("[run] Federated node started");
        LOGGER.info("[run] RSK address: {}", Hex.toHexString(this.member.getRskPublicKey().getAddress()));
    }

    private void configureFederatorSupport() throws SignerException {
        BtcECKey btcPublicKey = signer.getPublicKey(BTC_KEY_ID.getKeyId()).toBtcKey();
        ECKey rskPublicKey = signer.getPublicKey(RSK_KEY_ID.getKeyId()).toEthKey();
        ECKey mstKey = signer.getPublicKey(MST_KEY_ID.getKeyId()).toEthKey();
        LOGGER.info(
            "[configureFederatorSupport] BTC public key: {}. RSK public key: {}. MST public key: {}",
            btcPublicKey,
            rskPublicKey,
            mstKey
        );

        this.member = new FederationMember(btcPublicKey, rskPublicKey, mstKey);
        federatorSupport.setMember(this.member);
        federatorSupport.setSigner(signer);
    }

    /**
     * Gather signers needed for signing BTC, RSK, MST
     * transactions from the configuration.
     * Then build a composite signer with those.
     */
    private ECDSASigner buildSigner() {
        ECDSACompositeSigner compositeSigner = new ECDSACompositeSigner();

        Stream.of(BTC_KEY_ID, RSK_KEY_ID, MST_KEY_ID).forEach(keyId -> {
            try {
                ECDSASigner createdSigner = buildSignerFromKey(keyId.getKeyId());
                compositeSigner.addSigner(createdSigner);
            } catch (SignerException e) {
                LOGGER.error("[buildSigner] Error trying to build signer with key id {}. Detail: {}", keyId, e.getMessage());
            } catch (Exception e) {
                LOGGER.error("[buildSigner] Error creating signer {}. Detail: {}", keyId, e.getMessage());
                throw e;
            }
        });

        LOGGER.debug("[buildSigner] Signers created");

        return compositeSigner;
    }

    private HSMBookkeepingClient buildBookKeepingClient(
        HSMClientProtocol protocol,
        PowHSMBookkeepingConfig bookKeepingConfig) throws HSMClientException {

        HSMBookkeepingClient bookKeepingClient = hsmBookKeepingClientProvider.getHSMBookKeepingClient(protocol);
        bookKeepingClient.setMaxChunkSizeToHsm(bookKeepingConfig.getMaxChunkSizeToHsm());
        LOGGER.info("[buildBookKeepingClient] HSMBookkeeping client built for HSM version: {}", bookKeepingClient.getVersion());
        return bookKeepingClient;
    }

    private HSMBookkeepingService buildBookKeepingService(
        HSMBookkeepingClient bookKeepingClient,
        PowHSMBookkeepingConfig bookKeepingConfig) throws HSMClientException {

        HSMBookkeepingService service = new HSMBookkeepingService(
            fedNodeContext.getBlockStore(),
            bookKeepingClient,
            new ConfirmedBlocksProvider(
                bookKeepingConfig.getDifficultyTarget(),
                bookKeepingConfig.getMaxAmountBlockHeaders(),
                fedNodeContext.getBlockStore(),
                bookKeepingConfig.getDifficultyCap(),
                hsmBookkeepingClient.getVersion()
            ),
            fedNodeContext.getNodeBlockProcessor(),
            bookKeepingConfig.getInformerInterval(),
            bookKeepingConfig.isStopBookkeepingScheduler()
        );
        LOGGER.info("[buildBookKeepingService] HSMBookkeeping Service built for HSM version: {}", bookKeepingClient.getVersion());
        return service;
    }

    /**
     * Build a signer from a certain configuration key
     *
     * Fallback to using the (old) "keyFile" configuration
     * option if specific key configuration is not found.
     */
    private ECDSASigner buildSignerFromKey(KeyId key) throws SignerException {
        SignerConfig signerConfig = config.signerConfig(key.getId());
        return new ECDSASignerFactory().buildFromConfig(signerConfig);
    }

    private boolean checkFederateRequirements() {
        if (config.isFederatorEnabled()) {
            int defaultPort = bridgeConstants.getBtcParams().getPort();
            List<String> peers = config.bitcoinPeerAddresses();

            Federator federator = new Federator(
                signer,
                Arrays.asList(BTC_KEY_ID.getKeyId(), RSK_KEY_ID.getKeyId()),
                new FederatorPeersChecker(
                    defaultPort,
                    peers,
                    ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString())
                )
            );
            return federator.validFederator();
        }
        return false;
    }

    private void startFederate() throws Exception {
        LOGGER.debug("[startFederate] Starting Federation Behaviour");
        if (config.isFederatorEnabled()) {
            // Setup a federation watcher to trigger starts and stops of the
            // btc to rsk client upon federation changes
            FederationProvider federationProvider = new FederationProviderFromFederatorSupport(
                federatorSupport,
                bridgeConstants
            );

            BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
            PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
            btcToRskClientFileStorage = new BtcToRskClientFileStorageImpl(new BtcToRskClientFileStorageInfo(config));
            bitcoinWrapper = createAndSetupBitcoinWrapper(btcLockSenderProvider, peginInstructionsProvider);

            btcToRskClientActive.setup(
                config.getActivationConfig(),
                bitcoinWrapper,
                bridgeConstants,
                btcToRskClientFileStorage,
                btcLockSenderProvider,
                peginInstructionsProvider,
                config.isUpdateBridgeTimerEnabled(),
                config.getAmountOfHeadersToSend()
            );
            btcToRskClientRetiring.setup(
                config.getActivationConfig(),
                bitcoinWrapper,
                bridgeConstants,
                btcToRskClientFileStorage,
                btcLockSenderProvider,
                peginInstructionsProvider,
                config.isUpdateBridgeTimerEnabled(),
                config.getAmountOfHeadersToSend()
            );
            BtcLogMonitor btcLogMonitor = new BtcLogMonitor(bitcoinWrapper, federateLogger);
            btcLogMonitor.start();
            rskLogMonitor.start();
            if (hsmBookkeepingService != null) {
                hsmBookkeepingService.addListener((e) -> {
                    LOGGER.error("[startFederate] HSM bookkeeping service informed unrecoverable state, shutting down", e);
                    this.shutdown();
                });
                hsmBookkeepingService.start();
            }
            federateLogger.log();
            BtcReleaseClientStorageAccessor btcReleaseClientStorageAccessor = new BtcReleaseClientStorageAccessor(config);
            btcReleaseClient.setup(
                signer,
                config.getActivationConfig(),
                new SignerMessageBuilderFactory(
                    fedNodeContext.getReceiptStore()
                ),
                new ReleaseCreationInformationGetter(
                    fedNodeContext.getReceiptStore(),
                    fedNodeContext.getBlockStore()
                ),
                new PegoutSigningRequirementsEnforcer(
                    new AncestorBlockUpdater(
                        fedNodeContext.getBlockStore(),
                        hsmBookkeepingClient
                    )
                ),
                btcReleaseClientStorageAccessor,
                new BtcReleaseClientStorageSynchronizer(
                    fedNodeContext.getBlockStore(),
                    fedNodeContext.getReceiptStore(),
                    fedNodeContext.getNodeBlockProcessor(),
                    btcReleaseClientStorageAccessor,
                    config.getBtcReleaseClientInitializationMaxDepth()
                )
            );
            federationWatcher.setup(federationProvider);

            federationWatcher.addListener(new FederationWatcher.Listener() {
                @Override
                public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                    String oldFederationAddress = oldFederation.map(f -> f.getAddress().toString()).orElse("NONE");
                    String newFederationAddress = newFederation.getAddress().toString();
                    LOGGER.debug(String.format("[onActiveFederationChange] Active federation change: from %s to %s", oldFederationAddress, newFederationAddress));
                    triggerClientChange(btcToRskClientActive, Optional.of(newFederation));
                }

                @Override
                public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                    String oldFederationAddress = oldFederation.map(f -> f.getAddress().toString()).orElse("NONE");
                    String newFederationAddress = newFederation.map(f -> f.getAddress().toString()).orElse("NONE");
                    LOGGER.debug(String.format("[onRetiringFederationChange] Retiring federation change: from %s to %s", oldFederationAddress, newFederationAddress));
                    triggerClientChange(btcToRskClientRetiring, newFederation);
                }
            });
            // Trigger the first events
            federationWatcher.updateState();
        }
    }

    @PreDestroy
    public void tearDown() {
        LOGGER.debug("[tearDown] FederateRunner tearDown starting...");

        this.stop();

        LOGGER.debug("[tearDown] FederateRunner tearDown finished.");
    }

    private void shutdown() {
        try {
            this.tearDown();
        } catch(Exception e){
            LOGGER.error("[shutdown] FederateRunner teardown failed", e);
        }
        System.exit(-1);
    }

    // TODO: This this method (and this whole class)
    private void triggerClientChange(BtcToRskClient client, Optional<Federation> federation) {
        client.stop();
        federation.ifPresent(btcReleaseClient::stop);
        // Only start if this federator is part of the new federation
        if (federation.isPresent() && federation.get().isMember(this.member)) {
            String federationAddress = federation.get().getAddress().toString();
            LOGGER.debug("[triggerClientChange] Starting lock and release clients since I belong to federation {}", federationAddress);
            LOGGER.info("[triggerClientChange] Joined to {} federation", federationAddress);
            client.start(federation.get());
            btcReleaseClient.start(federation.get());
        } else {
            LOGGER.warn("[triggerClientChange] This federator node is not part of the new federation. Check your configuration for signers BTC, RSK and MST keys");
        }
    }

    @Override
    public void stop() {
        LOGGER.info("[stop] Shutting down Federation node");
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
        LOGGER.info("[stop] Federation node Shut down.");
    }

    private BitcoinWrapper createAndSetupBitcoinWrapper(
        BtcLockSenderProvider btcLockSenderProvider,
        PeginInstructionsProvider peginInstructionsProvider) throws UnknownHostException {

        Context btcContext = new Context(ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString()));
        File pegDirectory = new File(this.btcToRskClientFileStorage.getInfo().getPegDirectoryPath());
        Kit kit = new Kit(btcContext, pegDirectory, "BtcToRskClient");

        BitcoinWrapper wrapper = new BitcoinWrapperImpl(
            btcContext,
            bridgeConstants,
            btcLockSenderProvider,
            peginInstructionsProvider,
            federatorSupport,
            kit
        );
        wrapper.setup(federatorSupport.getBitcoinPeerAddresses());
        wrapper.start();

        return wrapper;
    }
}
