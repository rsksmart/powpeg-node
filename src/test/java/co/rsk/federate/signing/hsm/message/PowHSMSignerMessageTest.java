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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.TransactionReceipt;
import org.junit.jupiter.api.Test;

class PowHSMSignerMessageTest {

    @Test
    void equality() {
        String rawTx1 = "020000000001017001d967a340069c0b169fcbeb9cb6e0d78a27c94a41acbce762abc695aefab10000000017160014c" +
                "fa63de9979e2a8005e6cb516b86202860ff3971ffffffff0200c2eb0b0000000017a914291a7ddc558810708149a731f39cd3c3" +
                "a8782cfd870896e1110000000017a91425a2e67511a0207c4387ce8d3eeef498a4782e64870247304402207e0615f440bbc5035" +
                "1fb5d8839b3fae6c74f652c9ffc9291008f4ea39f9565980220354c734511a0560367b300eecb1a7472317a995462622e06ee91" +
                "cbe0517c17e1012102e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea395400000000";
        String rawTx2 = "0200000001ba47c93a9c8bac4a305ca5e0f8a9a81d1972e06c20fdb0f62ac9960bd48529eb000000006a47304402207" +
                "ce2c6749019d78c9e7a39a82a08b36956fd05edbb8e820eef5946dc6008c5d00220024a1b6261a8afd9ad8c8888d5fb8f9bc75b" +
                "c273100abaa68dac225fadcf2c82012103efa4762ccc1358b72f597d002b7fd1cd58cd05db34fe9fa63e43634acf200927fffff" +
                "fff0200c2eb0b000000001976a914c4f8ff441f41bcc771519dc5ceb9e5a1ee058f7b88ac6cb0eb0b000000001976a9146a6ba0b" +
                "567d75a449d158fe2d3dacc53f58b246688ac00000000";
        BtcTransaction btcTx1 = new BtcTransaction(RegTestParams.get(), Hex.decode(rawTx1));
        BtcTransaction btcTx2 = new BtcTransaction(RegTestParams.get(), Hex.decode(rawTx2));
        int inputIndex = 0;
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 1;
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getHash()).thenReturn(new Keccak256(bytes));
        TransactionReceipt txReceipt = new TransactionReceipt();
        List<Trie> receiptMerkleProof = new ArrayList<>();
        receiptMerkleProof.add(new Trie());
        Sha256Hash sigHash = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");

        SignerMessage m1 = new PowHSMSignerMessage(btcTx1, inputIndex, txReceipt, receiptMerkleProof, sigHash);
        SignerMessage m2 = new PowHSMSignerMessage(btcTx1, inputIndex, txReceipt, receiptMerkleProof, sigHash);
        SignerMessage m3 = new PowHSMSignerMessage(btcTx2, inputIndex, txReceipt, receiptMerkleProof, sigHash);

        assertEquals(m1, m2);
        assertNotSame(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertNotEquals(m1, m3);
        assertNotEquals(m1.hashCode(), m3.hashCode());
        assertNotEquals(m2, m3);
        assertNotEquals(m2.hashCode(), m3.hashCode());
    }

    @Test
    void getBtcTransactionSerialized() {
        String rawTx = "020000000001017001d967a340069c0b169fcbeb9cb6e0d78a27c94a41acbce762abc695aefab10000000017160014c" +
                "fa63de9979e2a8005e6cb516b86202860ff3971ffffffff0200c2eb0b0000000017a914291a7ddc558810708149a731f39cd3c3" +
                "a8782cfd870896e1110000000017a91425a2e67511a0207c4387ce8d3eeef498a4782e64870247304402207e0615f440bbc5035" +
                "1fb5d8839b3fae6c74f652c9ffc9291008f4ea39f9565980220354c734511a0560367b300eecb1a7472317a995462622e06ee91" +
                "cbe0517c17e1012102e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea395400000000";
        BtcTransaction btcTx = new BtcTransaction(RegTestParams.get(), Hex.decode(rawTx));
        int inputIndex = 0;
        BlockHeader blockHeader = mock(BlockHeader.class);
        TransactionReceipt txReceipt = new TransactionReceipt();
        List<Trie> receiptMerkleProof = new ArrayList<>();
        receiptMerkleProof.add(new Trie());
        Sha256Hash sigHash = Sha256Hash.ZERO_HASH;
        SignerMessage message = new PowHSMSignerMessage(btcTx, inputIndex, txReceipt, receiptMerkleProof, sigHash);

        String txSerialized = ((PowHSMSignerMessage) message).getBtcTransactionSerialized();

        assertEquals(Hex.toHexString(btcTx.bitcoinSerialize()), txSerialized);
        assertNotSame(btcTx.bitcoinSerialize(), txSerialized);
    }

    @Test
    void getInputindex() {
        String rawTx = "020000000001017001d967a340069c0b169fcbeb9cb6e0d78a27c94a41acbce762abc695aefab10000000017160014c" +
                "fa63de9979e2a8005e6cb516b86202860ff3971ffffffff0200c2eb0b0000000017a914291a7ddc558810708149a731f39cd3c3" +
                "a8782cfd870896e1110000000017a91425a2e67511a0207c4387ce8d3eeef498a4782e64870247304402207e0615f440bbc5035" +
                "1fb5d8839b3fae6c74f652c9ffc9291008f4ea39f9565980220354c734511a0560367b300eecb1a7472317a995462622e06ee91" +
                "cbe0517c17e1012102e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea395400000000";
        BtcTransaction btcTx = new BtcTransaction(RegTestParams.get(), Hex.decode(rawTx));
        int inputIndex = 2;
        BlockHeader blockHeader = mock(BlockHeader.class);
        TransactionReceipt txReceipt = new TransactionReceipt();
        List<Trie> receiptMerkleProof = new ArrayList<>();
        receiptMerkleProof.add(new Trie());
        Sha256Hash sigHash = Sha256Hash.ZERO_HASH;
        SignerMessage message = new PowHSMSignerMessage(btcTx, inputIndex, txReceipt, receiptMerkleProof, sigHash);

        assertEquals(inputIndex, ((PowHSMSignerMessage) message).getInputIndex());
    }

    @Test
    void getTransactionReceiptReceipt() {
        String rawTx = "020000000001017001d967a340069c0b169fcbeb9cb6e0d78a27c94a41acbce762abc695aefab10000000017160014c" +
                "fa63de9979e2a8005e6cb516b86202860ff3971ffffffff0200c2eb0b0000000017a914291a7ddc558810708149a731f39cd3c3" +
                "a8782cfd870896e1110000000017a91425a2e67511a0207c4387ce8d3eeef498a4782e64870247304402207e0615f440bbc5035" +
                "1fb5d8839b3fae6c74f652c9ffc9291008f4ea39f9565980220354c734511a0560367b300eecb1a7472317a995462622e06ee91" +
                "cbe0517c17e1012102e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea395400000000";
        BtcTransaction btcTx = new BtcTransaction(RegTestParams.get(), Hex.decode(rawTx));
        int inputIndex = 0;
        BlockHeader blockHeader = mock(BlockHeader.class);
        TransactionReceipt txReceipt = new TransactionReceipt();
        List<Trie> receiptMerkleProof = new ArrayList<>();
        receiptMerkleProof.add(new Trie());
        Sha256Hash sigHash = Sha256Hash.ZERO_HASH;
        SignerMessage message = new PowHSMSignerMessage(btcTx, inputIndex, txReceipt, receiptMerkleProof, sigHash);

        String receipt = ((PowHSMSignerMessage) message).getTransactionReceipt();

        assertEquals(Hex.toHexString(txReceipt.getEncoded()), receipt);
    }

    @Test
    void getReceiptMerkleProof() {
        String rawTx = "020000000001017001d967a340069c0b169fcbeb9cb6e0d78a27c94a41acbce762abc695aefab10000000017160014c" +
                "fa63de9979e2a8005e6cb516b86202860ff3971ffffffff0200c2eb0b0000000017a914291a7ddc558810708149a731f39cd3c3" +
                "a8782cfd870896e1110000000017a91425a2e67511a0207c4387ce8d3eeef498a4782e64870247304402207e0615f440bbc5035" +
                "1fb5d8839b3fae6c74f652c9ffc9291008f4ea39f9565980220354c734511a0560367b300eecb1a7472317a995462622e06ee91" +
                "cbe0517c17e1012102e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea395400000000";
        BtcTransaction btcTx = new BtcTransaction(RegTestParams.get(), Hex.decode(rawTx));
        int inputIndex = 0;
        BlockHeader blockHeader = mock(BlockHeader.class);
        TransactionReceipt txReceipt = new TransactionReceipt();
        List<Trie> receiptMerkleProof = new ArrayList<>();
        receiptMerkleProof.add(new Trie());
        receiptMerkleProof.add(new Trie());
        Sha256Hash sigHash = Sha256Hash.ZERO_HASH;
        SignerMessage message = new PowHSMSignerMessage(btcTx, inputIndex, txReceipt, receiptMerkleProof, sigHash);

        String[] encodedReceipts = new String[receiptMerkleProof.size()];
        for (int i=0; i<encodedReceipts.length; i++) {
            encodedReceipts[i] = Hex.toHexString(receiptMerkleProof.get(i).toMessage());
        }

        String[] receipts = ((PowHSMSignerMessage) message).getReceiptMerkleProof();

        assertArrayEquals(encodedReceipts, receipts);
        assertNotSame(encodedReceipts, receipts);
    }

    @Test
    void getSigHash() {
        String rawTx = "020000000001017001d967a340069c0b169fcbeb9cb6e0d78a27c94a41acbce762abc695aefab10000000017160014c" +
                "fa63de9979e2a8005e6cb516b86202860ff3971ffffffff0200c2eb0b0000000017a914291a7ddc558810708149a731f39cd3c3" +
                "a8782cfd870896e1110000000017a91425a2e67511a0207c4387ce8d3eeef498a4782e64870247304402207e0615f440bbc5035" +
                "1fb5d8839b3fae6c74f652c9ffc9291008f4ea39f9565980220354c734511a0560367b300eecb1a7472317a995462622e06ee91" +
                "cbe0517c17e1012102e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea395400000000";
        BtcTransaction btcTx = new BtcTransaction(RegTestParams.get(), Hex.decode(rawTx));
        int inputIndex = 0;
        TransactionReceipt txReceipt = new TransactionReceipt();
        List<Trie> receiptMerkleProof = new ArrayList<>();
        receiptMerkleProof.add(new Trie());
        Sha256Hash sigHash = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");
        SignerMessage message = new PowHSMSignerMessage(btcTx, inputIndex, txReceipt, receiptMerkleProof, sigHash);

        assertEquals(sigHash, ((PowHSMSignerMessage) message).getSigHash());
    }
}
