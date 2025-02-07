package co.rsk.federate.btcreleaseclient;

import static co.rsk.federate.signing.PowPegNodeKeyId.BTC;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.script.RedeemScriptParser;
import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.btcreleaseclient.cache.PegoutSignedCache;
import co.rsk.federate.btcreleaseclient.cache.PegoutSignedCacheImpl;
import co.rsk.federate.config.PowpegNodeSystemProperties;
import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.FederationCantSignException;
import co.rsk.federate.signing.FederatorAlreadySignedException;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.message.HSMReleaseCreationInformationException;
import co.rsk.federate.signing.hsm.message.ReleaseCreationInformation;
import co.rsk.federate.signing.hsm.message.ReleaseCreationInformationGetter;
import co.rsk.federate.signing.hsm.message.SignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilder;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderException;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderFactory;
import co.rsk.federate.signing.hsm.requirements.ReleaseRequirementsEnforcer;
import co.rsk.federate.signing.hsm.requirements.ReleaseRequirementsEnforcerException;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.BridgeUtils;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.StateForFederator;
import co.rsk.peg.StateForProposedFederator;
import co.rsk.peg.bitcoin.BitcoinUtils;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PreDestroy;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.script.ScriptPattern;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for managing the signing and broadcasting of pegout transactions
 * to the Bitcoin network in a federated bridge environment. The BtcReleaseClient
 * coordinates the execution of pegout operations, ensuring transactions are 
 * correctly signed and propagated.
 *
 * <p>Key responsibilities include:</p>
 * <ul>
 *   <li>Assembling transaction data and managing signing processes</li>
 *   <li>Validating transaction information before broadcast</li>
 *   <li>Ensuring successful pegout transaction broadcast to the Bitcoin network</li>
 * </ul>
 */
public class BtcReleaseClient {

    private static final Logger logger = LoggerFactory.getLogger(BtcReleaseClient.class);
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final List<DataWord> SINGLE_RELEASE_BTC_TOPIC_RLP = Collections.singletonList(Bridge.RELEASE_BTC_TOPIC);
    private static final DataWord SINGLE_RELEASE_BTC_TOPIC_SOLIDITY = DataWord.valueOf(BridgeEvents.RELEASE_BTC.getEvent().encodeSignatureLong());

    private final Ethereum ethereum;
    private final FederatorSupport federatorSupport;
    private final Set<Federation> observedFederations;
    private final NodeBlockProcessor nodeBlockProcessor;
    private final BridgeConstants bridgeConstants;
    private final boolean isPegoutEnabled;
    private final PegoutSignedCache pegoutSignedCache;

    private ActivationConfig activationConfig;
    private PeerGroup peerGroup;
    private ECDSASigner signer;
    private BtcReleaseEthereumListener blockListener;
    private SignerMessageBuilderFactory signerMessageBuilderFactory;
    private ReleaseCreationInformationGetter releaseCreationInformationGetter;
    private ReleaseRequirementsEnforcer releaseRequirementsEnforcer;
    private BtcReleaseClientStorageAccessor storageAccessor;
    private BtcReleaseClientStorageSynchronizer storageSynchronizer;

    public BtcReleaseClient(
        Ethereum ethereum,
        FederatorSupport federatorSupport,
        PowpegNodeSystemProperties systemProperties,
        NodeBlockProcessor nodeBlockProcessor
    ) {
        this.ethereum = ethereum;
        this.federatorSupport = federatorSupport;
        this.observedFederations = new HashSet<>();
        this.blockListener = new BtcReleaseEthereumListener();
        this.bridgeConstants = systemProperties.getNetworkConstants().getBridgeConstants();
        this.isPegoutEnabled = systemProperties.isPegoutEnabled();
        this.nodeBlockProcessor = nodeBlockProcessor;
        this.pegoutSignedCache = new PegoutSignedCacheImpl(
            systemProperties.getPegoutSignedCacheTtl(), Clock.systemUTC());
    }

    public void setup(
        ECDSASigner signer,
        ActivationConfig activationConfig,
        SignerMessageBuilderFactory signerMessageBuilderFactory,
        ReleaseCreationInformationGetter pegoutCreationInformationGetter,
        ReleaseRequirementsEnforcer releaseRequirementsEnforcer,
        BtcReleaseClientStorageAccessor storageAccessor,
        BtcReleaseClientStorageSynchronizer storageSynchronizer
    ) throws BtcReleaseClientException {
        this.signer = signer;
        this.activationConfig = activationConfig;
        logger.debug("[setup] Signer: {}", signer.getClass());

        org.bitcoinj.core.Context btcContext = new org.bitcoinj.core.Context(
            ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString()));
        peerGroup = new PeerGroup(btcContext);
        try {
            if (!federatorSupport.getBitcoinPeerAddresses().isEmpty()) {
                for (PeerAddress peerAddress : federatorSupport.getBitcoinPeerAddresses()) {
                    peerGroup.addAddress(peerAddress);
                }
                peerGroup.setMaxConnections(federatorSupport.getBitcoinPeerAddresses().size());
            }
        } catch (Exception e) {
            throw new BtcReleaseClientException("Error configuring peerSupport", e);
        }
        peerGroup.start();

        this.blockListener = new BtcReleaseEthereumListener();
        this.signerMessageBuilderFactory = signerMessageBuilderFactory;
        this.releaseCreationInformationGetter = pegoutCreationInformationGetter;
        this.releaseRequirementsEnforcer = releaseRequirementsEnforcer;

        this.storageAccessor = storageAccessor;
        this.storageSynchronizer = storageSynchronizer;

        logger.debug("[setup] Is pegout enabled? {}", isPegoutEnabled);
    }

    public void start(Federation federation) {
       FederationMember federationMember = federatorSupport.getFederationMember();
       if (!federation.isMember(federationMember)) {
            String message = String.format(
                "Member %s is no part of the federation %s",
                federationMember.getBtcPublicKey(),
                federation.getAddress());
            logger.error("[start] {}", message);
            throw new IllegalStateException(message);
        }

        if (!observedFederations.contains(federation)) {
            observedFederations.add(federation);
            logger.info("[start] Observing federation {}", federation.getAddress());
        }

        if (observedFederations.size() == 1) {
            // If there is just one observed Federation, it means the btcReleaseClient wasn't started
            logger.info("[start] Starting block listener");
            ethereum.addListener(blockListener);
        }
    }

    public void stop(Federation federation) {
        if (observedFederations.contains(federation)) {
            observedFederations.remove(federation);
            logger.info("[stop] Stopping observing federation {}", federation.getAddress());
        }

        if (observedFederations.isEmpty()) {
            // If there are no more observed Federations, the btcReleaseClient should stop
            logger.info("[stop] Stopping block listener");
            ethereum.removeListener(blockListener);
        }
    }

    @PreDestroy
    public void tearDown() {
        org.bitcoinj.core.Context.propagate(
            new org.bitcoinj.core.Context(
                ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString())));
        peerGroup.stop();
        peerGroup = null;
    }

    private class BtcReleaseEthereumListener extends EthereumListenerAdapter {
        @Override
        public void onBestBlock(org.ethereum.core.Block block, List<TransactionReceipt> receipts) {
            if (!isPegoutEnabled) {
                logger.warn("[onBestBlock] Processing of RSK transactions waiting for signatures is disabled");
                return;
            }

            boolean hasBetterBlockToSync = nodeBlockProcessor.hasBetterBlockToSync();
            boolean isStorageSynced = storageSynchronizer.isSynced();
            if (hasBetterBlockToSync || !isStorageSynced) {
                logger.trace(
                    "[onBestBlock] Node is not ready to process pegouts. hasBetterBlockToSync: {} - isStorageSynced: {}",
                    hasBetterBlockToSync,
                    isStorageSynced
                );
                return;
            }
            storageSynchronizer.processBlock(block, receipts);
          
            // Sign svp spend tx waiting for signatures, if it exists,
            // before attempting to sign any pegouts.
            if (activationConfig.isActive(ConsensusRule.RSKIP419, block.getNumber())) {
                federatorSupport.getStateForProposedFederator()
                    .map(StateForProposedFederator::getSvpSpendTxWaitingForSignatures)
                    .filter(svpSpendTxWaitingForSignatures -> isSVPSpendTxReadyToSign(block.getNumber(), svpSpendTxWaitingForSignatures))
                    .ifPresent(svpSpendTxReadyToBeSigned -> processReleases(Set.of(svpSpendTxReadyToBeSigned)));
            }

            // Processing transactions waiting for signatures on best block only still "works",
            // since it all lies within RSK's blockchain and normal rules apply. I.e., this
            // process works on a block-by-block basis.
            StateForFederator stateForFederator = federatorSupport.getStateForFederator();
            processReleases(stateForFederator.getRskTxsWaitingForSignatures().entrySet());
        }

        @Override
        public void onBlock(org.ethereum.core.Block block, List<TransactionReceipt> receipts) {
            if (!isPegoutEnabled || nodeBlockProcessor.hasBetterBlockToSync()) {
                return;
            }

            /* Pegout events must be processed on an every-single-block basis,
             since otherwise we could be missing pegouts potentially mined
             on what originally were side-chains and then turned into best-chains.*/
            Stream<LogInfo> transactionLogs = receipts.stream().map(TransactionReceipt::getLogInfoList).flatMap(Collection::stream);
            Stream<LogInfo> bridgeLogs = transactionLogs.filter(info -> Arrays.equals(info.getAddress(), PrecompiledContracts.BRIDGE_ADDR.getBytes()));

            boolean solidityFormatIsActive = activationConfig.isActive(ConsensusRule.RSKIP146, block.getNumber());
            Stream<LogInfo> pegoutLogs = bridgeLogs.filter(info -> solidityFormatIsActive ?
                    SINGLE_RELEASE_BTC_TOPIC_SOLIDITY.equals(info.getTopics().get(0)) :
                    SINGLE_RELEASE_BTC_TOPIC_RLP.equals(info.getTopics()));

            Stream<BtcTransaction> pegoutTxs = pegoutLogs.map(info -> solidityFormatIsActive ?
                    convertToBtcTxFromSolidityData(info.getData()) :
                    convertToBtcTxFromRLPData(info.getData()));

            pegoutTxs.forEach(BtcReleaseClient.this::onBtcRelease);
        }

        /**
         * Determines if the svp spend transaction hash is ready to be signed based on its block confirmations.
         *
         * <p>
         * This method retrieves the block associated with the given transaction hash and calculates
         * the difference in block numbers between the current block and the block containing the transaction.
         * If the difference meets or exceeds the required confirmation threshold defined in the bridge constants,
         * the transaction is considered ready for signing.
         * </p>
         *
         * @param currentBlockNumber the current block number in the blockchain
         * @param svpSpendTxEntry the Keccak256 hash and the Bitcoin transaction of the svp spend transaction waiting to be signed
         * @return {@code true} if the transaction has the required number of confirmations and is ready to be signed;
         *         {@code false} otherwise
         */
        private boolean isSVPSpendTxReadyToSign(long currentBlockNumber, Map.Entry<Keccak256, BtcTransaction> svpSpendTxEntry) {
            try {

                BtcTransaction svpSpendTx = svpSpendTxEntry.getValue();

                logger.debug("[isSvpSpendTxReadyToSign] SVP spend tx before removing signatures [{}]", svpSpendTx.getHash());
                BitcoinUtils.removeSignaturesFromTransactionWithP2shMultiSigInputs(svpSpendTx);
                logger.debug("[isSvpSpendTxReadyToSign] SVP spend tx after removing signatures [{}]", svpSpendTx.getHash());

                int version = signer.getVersionForKeyId(BTC.getKeyId());
                ReleaseCreationInformation releaseCreationInformation = releaseCreationInformationGetter.getTxInfoToSign(
                    version, svpSpendTxEntry.getKey(), svpSpendTx);

                boolean isReadyToSign = Optional.ofNullable(releaseCreationInformation)
                    .map(ReleaseCreationInformation::getPegoutCreationBlock)
                    .map(Block::getNumber)
                    .map(blockNumberWithSvpSpendTx -> currentBlockNumber - blockNumberWithSvpSpendTx)
                    .filter(confirmationDifference -> confirmationDifference >= bridgeConstants.getRsk2BtcMinimumAcceptableConfirmations())
                    .isPresent();
                
                logger.info("[isSvpSpendTxReadyToSign] SVP spend tx readiness check for signing: tx hash [{}], Current block [{}], Ready to sign? [{}]",
                    svpSpendTxEntry.getKey(),
                    currentBlockNumber,
                    isReadyToSign ? "YES" : "NO");

                return isReadyToSign;
            } catch (Exception e) {
                logger.error("[isSvpSpendTxReadyToSign] Error ocurred while checking if SVP spend tx is ready to be signed", e);
               
                return false;
            }
        }

        private BtcTransaction convertToBtcTxFromRLPData(byte[] dataFromBtcReleaseTopic) {
            RLPList dataElements = (RLPList) RLP.decode2(dataFromBtcReleaseTopic).get(0);

            return new BtcTransaction(bridgeConstants.getBtcParams(), dataElements.get(1).getRLPData());
        }

        private BtcTransaction convertToBtcTxFromSolidityData(byte[] dataFromBtcReleaseTopic) {
            return new BtcTransaction(bridgeConstants.getBtcParams(),
                (byte[]) BridgeEvents.RELEASE_BTC.getEvent().decodeEventData(dataFromBtcReleaseTopic)[0]);
        }
    }

    protected void processReleases(Set<Map.Entry<Keccak256, BtcTransaction>> pegouts) {
        try {
            logger.info("[processReleases] Starting signing process with {} pegouts", pegouts.size());
            int version = signer.getVersionForKeyId(BTC.getKeyId());
            // Get pegout information and store it in a new list
            List<ReleaseCreationInformation> pegoutsReadyToSign = new ArrayList<>();
            for (Map.Entry<Keccak256, BtcTransaction> pegout : pegouts) {
                /*
                     Before RSKIP375 this key represents the pegout confirmed rsk tx hash
                     but since RSKIP375 this key of pegoutWaitingForSignature set represents the pegout creation rsk tx hash.
                 */
                Keccak256 pegoutCreationRskTxHash = pegout.getKey();
                BtcTransaction pegoutBtcTx = pegout.getValue();

                tryGetReleaseInformation(version, pegoutCreationRskTxHash, pegoutBtcTx)
                    .ifPresent(pegoutsReadyToSign::add);
            }
            logger.debug("[processReleases] Going to sign {} pegouts", pegoutsReadyToSign.size());
            // TODO: Sorting and then looping again is not efficient but we are making a compromise on performance here as we don't have that many release txs
            // Sort descending
            pegoutsReadyToSign.sort((a, b) -> (int) (b.getPegoutCreationBlock().getNumber() - a.getPegoutCreationBlock().getNumber()));
            // Sign only the first element
            if (!pegoutsReadyToSign.isEmpty()) {
                signRelease(version, pegoutsReadyToSign.get(0));
            }
        } catch (Exception e) {
            logger.error("[processReleases] There was an error trying to process pegouts", e);
        }
        logger.trace("[processReleases] Finished processing pegouts");
    }

    protected Optional<ReleaseCreationInformation> tryGetReleaseInformation(
        int signerVersion,
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx
    ) {
        try {
            // Discard pegout tx if processed in a previous round of execution
            logger.trace(
                "[tryGetReleaseInformation] Checking if pegoutCreationTxHash {} has already been signed",
                pegoutCreationRskTxHash);
            validateTxIsNotCached(pegoutCreationRskTxHash);

            // Discard pegout btc tx this fed already signed or cannot be signed by the observed federations
            logger.trace("[tryGetReleaseInformation] Validating if pegoutBtcTxHash {} can be signed by observed federations and " +
                             "that it is not already signed by current fed", pegoutBtcTx.getHash());
            validateTxCanBeSigned(pegoutBtcTx);

            // IMPORTANT: As per the current behaviour of the bridge, no pegout should have inputs to be signed
            // by different federations. Taking this into account, when removing the signatures from the tx new
            // scriptSigs are created that all spend from the same federation
            logger.trace("[tryGetReleaseInformation] Removing possible signatures from pegout btcTxHash {}", pegoutBtcTx.getHash());
            Federation spendingFed = getSpendingFederation(pegoutBtcTx);
            removeSignaturesFromTransaction(pegoutBtcTx, spendingFed);
            logger.trace("[tryGetReleaseInformation] pegout btcTxHash without signatures {}", pegoutBtcTx.getHash());

            logger.trace("[tryGetReleaseInformation] Is pegout btc in storage? {}", storageAccessor.hasBtcTxHash(pegoutBtcTx.getHash()));
            // Try to get the rsk transaction from the map in memory  where the pegout was created
            Keccak256 pegoutCreationRskTxHashToUse = storageAccessor.hasBtcTxHash(pegoutBtcTx.getHash()) ?
                storageAccessor.getRskTxHash(pegoutBtcTx.getHash()) :
                pegoutCreationRskTxHash;

            logger.debug("[tryGetReleaseInformation] Going to lookup rsk transaction {} to get pegout to sign", pegoutCreationRskTxHash);

            // [-- Ignore punished transactions] --> this won't be done for now but should be taken into consideration
            // -- Get Real Block where release_requested was emitted
            logger.trace("[tryGetReleaseInformation] Getting pegout information");
            return Optional.of(releaseCreationInformationGetter.getTxInfoToSign(
                signerVersion,
                pegoutCreationRskTxHashToUse,
                pegoutBtcTx,
                pegoutCreationRskTxHash
            ));
        } catch (HSMReleaseCreationInformationException | FederationCantSignException e) {
            String message = String.format(
                "[tryGetReleaseInformation] There was an error trying to process pegout with btcTxHash %s",
                pegoutBtcTx.getHash()
            );
            logger.error(message, e);
        } catch (FederatorAlreadySignedException e) {
            logger.info("[tryGetReleaseInformation] {}", e.getMessage());
        }
        return Optional.empty();
    }

    void validateTxIsNotCached(Keccak256 pegoutCreationRskTxHash) throws FederatorAlreadySignedException {
        if (pegoutSignedCache.hasAlreadyBeenSigned(pegoutCreationRskTxHash)) {
            String message = String.format(
                "Rsk pegout creation tx hash %s was found in the pegouts signed cache",
                pegoutCreationRskTxHash);
            throw new FederatorAlreadySignedException(message);
        }
    }

    protected void validateTxCanBeSigned(BtcTransaction pegoutBtcTx) throws FederatorAlreadySignedException, FederationCantSignException {
        try {
            BtcECKey federatorPublicKey = signer.getPublicKey(BTC.getKeyId()).toBtcKey();
            logger.trace("[validateTxCanBeSigned] Federator public key {}", federatorPublicKey);

            for (int inputIndex = 0; inputIndex < pegoutBtcTx.getInputs().size(); inputIndex++) {
                TransactionInput txIn = pegoutBtcTx.getInput(inputIndex);
                Script redeemScript = getRedeemScriptFromInput(txIn);
                Script standardRedeemScript = extractStandardRedeemScript(redeemScript);

                // Check if input is not already signed by the current federator
                logger.trace("[validateTxCanBeSigned] Checking if the input {} is not already signed by the current federator", inputIndex);
                co.rsk.bitcoinj.core.Sha256Hash sigHash = pegoutBtcTx.hashForSignature(
                        inputIndex,
                        redeemScript,
                        BtcTransaction.SigHash.ALL,
                        false
                );
                if (BridgeUtils.isInputSignedByThisFederator(federatorPublicKey, sigHash, txIn)) {
                    String message = String.format(
                            "Btc tx %s input %d already signed by current federator with public key %s",
                            pegoutBtcTx.getHashAsString(),
                            inputIndex,
                            federatorPublicKey
                    );
                    throw new FederatorAlreadySignedException(message);
                }

                // Check if any of the observed federations can sign the tx
                logger.trace("[validateTxCanBeSigned] Checking if any of the observed federations can sign the tx input {}", inputIndex);
                observedFederations.forEach(
                    f -> logger.trace("[validateTxCanBeSigned] federation p2sh redeem script {}", f.getRedeemScript()));
                List<Federation> spendingFedFilter = observedFederations.stream()
                        .filter(f -> (extractDefaultRedeemScript(f)).equals(standardRedeemScript)).collect(Collectors.toList());
                logger.debug("[validateTxCanBeSigned] spendingFedFilter size {}", spendingFedFilter.size());
                if (spendingFedFilter.isEmpty()) {
                    String message = String.format(
                            "Transaction %s can't be signed by any of the observed federations",
                            pegoutBtcTx.getHash()
                    );
                    throw new FederationCantSignException(message);
                }
            }
        } catch (SignerException e) {
            String message = String.format("[validateTxCanBeSigned] Error validating tx %s, " +
                    "failed to get current federator public key", pegoutBtcTx.getHashAsString());
            logger.error(message, e);
        }
    }

    protected void signRelease(int signerVersion, ReleaseCreationInformation pegoutCreationInformation) {
        final String topic = "btcrelease";
        try {
            logger.debug("[signRelease] HSM signer version {}", signerVersion);
            logger.debug("[signRelease] Going to sign pegout created in rsk transaction: {}", pegoutCreationInformation.getPegoutCreationRskTxHash());
            logger.trace("[signRelease] Enforce signer requirements");
            releaseRequirementsEnforcer.enforce(signerVersion, pegoutCreationInformation);
            SignerMessageBuilder messageBuilder = signerMessageBuilderFactory.buildFromConfig(
                signerVersion,
                pegoutCreationInformation
            );
            co.rsk.bitcoinj.core.Context.propagate(new co.rsk.bitcoinj.core.Context(bridgeConstants.getBtcParams()));
            List<byte[]> signatures = new ArrayList<>();
            for (int inputIndex = 0; inputIndex < pegoutCreationInformation.getPegoutBtcTx().getInputs().size(); inputIndex++) {
                SignerMessage messageToSign = messageBuilder.buildMessageForIndex(inputIndex);
                logger.trace("[signRelease] Message to sign: {}", messageToSign.getClass());
                ECKey.ECDSASignature ethSig = signer.sign(BTC.getKeyId(), messageToSign);
                logger.debug("[signRelease] Message successfully signed");
                BtcECKey.ECDSASignature sig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
                signatures.add(sig.encodeToDER());
            }

            logger.info("[signRelease] Signed pegout created in rsk transaction {}", pegoutCreationInformation.getPegoutConfirmationRskTxHash());
            federatorSupport.addSignature(signatures, pegoutCreationInformation.getPegoutConfirmationRskTxHash().getBytes());

            logger.trace("[signRelease] Put pegoutCreationRskTxHash {} in the pegouts signed cache",
                pegoutCreationInformation.getPegoutCreationRskTxHash());
            pegoutSignedCache.putIfAbsent(
                pegoutCreationInformation.getPegoutCreationRskTxHash());
        } catch (SignerException e) {
            String message = String.format("Error signing pegout created in rsk transaction %s", pegoutCreationInformation.getPegoutCreationRskTxHash());
            logger.error(message, e);
            panicProcessor.panic(topic, message);
        } catch (HSMClientException | SignerMessageBuilderException | ReleaseRequirementsEnforcerException e) {
            logger.error("[signRelease] {}", e.getMessage());
            panicProcessor.panic(topic, e.getMessage());
        } catch (Exception e) {
            String message = String.format(
                "[signRelease] There was an error trying to sign pegout created in rsk tx: %s and btc transaction: %s",
                pegoutCreationInformation.getPegoutCreationRskTxHash(),
                pegoutCreationInformation.getPegoutBtcTx().getHash()
            );
            logger.error(message, e);
            panicProcessor.panic(topic, e.getMessage());
        }
    }

    // Executed when a tx is ready for broadcasting
    public void onBtcRelease(BtcTransaction signedBtcTx) {
        NetworkParameters btcParams = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString());
        org.bitcoinj.core.Context.propagate(new org.bitcoinj.core.Context(btcParams));
        // broadcast signedBtcTx to the btc network
        // Wrap signedBtcTx in a org.bitcoinj.core.Transaction
        Transaction signedBtcTxToBroadcast = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString(), signedBtcTx);
        peerGroup.broadcastTransaction(signedBtcTxToBroadcast);
        signedBtcTxToBroadcast.getOutputs().forEach(txo -> {
            LegacyAddress destination = null;
            if (ScriptPattern.isP2SH(txo.getScriptPubKey())) {
                destination = LegacyAddress.fromScriptHash(btcParams, ScriptPattern.extractHashFromP2SH(txo.getScriptPubKey()));
            } else if (ScriptPattern.isP2PKH(txo.getScriptPubKey())) {
                destination = LegacyAddress.fromPubKeyHash(btcParams, ScriptPattern.extractHashFromP2PKH(txo.getScriptPubKey()));
            }
            logger.info("Broadcasted {} to {} in pegoutBtcTxId {}", txo.getValue(), destination, signedBtcTxToBroadcast.getTxId());
        });
    }

    /*
    Received pegoutBtcTx inputs are replaced by base inputs without signatures that spend from the given federation.
    This way the pegoutBtcTx has the same hash as the one registered in release_requested event topics.
     */
    protected void removeSignaturesFromTransaction(BtcTransaction pegoutBtcTx, Federation spendingFed) {
        for (int inputIndex = 0; inputIndex < pegoutBtcTx.getInputs().size(); inputIndex++) {
            //Get redeem script for current input
            TransactionInput txInput = pegoutBtcTx.getInput(inputIndex);
            Script inputRedeemScript = getRedeemScriptFromInput(txInput);
            logger.trace("[removeSignaturesFromTransaction] input {} scriptSig {}", inputIndex, pegoutBtcTx.getInput(inputIndex).getScriptSig());
            logger.trace("[removeSignaturesFromTransaction] input {} redeem script {}", inputIndex, inputRedeemScript);

            txInput.setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(spendingFed, inputRedeemScript));
            logger.debug("[removeSignaturesFromTransaction] Updated input {} scriptSig with base input script that " +
                    "spends from the federation {}", inputIndex, spendingFed.getAddress());
        }
    }

    protected Script extractStandardRedeemScript(Script redeemScript) {
        RedeemScriptParser redeemScriptParser = RedeemScriptParserFactory.get(redeemScript.getChunks());
        List<ScriptChunk> defaultRedeemScriptChunks = redeemScriptParser.extractStandardRedeemScriptChunks();
        return new ScriptBuilder().addChunks(defaultRedeemScriptChunks).build();
    }

    private Script extractDefaultRedeemScript(Federation federation) {
        if (federation instanceof ErpFederation) {
            return ((ErpFederation) federation).getDefaultRedeemScript();
        }
        return federation.getRedeemScript();
    }

    protected Script getRedeemScriptFromInput(TransactionInput txInput) {
        Script inputScript = txInput.getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        // Last chunk of the scriptSig contains the redeem script
        byte[] program = chunks.get(chunks.size() - 1).data;
        return new Script(program);
    }

    protected Federation getSpendingFederation(BtcTransaction btcTx) {
        TransactionInput firstInput = btcTx.getInput(0);
        Script redeemScript = extractStandardRedeemScript(getRedeemScriptFromInput(firstInput));

        List<Federation> spendingFedFilter = observedFederations.stream()
                .filter(f -> (extractDefaultRedeemScript(f)).equals(redeemScript)).collect(Collectors.toList());

        return spendingFedFilter.get(0);
    }

    private static Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation, Script customRedeemScript) {
        Script scriptPubKey = federation.getP2SHScript();
        Script redeemScript = federation.getRedeemScript();
        RedeemData redeemData = RedeemData.of(federation.getBtcPublicKeys(), redeemScript);

        // customRedeemScript might not be actually custom, but just in case, use the provided redeemScript
        return scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), customRedeemScript);
    }
}
