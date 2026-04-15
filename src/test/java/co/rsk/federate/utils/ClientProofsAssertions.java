package co.rsk.federate.utils;

import co.rsk.federate.BtcToRskClient;
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

import static org.junit.jupiter.api.Assertions.*;

public class ClientProofsAssertions {

    public static void assertWTxIdIsInTxsToBeSentMap(BtcToRskClient btcToRskClient, Transaction tx) {
        assertTrue(btcToRskClient.getTransactionsToSendToRsk().containsKey(tx.getWTxId()));
    }

    public static void assertWTxIdIsInProofsFile(NetworkParameters networkParameters, BtcToRskClientFileStorage btcToRskClientFileStorage, Transaction tx) throws IOException {
        Set<Sha256Hash> txProofsKeySet = getTxProofsKeySet(networkParameters, btcToRskClientFileStorage);
        assertTrue(txProofsKeySet.contains(tx.getWTxId()));
    }

    public static void assertWTxIdIsNotInProofsFile(NetworkParameters networkParameters, BtcToRskClientFileStorage btcToRskClientFileStorage, Transaction tx) throws IOException {
        Set<Sha256Hash> txProofsKeySet = getTxProofsKeySet(networkParameters, btcToRskClientFileStorage);
        assertFalse(txProofsKeySet.contains(tx.getWTxId()));
    }

    public static void assertProofsFileIsEmpty(NetworkParameters networkParameters, BtcToRskClientFileStorage btcToRskClientFileStorage) throws IOException {
        Set<Sha256Hash> txProofsKeySet = getTxProofsKeySet(networkParameters, btcToRskClientFileStorage);
        assertEquals(0, txProofsKeySet.size());
    }

    private static Set<Sha256Hash> getTxProofsKeySet(NetworkParameters networkParameters, BtcToRskClientFileStorage btcToRskClientFileStorage) throws IOException {
        BtcToRskClientFileData fileData = btcToRskClientFileStorage.read(networkParameters).getData();
        Map<Sha256Hash, List<Proof>> transactionProofs = fileData.getTransactionProofs();

        return transactionProofs.keySet();
    }
}
