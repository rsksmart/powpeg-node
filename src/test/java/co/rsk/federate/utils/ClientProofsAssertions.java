package co.rsk.federate.utils;

import co.rsk.federate.Proof;
import co.rsk.federate.io.BtcToRskClientFileData;
import co.rsk.federate.io.BtcToRskClientFileStorage;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientProofsAssertions {

    public static void assertWTxIdIsInProofsFile(NetworkParameters networkParameters, BtcToRskClientFileStorage btcToRskClientFileStorage, Transaction tx) throws IOException {
        BtcToRskClientFileData fileData = btcToRskClientFileStorage.read(networkParameters).getData();
        Map<Sha256Hash, List<Proof>> transactionProofs = fileData.getTransactionProofs();

        Set<Sha256Hash> txProofsKeySet = transactionProofs.keySet();
        assertEquals(1, txProofsKeySet.size());

        Sha256Hash wTxId = tx.getWTxId();
        Sha256Hash savedWTxId = txProofsKeySet.iterator().next();
        assertEquals(wTxId, savedWTxId);
    }

    public static void assertProofsFileIsEmpty(NetworkParameters networkParameters, BtcToRskClientFileStorage btcToRskClientFileStorage) throws IOException {
        BtcToRskClientFileData fileData = btcToRskClientFileStorage.read(networkParameters).getData();
        Map<Sha256Hash, List<Proof>> transactionProofs = fileData.getTransactionProofs();

        Set<Sha256Hash> txProofsKeySet = transactionProofs.keySet();
        assertEquals(0, txProofsKeySet.size());
    }
}
