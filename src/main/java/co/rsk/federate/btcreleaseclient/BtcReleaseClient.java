package co.rsk.federate.btcreleaseclient;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.script.RedeemScriptParser;
import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.config.BridgeConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.FedNodeRunner;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.FederationCantSignException;
import co.rsk.federate.signing.FederatorAlreadySignedException;
import co.rsk.federate.signing.KeyId;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.message.HSMReleaseCreationInformationException;
import co.rsk.federate.signing.hsm.message.PegoutCreationInformation;
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
import co.rsk.peg.Federation;
import co.rsk.peg.StateForFederator;
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
 * Manages signing and broadcasting pegouts
 * @author Oscar Guindzberg
 */
public class BtcReleaseClient {
    private static final Logger logger = LoggerFactory.getLogger(BtcReleaseClient.class);
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final List<DataWord> SINGLE_RELEASE_BTC_TOPIC_RLP = Collections.singletonList(Bridge.RELEASE_BTC_TOPIC);
    private static final DataWord SINGLE_RELEASE_BTC_TOPIC_SOLIDITY = DataWord.valueOf(BridgeEvents.RELEASE_BTC.getEvent().encodeSignatureLong());

    private ActivationConfig activationConfig;
    private PeerGroup peerGroup;

    private final Ethereum ethereum;
    private final FederatorSupport federatorSupport;
    private final FedNodeSystemProperties systemProperties;
    private final Set<Federation> observedFederations;
    private final NodeBlockProcessor nodeBlockProcessor;
    private final BridgeConstants bridgeConstants;
    private final boolean isPegoutEnabled;

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
        FedNodeSystemProperties systemProperties,
        NodeBlockProcessor nodeBlockProcessor
    ) {
        this.ethereum = ethereum;
        this.federatorSupport = federatorSupport;
        this.systemProperties = systemProperties;
        this.observedFederations = new HashSet<>();
        this.blockListener = new BtcReleaseEthereumListener();
        this.bridgeConstants = this.systemProperties.getNetworkConstants().getBridgeConstants();
        this.isPegoutEnabled = this.systemProperties.isPegoutEnabled();
        this.nodeBlockProcessor = nodeBlockProcessor;
    }

    public void setup(
        ECDSASigner signer,
        ActivationConfig activationConfig,
        SignerMessageBuilderFactory signerMessageBuilderFactory,
        ReleaseCreationInformationGetter releaseCreationInformationGetter,
        ReleaseRequirementsEnforcer releaseRequirementsEnforcer,
        BtcReleaseClientStorageAccessor storageAccessor,
        BtcReleaseClientStorageSynchronizer storageSynchronizer
    ) throws BtcReleaseClientException {
        this.signer = signer;
        this.activationConfig = activationConfig;
        logger.debug("[setup] Signer: {}", signer.getClass());

        org.bitcoinj.core.Context btcContext = new org.bitcoinj.core.Context(ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString()));
        peerGroup = new PeerGroup(btcContext);
        try {
            if (!federatorSupport.getBitcoinPeerAddresses().isEmpty()) {
                for (PeerAddress peerAddress : federatorSupport.getBitcoinPeerAddresses()) {
                    peerGroup.addAddress(peerAddress);
                }
                peerGroup.setMaxConnections(federatorSupport.getBitcoinPeerAddresses().size());
            }
        } catch(Exception e) {
            throw new BtcReleaseClientException("Error configuring peerSupport", e);
        }
        peerGroup.start();

        blockListener = new BtcReleaseEthereumListener();
        this.signerMessageBuilderFactory = signerMessageBuilderFactory;
        this.releaseCreationInformationGetter = releaseCreationInformationGetter;
        this.releaseRequirementsEnforcer = releaseRequirementsEnforcer;

        this.storageAccessor = storageAccessor;
        this.storageSynchronizer = storageSynchronizer;

        logger.debug("[setup] Is pegout enabled? {}", isPegoutEnabled);
    }

    public void start(Federation federation) {
        if (!observedFederations.contains(federation)) {
            observedFederations.add(federation);
            logger.debug("[start] observing Federation {}", federation.getAddress());
        }
        if (observedFederations.size() == 1) {
            // If there is just one observed Federation, it means the btcReleaseClient wasn't started
            logger.debug("[start] Starting");
            ethereum.addListener(this.blockListener);
        }
    }

    public void stop(Federation federation) {
        if (observedFederations.contains(federation)) {
            observedFederations.remove(federation);
            logger.debug("[stop] not observing Federation {}", federation.getAddress());
        }
        if (observedFederations.isEmpty()) {
            // If there are no more observed Federations, the btcReleaseClient should stop
            logger.debug("[stop] Stopping");
            ethereum.removeListener(this.blockListener);
        }
    }

    @PreDestroy
    public void tearDown() {
        org.bitcoinj.core.Context.propagate(new org.bitcoinj.core.Context(ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString())));
        peerGroup.stop();
        peerGroup = null;
    }

    private class BtcReleaseEthereumListener extends EthereumListenerAdapter {
        @Override
        public void onBestBlock(org.ethereum.core.Block block, List<TransactionReceipt> receipts) {
            boolean hasBetterBlockToSync = nodeBlockProcessor.hasBetterBlockToSync();
            boolean isStorageSynced = storageSynchronizer.isSynced();
            if (hasBetterBlockToSync || !isStorageSynced) {
                logger.trace(
                    "[onBestBlock] Node is not ready to process pegouts. hasBetterBlockToSync: {} isStorageSynced: {}",
                    hasBetterBlockToSync,
                    isStorageSynced
                );
                return;
            }
            // Processing transactions waiting for signatures on best block only still "works",
            // since it all lies within RSK's blockchain and normal rules apply. I.e., this
            // process works on a block-by-block basis.
            StateForFederator stateForFederator = federatorSupport.getStateForFederator();
            storageSynchronizer.processBlock(block, receipts);

            // Delegate processing to our own method
            logger.trace("[onBestBlock] Got {} pegouts", stateForFederator.getRskTxsWaitingForSignatures().entrySet().size());
            if (isPegoutEnabled) {
                processReleases(stateForFederator.getRskTxsWaitingForSignatures().entrySet());
            }
        }

        @Override
        public void onBlock(org.ethereum.core.Block block, List<TransactionReceipt> receipts) {
            if (!isPegoutEnabled || nodeBlockProcessor.hasBetterBlockToSync()) {
                return;
            }
            // Pegout must be processed on an every-single-block basis,
            // since otherwise we could be missing pegout potentially mined
            // on what originally were side-chains and then turned into best-chains.
            Stream<LogInfo> transactionLogs = receipts.stream().map(TransactionReceipt::getLogInfoList).flatMap(Collection::stream);
            Stream<LogInfo> bridgeLogs = transactionLogs.filter(info -> Arrays.equals(info.getAddress(), PrecompiledContracts.BRIDGE_ADDR.getBytes()));

            boolean solidityFormatIsActive = activationConfig.isActive(ConsensusRule.RSKIP146, block.getNumber());
            Stream<LogInfo> pegoutLogs = bridgeLogs.filter(info -> solidityFormatIsActive ?
                    SINGLE_RELEASE_BTC_TOPIC_SOLIDITY.equals(info.getTopics().get(0)) :
                    SINGLE_RELEASE_BTC_TOPIC_RLP.equals(info.getTopics()));

            Stream<BtcTransaction> btcTransactionsToRelease = pegoutLogs.map(info -> solidityFormatIsActive ?
                    convertToBtcTxFromSolidityData(info.getData()) :
                    convertToBtcTxFromRLPData(info.getData()));

            btcTransactionsToRelease.forEach(BtcReleaseClient.this::onBroadcastingSignedPegout);
        }

        private BtcTransaction convertToBtcTxFromRLPData(byte[] dataFromBtcReleaseTopic) {
            RLPList dataElements = (RLPList)RLP.decode2(dataFromBtcReleaseTopic).get(0);

            return new BtcTransaction(bridgeConstants.getBtcParams(), dataElements.get(1).getRLPData());
        }

        private BtcTransaction convertToBtcTxFromSolidityData(byte[] dataFromBtcReleaseTopic) {
            return new BtcTransaction(bridgeConstants.getBtcParams(),
                (byte[])BridgeEvents.RELEASE_BTC.getEvent().decodeEventData(dataFromBtcReleaseTopic)[0]);
        }
    }

    protected void processReleases(Set<Map.Entry<Keccak256, BtcTransaction>> pegouts) {
        try {
            logger.debug("[processReleases] Starting process with {} pegouts", pegouts.size());
            KeyId keyId = FedNodeRunner.BTC_KEY_ID;
            int version = signer.getVersionForKeyId(keyId);
            // Get pegout information and store it in a new list
            List<PegoutCreationInformation> pegoutsReadyToSign = new ArrayList<>();
            for (Map.Entry<Keccak256, BtcTransaction> pegout : pegouts) {
                BtcTransaction pegoutBtcTx = pegout.getValue();
                tryGetReleaseInformation(version, pegout.getKey(), pegoutBtcTx)
                    .ifPresent(pegoutsReadyToSign::add);
            }
            logger.debug("[processReleases] Going to sign {} pegouts", pegoutsReadyToSign.size());
            // TODO: Sorting and then looping again is not efficient but we are making a compromise on performance here as we don't have that many pegouts
            // Sort descending
            pegoutsReadyToSign.sort((a, b) -> (int) (b.getPegoutCreationRskBlock().getNumber() - a.getPegoutCreationRskBlock().getNumber()));
            // Sign only the first element
            if (!pegoutsReadyToSign.isEmpty()) {
                signConfirmedPegout(version, pegoutsReadyToSign.get(0));
            }
        } catch (Exception e) {
            logger.error("[processReleases] There was an error trying to process pegouts", e);
        }
        logger.trace("[processReleases] Finished processing pegouts");
    }

    protected Optional<PegoutCreationInformation> tryGetReleaseInformation(
        int signerVersion,
        Keccak256 pegoutConfirmationRskTxHash,
        BtcTransaction pegoutBtcTx
    ) {
        try {
            // Discard transactions this fed already signed or cannot be signed by the observed federations
            logger.trace("[tryGetReleaseInformation] Validating if pegout btcTxHash {} can be signed by observed federations and " +
                    "that it is not already signed by current fed", pegoutBtcTx.getHash());
            validateConfirmedPegoutCanBeSigned(pegoutBtcTx);

            // IMPORTANT: As per the current behaviour of the bridge, no pegout should have inputs to be signed
            // by different federations. Taking this into account, when removing the signatures from the tx new
            // scriptSigs are created that all spend from the same federation
            logger.trace("[tryGetReleaseInformation] Removing possible signatures from pegout btcTxHash {}", pegoutBtcTx.getHash());
            Federation spendingFed = getSpendingFederation(pegoutBtcTx);
            removeSignaturesFromPegoutBtxTx(pegoutBtcTx, spendingFed);
            logger.trace("[tryGetReleaseInformation] pegout btcTxHash without signatures {}", pegoutBtcTx.getHash());

            logger.trace("[tryGetReleaseInformation] Is tx in storage? {}", storageAccessor.hasBtcTxHash(pegoutBtcTx.getHash()));
            // Try to get the pegoutCreationRskTxHash from the map in memory, and if not found then use pegoutConfirmationRskTxHash
            Keccak256 pegoutCreationRskTxHash = storageAccessor.hasBtcTxHash(pegoutBtcTx.getHash()) ?
                storageAccessor.getRskTxHash(pegoutBtcTx.getHash()) :
                pegoutConfirmationRskTxHash;

            logger.debug("[tryGetReleaseInformation] Going to lookup pegoutCreationRskTxHash {} to sign", pegoutCreationRskTxHash);

            // [-- Ignore punished transactions] --> this won't be done for now but should be taken into consideration
            // -- Get Real Block where release_requested was emmited
            logger.trace("[tryGetReleaseInformation] Getting pegout information");
            return Optional.of(releaseCreationInformationGetter.getPegoutCreationInformationToSign(
                signerVersion,
                pegoutCreationRskTxHash,
                pegoutBtcTx,
                pegoutConfirmationRskTxHash
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

    protected void validateConfirmedPegoutCanBeSigned(BtcTransaction pegoutBtcTx) throws FederatorAlreadySignedException, FederationCantSignException {
        try {
            KeyId keyId = FedNodeRunner.BTC_KEY_ID;
            BtcECKey federatorPublicKey = signer.getPublicKey(keyId).toBtcKey();
            logger.trace("[validateConfirmedPegoutCanBeSigned] Federator public key {}", federatorPublicKey);

            for (int inputIndex = 0; inputIndex < pegoutBtcTx.getInputs().size(); inputIndex++) {
                TransactionInput txIn = pegoutBtcTx.getInput(inputIndex);
                Script redeemScript = getRedeemScriptFromInput(txIn);
                Script standardRedeemScript = extractStandardRedeemScript(redeemScript);

                // Check if input is not already signed by the current federator
                logger.trace("[validateConfirmedPegoutCanBeSigned] Checking if the input {} is not already signed by the current federator", inputIndex);
                co.rsk.bitcoinj.core.Sha256Hash sigHash = pegoutBtcTx.hashForSignature(
                        inputIndex,
                        redeemScript,
                        BtcTransaction.SigHash.ALL,
                        false
                );
                if (BridgeUtils.isInputSignedByThisFederator(federatorPublicKey, sigHash, txIn)) {
                    String message = String.format(
                            "PegoutBtcTxHash %s input %d already signed by current federator with public key %s",
                            pegoutBtcTx.getHashAsString(),
                            inputIndex,
                            federatorPublicKey
                    );
                    throw new FederatorAlreadySignedException(message);
                }

                // Check if any of the observed federations can sign the tx
                logger.trace("[validateConfirmedPegoutCanBeSigned] Checking if any of the observed federations can sign the pegout btc tx input {}", inputIndex);
                observedFederations.stream()
                        .forEach(f -> logger.trace("[validateConfirmedPegoutCanBeSigned] federation p2sh redeem script {}", f.getRedeemScript()));
                List<Federation> spendingFedFilter = observedFederations.stream()
                        .filter(f -> f.getStandardRedeemScript().equals(standardRedeemScript)).collect(Collectors.toList());
                logger.debug("[validateConfirmedPegoutCanBeSigned] spendingFedFilter size {}", spendingFedFilter.size());
                if (spendingFedFilter.isEmpty()) {
                    String message = String.format(
                            "PegoutBtcTxHash %s can't be signed by any of the observed federations",
                            pegoutBtcTx.getHash()
                    );
                    throw new FederationCantSignException(message);
                }
            }
        } catch (SignerException e) {
            String message = String.format("[validateConfirmedPegoutCanBeSigned] Error validating pegoutBtcTxHash %s, " +
                    "failed to get current federator public key", pegoutBtcTx.getHashAsString());
            logger.error(message, e);
        }
    }

    protected void signConfirmedPegout(int signerVersion, PegoutCreationInformation pegoutCreationInformation) {
        try {
            logger.debug("[signConfirmedPegout] HSM signer version {}", signerVersion);
            logger.debug("[signConfirmedPegout] Going to sign pegout with creationRskTxHash: {}", pegoutCreationInformation.getPegoutConfirmationRskTxHash());
            logger.trace("[signConfirmedPegout] Enforce signer requirements");
            releaseRequirementsEnforcer.enforce(signerVersion, pegoutCreationInformation);
            SignerMessageBuilder messageBuilder = signerMessageBuilderFactory.buildFromConfig(
                signerVersion,
                pegoutCreationInformation
            );
            co.rsk.bitcoinj.core.Context.propagate(new co.rsk.bitcoinj.core.Context(bridgeConstants.getBtcParams()));
            List<byte[]> signatures = new ArrayList<>();
            for (int inputIndex = 0; inputIndex < pegoutCreationInformation.getPegoutBtcTx().getInputs().size(); inputIndex++) {
                SignerMessage messageToSign = messageBuilder.buildMessageForIndex(inputIndex);
                logger.trace("[signConfirmedPegout] Message to sign: {}", messageToSign.getClass());
                ECKey.ECDSASignature ethSig = signer.sign(FedNodeRunner.BTC_KEY_ID, messageToSign);
                logger.debug("[signConfirmedPegout] Message successfully signed");
                BtcECKey.ECDSASignature sig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
                signatures.add(sig.encodeToDER());
            }

            logger.info("[signConfirmedPegout] Signed pegoutRskTxHash {}", pegoutCreationInformation.getPegoutConfirmationRskTxHash());
            federatorSupport.addSignature(signatures, pegoutCreationInformation.getPegoutConfirmationRskTxHash().getBytes());
        } catch (SignerException e) {
            String message = String.format("Error signing pegoutRskTxHash %s", pegoutCreationInformation.getPegoutConfirmationRskTxHash());
            logger.error(message, e);
            panicProcessor.panic("pegoutbtctx", message);
        } catch (HSMClientException | SignerMessageBuilderException | ReleaseRequirementsEnforcerException e) {
            logger.error("[signConfirmedPegout] {}", e.getMessage());
            panicProcessor.panic("pegoutbtctx", e.getMessage());
        } catch (Exception e) {
            String message = String.format(
                "[signRelease] There was an error trying to sign pegout with btcTxHash: %s",
                pegoutCreationInformation.getPegoutBtcTx().getHash()
            );
            logger.error(message, e);
            panicProcessor.panic("pegoutbtctx", e.getMessage());
        }
    }

    // Executed when a tx is ready for broadcasting
    public void onBroadcastingSignedPegout(BtcTransaction signedBtcTx) {
        NetworkParameters btcParams = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString());
        org.bitcoinj.core.Context.propagate(new org.bitcoinj.core.Context(btcParams));
        // broadcast signedBtcTx to the btc network
        // Wrap signedBtcTx in a org.bitcoinj.core.Transaction
        Transaction signedBtcTx2 = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString(), signedBtcTx);
        peerGroup.broadcastTransaction(signedBtcTx2);
        signedBtcTx2.getOutputs().forEach(txo -> {
            LegacyAddress destination = null;
            if (ScriptPattern.isP2SH(txo.getScriptPubKey())) {
                destination = LegacyAddress.fromScriptHash(btcParams, ScriptPattern.extractHashFromP2SH(txo.getScriptPubKey()));
            } else if (ScriptPattern.isP2PKH(txo.getScriptPubKey())) {
                destination = LegacyAddress.fromPubKeyHash(btcParams, ScriptPattern.extractHashFromP2PKH(txo.getScriptPubKey()));
            }
            logger.info("[onBroadcastingSignedPegout] Broadcasted {} to {} in pegoutBtcTxId {}", txo.getValue(), destination, signedBtcTx2.getTxId());
        });
    }

    /*
    Received pegoutBtcTx inputs are replaced by base inputs without signatures that spend from the given federation.
    This way the pegoutBtcTx has the same hash as the one registered in release_requested event topics.
     */
    protected void removeSignaturesFromPegoutBtxTx(BtcTransaction pegoutBtcTx, Federation spendingFed) {
        for (int inputIndex = 0; inputIndex < pegoutBtcTx.getInputs().size(); inputIndex++) {
            //Get redeem script for current input
            TransactionInput txInput = pegoutBtcTx.getInput(inputIndex);
            Script inputRedeemScript = getRedeemScriptFromInput(txInput);
            logger.trace("[removeSignaturesFromPegoutBtxTx] input {} scriptSig {}", inputIndex, pegoutBtcTx.getInput(inputIndex).getScriptSig());
            logger.trace("[removeSignaturesFromPegoutBtxTx] input {} redeem script {}", inputIndex, inputRedeemScript);

            txInput.setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(spendingFed, inputRedeemScript));
            logger.debug("[removeSignaturesFromPegoutBtxTx] Updated input {} scriptSig with base input script that " +
                    "spends from the federation {}", inputIndex, spendingFed.getAddress());
        }
    }

    protected Script extractStandardRedeemScript(Script redeemScript) {
        RedeemScriptParser parser = RedeemScriptParserFactory.get(redeemScript.getChunks());
        return parser.extractStandardRedeemScript();
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
                .filter(f -> f.getStandardRedeemScript().equals(redeemScript)).collect(Collectors.toList());

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
