package co.rsk.federate;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.bitcoin.BitcoinPeerFactory;
import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.signing.ECDSASigner;
import co.rsk.peg.Bridge;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.StateForFederator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bitcoinj.core.PartialMerkleTree;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Sha256Hash;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Helps the federator communication with the RSK blockchain.
 * @author Oscar Guindzberg
 */
public class FederatorSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(FederatorSupport.class);

    private final Blockchain blockchain;
    private final FedNodeSystemProperties config;

    private final NetworkParameters parameters;

    private final BridgeTransactionSender bridgeTransactionSender;

    private ECDSASigner signer;

    private FederationMember federationMember;
    private RskAddress federatorAddress;

    public FederatorSupport(
            Blockchain blockchain,
            FedNodeSystemProperties config,
            BridgeTransactionSender bridgeTransactionSender) {
        this.blockchain = blockchain;
        this.config = config;

        this.parameters = config.getNetworkConstants().getBridgeConstants().getBtcParams();

        this.bridgeTransactionSender = bridgeTransactionSender;
    }

    public void setMember(FederationMember fedMember) {
        this.federationMember = fedMember;
        this.federatorAddress = new RskAddress(federationMember.getRskPublicKey().getAddress());
    }

    public void setSigner(ECDSASigner signer) {
        this.signer = signer;
    }

    public FederationMember getFederationMember() {
        return this.federationMember;
    }

    public List<PeerAddress> getBitcoinPeerAddresses() throws UnknownHostException {
        return BitcoinPeerFactory.buildBitcoinPeerAddresses(ThinConverter.toOriginalInstance(config.getNetworkConstants().getBridgeConstants().getBtcParamsString()), this.config.getNetworkConstants().getBridgeConstants().getBtcParams().getPort(), this.config.bitcoinPeerAddresses());
    }

    public int getBtcBestBlockChainHeight() {
        BigInteger btcBlockchainBestChainHeight = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT);
        return btcBlockchainBestChainHeight.intValue();
    }

    public int getBtcBlockchainInitialBlockHeight() {
        BigInteger btcBlockchainInitialBlockHeight = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_BTC_BLOCKCHAIN_INITIAL_BLOCK_HEIGHT);
        return btcBlockchainInitialBlockHeight.intValue();
    }

    public Sha256Hash getBtcBlockchainBlockHashAtDepth(int depth) {
        byte[] blockHashBytes = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_BTC_BLOCKCHAIN_BLOCK_HASH_AT_DEPTH, new Object[]{depth});
        return Sha256Hash.wrap(blockHashBytes);
    }

    public Object[] getBtcBlockchainBlockLocator() {
        return this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR);
    }

    public Long getRskBestChainHeight() {
        return blockchain.getBestBlock().getNumber();
    }

    public void sendReceiveHeaders(org.bitcoinj.core.Block[] headers) {
        LOGGER.debug("About to send to the bridge headers from {} to {}", headers[0].getHash(), headers[headers.length - 1].getHash());

        Object[] objectArray = new Object[headers.length];

        for (int i = 0; i < headers.length; i++) {
            objectArray[i] = headers[i].cloneAsHeader().bitcoinSerialize();
        }
        this.bridgeTransactionSender.sendRskTx(federatorAddress, signer, Bridge.RECEIVE_HEADERS, new Object[]{objectArray});
    }

    public Boolean isBtcTxHashAlreadyProcessed(Sha256Hash btcTxHash) {
        return this.bridgeTransactionSender.callTx(federatorAddress, Bridge.IS_BTC_TX_HASH_ALREADY_PROCESSED, new Object[]{btcTxHash.toString()});
    }

    public Long getBtcTxHashProcessedHeight(Sha256Hash btcTxHash) {
        BigInteger btcTxHashProcessedHeight = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_BTC_TX_HASH_PROCESSED_HEIGHT, new Object[]{btcTxHash.toString()});
        return btcTxHashProcessedHeight.longValue();
    }

    public void sendRegisterBtcTransaction(org.bitcoinj.core.Transaction tx, int blockHeight, PartialMerkleTree pmt) {
        LOGGER.debug("About to send to the bridge btc tx hash {}. Block height {}", tx.getWTxId(), blockHeight);

        byte[] txSerialized = tx.bitcoinSerialize();
        byte[] pmtSerialized = pmt.bitcoinSerialize();
        this.bridgeTransactionSender.sendRskTx(federatorAddress, signer, Bridge.REGISTER_BTC_TRANSACTION, txSerialized, blockHeight, pmtSerialized);
    }

    public void sendRegisterCoinbaseTransaction(CoinbaseInformation coinbaseInformation) {
        LOGGER.debug("About to send to the bridge btc coinbase tx hash {}. Block hash {}", coinbaseInformation.getCoinbaseTransaction().getTxId(), coinbaseInformation.getBlockHash());

        byte[] txSerialized = coinbaseInformation.getSerializedCoinbaseTransactionWithoutWitness();
        byte[] pmtSerialized = coinbaseInformation.getPmt().bitcoinSerialize();

        this.bridgeTransactionSender.sendRskTx(federatorAddress, signer,
                Bridge.REGISTER_BTC_COINBASE_TRANSACTION,
                txSerialized, coinbaseInformation.getBlockHash().getBytes(), pmtSerialized,
                coinbaseInformation.getWitnessRoot().getBytes(), coinbaseInformation.getCoinbaseWitnessReservedValue()
                );
    }

    public boolean hasBlockCoinbaseInformed(Sha256Hash blockHash) {
        return this.bridgeTransactionSender.callTx(
                federatorAddress,
                Bridge.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION,
                new Object[] { blockHash.getBytes() });
    }

    public StateForFederator getStateForFederator() {
        byte[] result = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_STATE_FOR_BTC_RELEASE_CLIENT);
        return new StateForFederator(result, this.parameters);
    }

    public List<Utxo> getUtxosAtBlock(long atBlockNumber) {

        String utxosJson = getUtxoJsonAtBlockRcpCall(atBlockNumber);

        LOGGER.trace("utxosJson: {}", utxosJson);

        List<Utxo> utxos = Utxo.parseRLPToActiveFederationUtxos(utxosJson);

        return utxos;

    }

    private String getUtxoJsonAtBlockRcpCall(long atBlockNumber) {
        try {

            URL url = new URL("http://localhost:4450");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            long id = System.currentTimeMillis();
            String bridgeAddress = "0x0000000000000000000000000000000001000006";

            LOGGER.trace("atBlockNumber: {}", atBlockNumber);

            // This storage key is only for newFederationBtcUTXOs.
            String bridgeUtxoStorageKey = "0x00000000000000000000006e657746656465726174696f6e4274635554584f73";

            String requestBody = String.format(
                "{" +
                    "\"jsonrpc\":\"2.0\"," +
                    "\"id\":%d," +
                    "\"method\":\"rsk_getStorageBytesAt\"," +
                    "\"params\":[\"%s\",\"%s\",\"0x%s\"]" +
                "}",
                id, bridgeAddress, bridgeUtxoStorageKey, Long.toHexString(atBlockNumber));

            // Send request
            OutputStream os = connection.getOutputStream();
            os.write(requestBody.getBytes());
            os.flush();

            // Read response
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                // Close connection
                connection.disconnect();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.toString());

                return rootNode.get("result").asText();

            } else {
                System.out.println("RPC request failed with response code: " + responseCode);
            }

            // Close connection
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public void addSignature(List<byte[]> signatures, byte[] rskTxHash) {
        byte[] federatorPublicKeyBytes = federationMember.getBtcPublicKey().getPubKey();
        this.bridgeTransactionSender.sendRskTx(federatorAddress, signer, Bridge.ADD_SIGNATURE, federatorPublicKeyBytes, signatures, rskTxHash);
    }

    public void sendUpdateCollections() {
        this.bridgeTransactionSender.sendRskTx(federatorAddress, signer, Bridge.UPDATE_COLLECTIONS);
    }

    public Address getFederationAddress() {
        return Address.fromBase58(getBtcParams(), this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_FEDERATION_ADDRESS));
    }

    public Integer getFederationSize() {
        BigInteger federationSize = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_FEDERATION_SIZE);
        return federationSize.intValue();
    }

    public Integer getFederationThreshold() {
        BigInteger federationThreshold = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_FEDERATION_THRESHOLD);
        return federationThreshold.intValue();
    }

    public BtcECKey getFederatorPublicKey(int index) {
        byte[] federatorPublicKey = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_FEDERATOR_PUBLIC_KEY, new Object[]{index});
        return BtcECKey.fromPublicOnly(federatorPublicKey);
    }

    public ECKey getFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        byte[] federatorPublicKey = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE, new Object[]{index, keyType.getValue()});
        return ECKey.fromPublicOnly(federatorPublicKey);
    }

    public Instant getFederationCreationTime() {
        BigInteger federationCreationTime = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_FEDERATION_CREATION_TIME);
        return Instant.ofEpochMilli(federationCreationTime.longValue());
    }

    public long getFederationCreationBlockNumber() {
        BigInteger federationCreationBlockNumber = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_FEDERATION_CREATION_BLOCK_NUMBER);
        return federationCreationBlockNumber.longValue();
    }

    public Optional<Address> getRetiringFederationAddress() {
        String addressString = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_RETIRING_FEDERATION_ADDRESS);

        if (addressString.equals("")) {
            return Optional.empty();
        }

        return Optional.of(Address.fromBase58(getBtcParams(), addressString));
    }

    public long getRetiringFederationCreationBlockNumber() {
        BigInteger retiringFederationCreationBlockNumber = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_RETIRING_FEDERATION_CREATION_BLOCK_NUMBER);
        return retiringFederationCreationBlockNumber.longValue();
    }

    public Integer getRetiringFederationSize() {
        BigInteger size = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_RETIRING_FEDERATION_SIZE);
        return size.intValue();
    }

    public Integer getRetiringFederationThreshold() {
        BigInteger threshold = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_RETIRING_FEDERATION_THRESHOLD);
        if (threshold == null) {
            return null;
        }

        return threshold.intValue();
    }

    public BtcECKey getRetiringFederatorPublicKey(int index) {
        byte[] publicKeyBytes = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_RETIRING_FEDERATOR_PUBLIC_KEY, new Object[]{index});

        if (publicKeyBytes == null) {
            return null;
        }

        return BtcECKey.fromPublicOnly(publicKeyBytes);
    }

    public ECKey getRetiringFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        byte[] publicKeyBytes = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE, new Object[]{index, keyType.getValue()});

        if (publicKeyBytes == null) {
            return null;
        }

        return ECKey.fromPublicOnly(publicKeyBytes);
    }

    public Instant getRetiringFederationCreationTime() {
        BigInteger creationTime = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_FEDERATION_CREATION_TIME);
        if (creationTime == null) {
            return null;
        }

        return Instant.ofEpochMilli(creationTime.longValue());
    }

    public int getBtcBlockchainBestChainHeight() {
        BigInteger btcBlockchainBestChainHeight = this.bridgeTransactionSender.callTx(federatorAddress, Bridge.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT);
        return btcBlockchainBestChainHeight.intValue();
    }

    public NetworkParameters getBtcParams() {
        return this.parameters;
    }

    public ActivationConfig.ForBlock getConfigForBestBlock() {
        long bestBlockNumber = blockchain.getBestBlock().getNumber();
        return config.getActivationConfig().forBlock(bestBlockNumber);
    }
}
