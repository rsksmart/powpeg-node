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

package co.rsk.federate.signing.hsm.message;

import static co.rsk.federate.signing.HSMField.INPUT;
import static co.rsk.federate.signing.HSMField.OUTPOINT_VALUE;
import static co.rsk.federate.signing.HSMField.SIGHASH_COMPUTATION_MODE;
import static co.rsk.federate.signing.HSMField.TX;
import static co.rsk.federate.signing.HSMField.WITNESS_SCRIPT;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.trie.Trie;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.ethereum.core.TransactionReceipt;
import org.spongycastle.util.encoders.Hex;

public class PowHSMSignerMessage extends SignerMessage {

    private static final String SIGHASH_LEGACY_MODE = "legacy";
    private static final String SIGHASH_SEGWIT_MODE = "segwit";
    private final BtcTransaction btcTransaction;
    private final int inputIndex;
    private final TransactionReceipt txReceipt;
    private final List<Trie> receiptMerkleProof;
    private final Sha256Hash sigHash;
    private final List<Coin> outpointValues;

    public PowHSMSignerMessage(
        BtcTransaction btcTransaction,
        int index,
        TransactionReceipt txReceipt,
        List<Trie> receiptMerkleProof,
        Sha256Hash sigHash,
        List<Coin> outpointValues
    ) {
        this.btcTransaction = btcTransaction;
        this.inputIndex = index;
        this.txReceipt = txReceipt;
        this.receiptMerkleProof = Collections.unmodifiableList(receiptMerkleProof);
        this.sigHash = sigHash;
        this.outpointValues = Collections.unmodifiableList(outpointValues);
    }

    @Override
    public byte[] getBytes() {
        return sigHash.getBytes();
    }

    public String getBtcTransactionSerialized() {
        byte[] serializedTx = btcTransaction.bitcoinSerialize();
        return Hex.toHexString(serializedTx);
    }

    public int getInputIndex() {
        return inputIndex;
    }

    public String getTransactionReceipt() {
        return Hex.toHexString(txReceipt.getEncoded());
    }

    public String[] getReceiptMerkleProof() {
        String[] encodedReceipts = new String[receiptMerkleProof.size()];
        for (int i = 0; i < encodedReceipts.length; i++) {
            encodedReceipts[i] = Hex.toHexString(receiptMerkleProof.get(i).toMessage());
        }
        return encodedReceipts;
    }

    public Sha256Hash getSigHash() {
        return sigHash;
    }

    public JsonNode getMessageToSign(int version) {
        ObjectNode messageToSend = new ObjectMapper().createObjectNode();
        messageToSend.put(TX.getFieldName(), getBtcTransactionSerialized());
        messageToSend.put(INPUT.getFieldName(), inputIndex);
        if (version == HSMVersion.V5.getNumber()) {
            if (hasWitness()) {
                populateWithSegwitValues(messageToSend);
            } else {
                populateWithLegacyValues(messageToSend);
            }
        }
        return messageToSend;
    }

    private void populateWithLegacyValues(ObjectNode messageToSend) {
        messageToSend.put(SIGHASH_COMPUTATION_MODE.getFieldName(), SIGHASH_LEGACY_MODE);
    }

    private void populateWithSegwitValues(ObjectNode messageToSend) {
        messageToSend.put(SIGHASH_COMPUTATION_MODE.getFieldName(), SIGHASH_SEGWIT_MODE);

        long outpointValue = getOutpointValueForInputIndex();
        messageToSend.put(OUTPOINT_VALUE.getFieldName(), outpointValue);

        TransactionWitness witness = btcTransaction.getWitness(inputIndex);
        String encodedWitnessScript = Hex.toHexString(witness.getScriptBytes());
        messageToSend.put(WITNESS_SCRIPT.getFieldName(), encodedWitnessScript);
    }

    private long getOutpointValueForInputIndex() {
        return Optional.of(outpointValues)
            .filter(values -> inputIndex >= 0 && inputIndex < values.size())
            .map(values -> values.get(inputIndex))
            .map(Coin::getValue)
            // this exception is never supposed to be thrown unless a wrong input index is passed
            .orElseThrow(IllegalStateException::new);
    }

    private boolean hasWitness() {
        return btcTransaction.hasWitness();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }

        PowHSMSignerMessage message = (PowHSMSignerMessage) o;
        return this.btcTransaction.equals(message.btcTransaction) &&
            this.inputIndex == message.inputIndex &&
            Arrays.equals(this.txReceipt.getEncoded(), message.txReceipt.getEncoded()) &&
            this.receiptMerkleProof.equals(message.receiptMerkleProof) &&
            this.sigHash.equals(message.sigHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(btcTransaction, inputIndex, txReceipt, receiptMerkleProof);
    }
}
