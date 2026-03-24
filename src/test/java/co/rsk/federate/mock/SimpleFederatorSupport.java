package co.rsk.federate.mock;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.config.TestSystemProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationMember;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.PartialMerkleTree;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.ethereum.crypto.ECKey;

import static co.rsk.peg.federation.FederationChangeResponseCode.FEDERATION_NON_EXISTENT;

/**
 * Created by ajlopez on 6/2/2016.
 */
public class SimpleFederatorSupport extends FederatorSupport {
    private Federation federation;
    private Block[] headers;
    private int sendReceiveHeadersInvocations = 0;
    private final List<TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction = new ArrayList<>();
    private int height = 0;
    private Object[] locator;
    private Sha256Hash[] blockHashes;
    private Long bestChainHeight = 1L;
    private int initialChainHeight = 0;

    public SimpleFederatorSupport() {
        super(null, new TestSystemProperties(), null);
    }

    @Override
    public int getBtcBlockchainBestChainHeight() {
        return height;
    }

    public void setBtcBlockchainBestChainHeight(int h) {
        height = h;
    }

    @Override
    public int getBtcBlockchainInitialBlockHeight() {
        return initialChainHeight;
    }

    public void setBtcBlockchainInitialBlockHeight(int h) {
        initialChainHeight = h;
    }

    @Override
    public Sha256Hash getBtcBlockchainBlockHashAtDepth(int depth) {
        return blockHashes[depth];
    }

    public void setBlockHashes(Sha256Hash[] hashes) {
        blockHashes = hashes;
    }

    @Override
    public Object[] getBtcBlockchainBlockLocator() {
        return this.locator;
    }

    public void setBtcBlockchainBlockLocator(Object[] locator) {
        this.locator = locator;
    }

    @Override
    public List<PeerAddress> getBitcoinPeerAddresses() {
        return null;
    }

    public void setFederation(Federation federation) {
        this.federation = federation;
    }

    @Override
    public Address getFederationAddress() {
        return federation.getAddress();
    }

    @Override
    public Integer getFederationSize() {
        return federation.getSize();
    }

    @Override
    public ECKey getFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        FederationMember member = federation.getMembers().get(index);
        return switch (keyType) {
            case BTC -> ECKey.fromPublicOnly(member.getBtcPublicKey().getPubKey());
            case RSK -> member.getRskPublicKey();
            case MST -> member.getMstPublicKey();
        };
    }
    @Override
    public Instant getFederationCreationTime() {
        return federation.getCreationTime();
    }

    @Override
    public long getFederationCreationBlockNumber() {
        return federation.getCreationBlockNumber();
    }

    @Override
    public NetworkParameters getBtcParams() {
        return federation.getBtcParams();
    }

    @Override
    public Integer getRetiringFederationSize() {
        return FEDERATION_NON_EXISTENT.getCode();
    }

    @Override
    public Optional<Integer> getProposedFederationSize() {
        return Optional.empty();
    }

    @Override
    public void sendReceiveHeaders(Block[] headers) {
        if (this.headers == null) {
            this.headers = headers;
        } else {
            // concatenate existing headers and new headers
            Block[] result = new Block[this.headers.length + headers.length];
            System.arraycopy(this.headers, 0, result, 0, this.headers.length);
            System.arraycopy(headers, 0, result, this.headers.length, headers.length);
            this.headers = result;
        }
        sendReceiveHeadersInvocations++;
    }

    public Block[] getReceiveHeaders() {
        return this.headers;
    }

    public int getSendReceiveHeadersInvocations() {
        return sendReceiveHeadersInvocations;
    }

    @Override
    public void sendRegisterBtcTransaction(Transaction tx, int blockHeight, PartialMerkleTree pmt) {
        TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction = new TransactionSentToRegisterBtcTransaction();

        txSentToRegisterBtcTransaction.tx = tx;
        txSentToRegisterBtcTransaction.blockHeight = blockHeight;
        txSentToRegisterBtcTransaction.pmt = pmt;

        txsSentToRegisterBtcTransaction.add(txSentToRegisterBtcTransaction);
    }

    @Override
    public void addSignature(List<byte[]> signatures, byte[] rskTxHash) {}

    @Override
    public void sendUpdateCollections() {}

    @Override
    public Boolean isBtcTxHashAlreadyProcessed(Sha256Hash btcTxHash) {
        return txsSentToRegisterBtcTransaction.contains(btcTxHash);
    }

    @Override
    public Long getBtcTxHashProcessedHeight(Sha256Hash btcTxHash) {
        return txsSentToRegisterBtcTransaction.contains(btcTxHash) ? 1L : -1L;
    }

    @Override
    public Long getRskBestChainHeight() {
        return this.bestChainHeight;
    }

    public void setRskBestChainHeight(Long height) {
        this.bestChainHeight = height;
    }

    public List<TransactionSentToRegisterBtcTransaction> getTxsSentToRegisterBtcTransaction() {
        return txsSentToRegisterBtcTransaction;
    }

    public static class TransactionSentToRegisterBtcTransaction {
        public Transaction tx;
        public int blockHeight;
        public PartialMerkleTree pmt;
    }
}
