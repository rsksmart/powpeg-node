package co.rsk.federate.util;

import co.rsk.federate.Proof;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.LevelDBBlockStore;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletExtension;
import org.bitcoinj.wallet.WalletProtobufSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper class to read a bitcoinj wallet
 */
public class FederatorWalletReader {
    public static void main(String[] args) throws Exception {
        NetworkParameters params = RegTestParams.get();
        new Context(params);
        String databaseDir = "node1";
        File federatorDir = new File("/Users/oscar/codigo/rootstock/rootstockJ/" + databaseDir + "/peg/");

        System.out.println("***************************************** Wallet *****************************************");
        Wallet wallet = loadWallet(federatorDir, params);
        System.out.println(wallet);
        System.out.println();

        System.out.println("***************************************** BlockChain *****************************************");
        BlockChain blockChain = loadBlockchain(federatorDir, params);
        System.out.println("blockChain.getBestChainHeight() = " + blockChain.getBestChainHeight());
        System.out.println("blockChain.getChainHead() = " + blockChain.getChainHead());
        System.out.println();

        System.out.println("***************************************** Proofs *****************************************");
        Map<Sha256Hash, List<Proof>> proofs = loadProofs(federatorDir, params);
        int zeroProofs = 0;
        int oneProof = 0;
        int twoOrMoreProofs = 0;
        for (Sha256Hash sha256Hash : proofs.keySet()) {
            if (proofs.get(sha256Hash).size() == 0) {
                zeroProofs++;
            }
            if (proofs.get(sha256Hash).size() == 1) {
                oneProof++;
            }
            if (proofs.get(sha256Hash).size() > 1) {
                twoOrMoreProofs++;
            }
        }
        System.out.println("zeroProofs = " + zeroProofs);
        System.out.println("oneProof = " + oneProof);
        System.out.println("twoOrMoreProofs = " + twoOrMoreProofs);
    }

    private static Wallet loadWallet(File federatorDir, NetworkParameters params) throws Exception {
        Wallet wallet;
        FileInputStream walletStream = new FileInputStream(new File(federatorDir, "BtcToRskClient.wallet"));
        try {
            List<WalletExtension> extensions = new ArrayList<>();
            WalletExtension[] extArray = extensions.toArray(new WalletExtension[extensions.size()]);
            Protos.Wallet proto = WalletProtobufSerializer.parseToProto(walletStream);
            WalletProtobufSerializer serializer = new WalletProtobufSerializer();
            wallet = serializer.readWallet(params, extArray, proto);

        } finally {
            walletStream.close();
        }
        return wallet;
    }

    private static Map<Sha256Hash, List<Proof>> loadProofs(File federatorDir, NetworkParameters parameters) throws Exception {
        return Proof.deserializeProofs(Files.readAllBytes(new File(federatorDir, "BtcToRskClient.proofs").toPath()), parameters);
    }

    private static BlockChain loadBlockchain(File federatorDir, NetworkParameters params) throws Exception {
        BlockStore store = new LevelDBBlockStore(Context.get(), new File(federatorDir, "chain"));
        return new BlockChain(params, store);
    }


}