package co.rsk.federate.btcreleaseclient;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutPoint;
import co.rsk.bitcoinj.script.RedeemScriptParser;
import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.config.BridgeConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.bitcoin.BitcoinRpcClient;
import co.rsk.federate.bitcoin.BitcoinWrapper;
import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.rpc.JsonRpcException;
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
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.StateForFederator;

import java.io.IOException;
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
import org.bitcoinj.core.TransactionOutput;
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

import static co.rsk.federate.signing.PowPegNodeKeyId.BTC_KEY_ID;

/**
 * Manages signing and broadcasting release txs
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
    private BitcoinWrapper bitcoinWrapper;

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
        BitcoinWrapper bitcoinWrapper,
        ECDSASigner signer,
        ActivationConfig activationConfig,
        SignerMessageBuilderFactory signerMessageBuilderFactory,
        ReleaseCreationInformationGetter releaseCreationInformationGetter,
        ReleaseRequirementsEnforcer releaseRequirementsEnforcer,
        BtcReleaseClientStorageAccessor storageAccessor,
        BtcReleaseClientStorageSynchronizer storageSynchronizer
    ) throws BtcReleaseClientException {
        this.bitcoinWrapper = bitcoinWrapper;
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
                    "[onBestBlock] Node is not ready to process releases. hasBetterBlockToSync: {} isStorageSynced: {}",
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
            logger.trace("[onBestBlock] Got {} releases", stateForFederator.getRskTxsWaitingForSignatures().entrySet().size());
            if (isPegoutEnabled) {
                processReleases(stateForFederator.getRskTxsWaitingForSignatures().entrySet());
            }
        }

        @Override
        public void onBlock(org.ethereum.core.Block block, List<TransactionReceipt> receipts) {
            if (!isPegoutEnabled || nodeBlockProcessor.hasBetterBlockToSync()) {
                return;
            }
            // BTC-release events must be processed on an every-single-block basis,
            // since otherwise we could be missing release transactions potentially mined
            // on what originally were side-chains and then turned into best-chains.
            Stream<LogInfo> transactionLogs = receipts.stream().map(TransactionReceipt::getLogInfoList).flatMap(Collection::stream);
            Stream<LogInfo> bridgeLogs = transactionLogs.filter(info -> Arrays.equals(info.getAddress(), PrecompiledContracts.BRIDGE_ADDR.getBytes()));

            boolean solidityFormatIsActive = activationConfig.isActive(ConsensusRule.RSKIP146, block.getNumber());
            Stream<LogInfo> releaseBtcLogs = bridgeLogs.filter(info -> solidityFormatIsActive ?
                    SINGLE_RELEASE_BTC_TOPIC_SOLIDITY.equals(info.getTopics().get(0)) :
                    SINGLE_RELEASE_BTC_TOPIC_RLP.equals(info.getTopics()));

            Stream<BtcTransaction> btcTransactionsToRelease = releaseBtcLogs.map(info -> solidityFormatIsActive ?
                    convertToBtcTxFromSolidityData(info.getData()) :
                    convertToBtcTxFromRLPData(info.getData()));

            btcTransactionsToRelease.forEach(BtcReleaseClient.this::onBtcRelease);
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

    protected void processReleases(Set<Map.Entry<Keccak256, BtcTransaction>> releases) {
        try {
            logger.debug("[processReleases] Starting process with {} releases", releases.size());
            int version = signer.getVersionForKeyId(BTC_KEY_ID.getKeyId());
            // Get release information and store it in a new list
            List<ReleaseCreationInformation> releasesReadyToSign = new ArrayList<>();
            for (Map.Entry<Keccak256, BtcTransaction> release : releases) {
                BtcTransaction releaseTx = release.getValue();

                // Proving an utxo can be fetched from the bitcoin node
                String rpcUser = "user";
                String rpcPassword = "pass";
                getTransactionUsingBitcoinRpcClient(rpcUser, rpcPassword, "http://127.0.0.1:32591");

                Transaction btcTransaction = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString(), releaseTx);
                Transaction randomTx = bitcoinWrapper.getTransaction(org.bitcoinj.core.Sha256Hash.wrap("2227ea59130c7270e3c7b2af502bb76e0327a8b423a9419f115f3bb2c63f0600"));
                printTx(randomTx, "random tx");

                Transaction fedTx = bitcoinWrapper.getTransaction(org.bitcoinj.core.Sha256Hash.wrap("d1eb41686b5ff57b7322f38148c68a6362a42bc99f06478dbfa1274f3b030c5f"));
                printTx(fedTx, "fed tx");

                List<Transaction> utxos = btcTransaction.getInputs().stream().map(transactionInput -> bitcoinWrapper.getTransaction(transactionInput.getOutpoint().getHash())).collect(Collectors.toList());
                logger.debug("[processReleases] POC - UTXOs found {}, for a number of inputs of:  {}", utxos.size(), btcTransaction.getInputs().size());
                for (int i = 0; i < utxos.size(); i++) {
                    TransactionInput releaseTxInput = releaseTx.getInput(i);
                    long utxoIdx = releaseTxInput.getOutpoint().getIndex();
                    Transaction utxo = utxos.get(i);
                    TransactionOutput utxoOutput = utxo.getOutput(utxoIdx);
                    logger.debug("[processReleases] POC - UTXO tx hash {}.", releaseTxInput.getOutpoint().getHash());
                    logger.debug("[processReleases] POC - UTXO index {}", utxoOutput.getIndex());
                    logger.debug("[processReleases] POC - UTXO value {}", utxoOutput.getValue());
                }

                tryGetReleaseInformation(version, release.getKey(), releaseTx)
                    .ifPresent(releasesReadyToSign::add);
            }
            logger.debug("[processReleases] Going to sign {} releases", releasesReadyToSign.size());
            // TODO: Sorting and then looping again is not efficient but we are making a compromise on performance here as we don't have that many release txs
            // Sort descending
            releasesReadyToSign.sort((a, b) -> (int) (b.getBlock().getNumber() - a.getBlock().getNumber()));
            // Sign only the first element
            if (releasesReadyToSign.size() > 0) {
                signRelease(version, releasesReadyToSign.get(0));
            }
        } catch (Exception e) {
            logger.error("[processReleases] There was an error trying to process releases", e);
        }
        logger.trace("[processReleases] Finished processing releases");
    }

    private void printTx(Transaction randomTx, String prefixMessage) {
        if (randomTx == null) {
            logger.debug("[processReleases] POC - {} tx could not be found.", prefixMessage);
        } else {
            logger.debug("[processReleases] POC - {} tx could be found.", prefixMessage);

            TransactionOutput txOutput = randomTx.getOutput(0);
            logger.debug("[processReleases] POC - UTXO tx hash {}.", randomTx.getTxId());
            logger.debug("[processReleases] POC - UTXO index {}", txOutput.getIndex());
            logger.debug("[processReleases] POC - UTXO value {}", txOutput.getValue());
        }
    }

    private void getTransactionUsingBitcoinRpcClient(String rpcUser, String rpcPassword, String rpcUrl) throws IOException, JsonRpcException {
        logger.debug("[processReleases] POC - bitcoin rpc connection params: rpcUser {}, rpcPassword {}, rpcUrl {}", rpcUser, rpcPassword, rpcUrl);
        BitcoinRpcClient rpcClient = new BitcoinRpcClient(rpcUrl, rpcUser, rpcPassword);

        String transactionId = "2227ea59130c7270e3c7b2af502bb76e0327a8b423a9419f115f3bb2c63f0600";
        try {
            String result = rpcClient.executeRPC("gettransaction", "\"" + transactionId + "\"");
            logger.debug("[processReleases] POC - transaction info {}", result);
        } catch (IOException e) {
            logger.error("[processReleases] POC - Error trying to get transaction info.", e);
        }
    }

    protected Optional<ReleaseCreationInformation> tryGetReleaseInformation(
        int signerVersion,
        Keccak256 rskTxHash,
        BtcTransaction releaseTx
    ) {
        try {
            // Discard transactions this fed already signed or cannot be signed by the observed federations
            logger.trace("[tryGetReleaseInformation] Validating tx {} can be signed by observed federations and " +
                    "that it is not already signed by current fed", releaseTx.getHash());
            validateTxCanBeSigned(releaseTx);

            // IMPORTANT: As per the current behaviour of the bridge, no release tx should have inputs to be signed
            // by different federations. Taking this into account, when removing the signatures from the tx new
            // scriptSigs are created that all spend from the same federation
            logger.trace("[tryGetReleaseInformation] Removing possible signatures from tx {}", releaseTx.getHash());
            Federation spendingFed = getSpendingFederation(releaseTx);
            removeSignaturesFromTransaction(releaseTx, spendingFed);
            logger.trace("[tryGetReleaseInformation] Tx hash without signatures {}", releaseTx.getHash());

            logger.trace("[tryGetReleaseInformation] Is tx in storage? {}", storageAccessor.hasBtcTxHash(releaseTx.getHash()));
            // Try to get the rskTxHash from the map in memory
            Keccak256 actualRskTxHash = storageAccessor.hasBtcTxHash(releaseTx.getHash()) ?
                storageAccessor.getRskTxHash(releaseTx.getHash()) :
                rskTxHash;

            logger.debug("[tryGetReleaseInformation] Going to lookup tx to sign {}", actualRskTxHash);

            // [-- Ignore punished transactions] --> this won't be done for now but should be taken into consideration
            // -- Get Real Block where release_requested was emmited
            logger.trace("[tryGetReleaseInformation] Getting release information");
            return Optional.of(releaseCreationInformationGetter.getTxInfoToSign(
                signerVersion,
                actualRskTxHash,
                releaseTx,
                rskTxHash
            ));
        } catch (HSMReleaseCreationInformationException | FederationCantSignException e) {
            String message = String.format(
                "[tryGetReleaseInformation] There was an error trying to process release for BTC tx %s",
                releaseTx.getHash()
            );
            logger.error(message, e);
        } catch (FederatorAlreadySignedException e) {
            logger.info("[tryGetReleaseInformation] {}", e.getMessage());
        }
        return Optional.empty();
    }

    protected void validateTxCanBeSigned(BtcTransaction btcTx) throws FederatorAlreadySignedException, FederationCantSignException {
        try {
            BtcECKey federatorPublicKey = signer.getPublicKey(BTC_KEY_ID.getKeyId()).toBtcKey();
            logger.trace("[validateTxCanBeSigned] Federator public key {}", federatorPublicKey);

            for (int inputIndex = 0; inputIndex < btcTx.getInputs().size(); inputIndex++) {
                TransactionInput txIn = btcTx.getInput(inputIndex);
                Script redeemScript = getRedeemScriptFromInput(txIn);
                Script standardRedeemScript = extractStandardRedeemScript(redeemScript);

                // Check if input is not already signed by the current federator
                logger.trace("[validateTxCanBeSigned] Checking if the input {} is not already signed by the current federator", inputIndex);
                co.rsk.bitcoinj.core.Sha256Hash sigHash = btcTx.hashForSignature(
                        inputIndex,
                        redeemScript,
                        BtcTransaction.SigHash.ALL,
                        false
                );
                if (BridgeUtils.isInputSignedByThisFederator(federatorPublicKey, sigHash, txIn)) {
                    String message = String.format(
                            "Btc tx %s input %d already signed by current federator with public key %s",
                            btcTx.getHashAsString(),
                            inputIndex,
                            federatorPublicKey
                    );
                    throw new FederatorAlreadySignedException(message);
                }

                // Check if any of the observed federations can sign the tx
                logger.trace("[validateTxCanBeSigned] Checking if any of the observed federations can sign the tx input {}", inputIndex);
                observedFederations.stream()
                        .forEach(f -> logger.trace("[validateTxCanBeSigned] federation p2sh redeem script {}", f.getRedeemScript()));
                List<Federation> spendingFedFilter = observedFederations.stream()
                        .filter(f -> (extractDefaultRedeemScript(f)).equals(standardRedeemScript)).collect(Collectors.toList());
                logger.debug("[validateTxCanBeSigned] spendingFedFilter size {}", spendingFedFilter.size());
                if (spendingFedFilter.isEmpty()) {
                    String message = String.format(
                            "Transaction %s can't be signed by any of the observed federations",
                            btcTx.getHash()
                    );
                    throw new FederationCantSignException(message);
                }
            }
        } catch (SignerException e) {
            String message = String.format("[validateTxCanBeSigned] Error validating tx %s, " +
                    "failed to get current federator public key", btcTx.getHashAsString());
            logger.error(message, e);
        }
    }

    protected void signRelease(int signerVersion, ReleaseCreationInformation releaseCreationInformation) {
        try {
            logger.debug("[signRelease] HSM signer version {}", signerVersion);
            logger.debug("[signRelease] Going to sign tx {}", releaseCreationInformation.getInformingRskTxHash());
            logger.trace("[signRelease] Enforce signer requirements");
            releaseRequirementsEnforcer.enforce(signerVersion, releaseCreationInformation);
            SignerMessageBuilder messageBuilder = signerMessageBuilderFactory.buildFromConfig(
                signerVersion,
                releaseCreationInformation
            );
            co.rsk.bitcoinj.core.Context.propagate(new co.rsk.bitcoinj.core.Context(bridgeConstants.getBtcParams()));
            List<byte[]> signatures = new ArrayList<>();
            for (int inputIndex = 0; inputIndex < releaseCreationInformation.getBtcTransaction().getInputs().size(); inputIndex++) {
                SignerMessage messageToSign = messageBuilder.buildMessageForIndex(inputIndex);
                logger.trace("[signRelease] Message to sign: {}", messageToSign.getClass());
                ECKey.ECDSASignature ethSig = signer.sign(BTC_KEY_ID.getKeyId(), messageToSign);
                logger.debug("[signRelease] Message successfully signed");
                BtcECKey.ECDSASignature sig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
                signatures.add(sig.encodeToDER());
            }

            logger.info("[signRelease] Signed Tx {}", releaseCreationInformation.getInformingRskTxHash());
            federatorSupport.addSignature(signatures, releaseCreationInformation.getInformingRskTxHash().getBytes());
        } catch (SignerException e) {
            String message = String.format("Error signing Tx %s", releaseCreationInformation.getInformingRskTxHash());
            logger.error(message, e);
            panicProcessor.panic("btcrelease", message);
        } catch (HSMClientException | SignerMessageBuilderException | ReleaseRequirementsEnforcerException e) {
            logger.error("[signRelease] {}", e.getMessage());
            panicProcessor.panic("btcrelease", e.getMessage());
        } catch (Exception e) {
            String message = String.format(
                "[signRelease] There was an error trying to sign release for BTC tx %s",
                releaseCreationInformation.getBtcTransaction().getHash()
            );
            logger.error(message, e);
            panicProcessor.panic("btcrelease", e.getMessage());
        }
    }

    // Executed when a tx is ready for broadcasting
    public void onBtcRelease(BtcTransaction signedBtcTx) {
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
            logger.info("Broadcasted {} to {} in tx {}", txo.getValue(), destination, signedBtcTx2.getTxId());
        });
    }

    /*
    Received tx inputs are replaced by base inputs without signatures that spend from the given federation.
    This way the tx has the same hash as the one registered in release_requested event topics.
     */
    protected void removeSignaturesFromTransaction(BtcTransaction tx, Federation spendingFed) {
        for (int inputIndex = 0; inputIndex < tx.getInputs().size(); inputIndex++) {
            //Get redeem script for current input
            TransactionInput txInput = tx.getInput(inputIndex);
            Script inputRedeemScript = getRedeemScriptFromInput(txInput);
            logger.trace("[removeSignaturesFromTransaction] input {} scriptSig {}", inputIndex, tx.getInput(inputIndex).getScriptSig());
            logger.trace("[removeSignaturesFromTransaction] input {} redeem script {}", inputIndex, inputRedeemScript);

            txInput.setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(spendingFed, inputRedeemScript));
            logger.debug("[removeSignaturesFromTransaction] Updated input {} scriptSig with base input script that " +
                    "spends from the federation {}", inputIndex, spendingFed.getAddress());
        }
    }

    protected Script extractStandardRedeemScript(Script redeemScript) {
        RedeemScriptParser parser = RedeemScriptParserFactory.get(redeemScript.getChunks());
        return parser.extractStandardRedeemScript();
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
