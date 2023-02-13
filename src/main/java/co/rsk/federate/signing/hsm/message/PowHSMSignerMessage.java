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

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.trie.Trie;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.ethereum.core.TransactionReceipt;
import org.spongycastle.util.encoders.Hex;

public class PowHSMSignerMessage extends SignerMessage {
    private final BtcTransaction btcTransaction;
    private final int inputIndex;
    private final TransactionReceipt txReceipt;
    private final List<Trie> receiptMerkleProof;
    private final Sha256Hash sigHash;

    public PowHSMSignerMessage(BtcTransaction btcTransaction,
                               int index,
                               TransactionReceipt txReceipt,
                               List<Trie> receiptMerkleProof,
                               Sha256Hash sigHash
    ) {
        this.btcTransaction = btcTransaction;
        this.inputIndex = index;
        this.txReceipt = txReceipt;
        this.receiptMerkleProof = receiptMerkleProof;
        this.sigHash = sigHash;
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
        for (int i=0; i<encodedReceipts.length; i++) {
            encodedReceipts[i] = Hex.toHexString(receiptMerkleProof.get(i).toMessage());
        }
        return encodedReceipts;
    }

    public Sha256Hash getSigHash() {
        return sigHash;
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
