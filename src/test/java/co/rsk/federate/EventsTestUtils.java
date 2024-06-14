package co.rsk.federate;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.pegin.RejectedPeginReason;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.ethereum.core.CallTransaction;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;

public final class EventsTestUtils {

    private EventsTestUtils() {
    }

    public static LogInfo createPegoutTransactionCreatedLog(Sha256Hash pegoutBtcTxHash,
        byte[] serializedOutpointValues) {
        CallTransaction.Function pegoutTransactionCreatedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();
        byte[] pegoutTransactionCreatedSignatureTopic = pegoutTransactionCreatedEvent.encodeSignatureLong();
        List<DataWord> topics = new ArrayList<>();
        topics.add(
            DataWord.valueOf(pegoutTransactionCreatedSignatureTopic));
        topics.add(DataWord.valueOf(pegoutBtcTxHash.getBytes()));

        byte[] pegoutTransactionCreatedEncodedData = pegoutTransactionCreatedEvent.encodeEventData(
            serializedOutpointValues);

        return new LogInfo(
            PrecompiledContracts.BRIDGE_ADDR.getBytes(),
            topics, pegoutTransactionCreatedEncodedData);
    }

    public static LogInfo createBatchPegoutCreatedLog(Sha256Hash pegoutBtcTxHash,
        List<Keccak256> pegoutRequestRskTxHashes) {
        CallTransaction.Function batchPegoutCreatedEvent = BridgeEvents.BATCH_PEGOUT_CREATED.getEvent();
        byte[] batchPegoutEventSignatureTopic = batchPegoutCreatedEvent.encodeSignatureLong();
        List<DataWord> topics = new ArrayList<>();
        topics.add(
            DataWord.valueOf(batchPegoutEventSignatureTopic));
        topics.add(DataWord.valueOf(pegoutBtcTxHash.getBytes()));

        byte[] encodedData = batchPegoutCreatedEvent.encodeEventData(
            serializeRskTxHashes(pegoutRequestRskTxHashes));

        return new LogInfo(PrecompiledContracts.BRIDGE_ADDR.getBytes(),
            topics, encodedData);
    }

    public static LogInfo createReleaseRequestedLog(Keccak256 pegoutRskTxHash,
        Sha256Hash pegoutBtcTxHash,
        Coin amount) {
        CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();

        byte[] releaseRequestedSignatureTopic = releaseRequestedEvent.encodeSignatureLong();
        List<DataWord> topics = new ArrayList<>();
        topics.add(DataWord.valueOf(releaseRequestedSignatureTopic));
        topics.add(DataWord.valueOf(pegoutRskTxHash.getBytes()));
        topics.add(DataWord.valueOf(pegoutBtcTxHash.getBytes()));

        byte[] encodedData = releaseRequestedEvent.encodeEventData(amount.getValue());

        return new LogInfo(PrecompiledContracts.BRIDGE_ADDR.getBytes(),
            topics, encodedData);
    }

    public static LogInfo createUpdateCollectionsLog(RskAddress senderAddress) {
        CallTransaction.Function updateCollectionsEvent = BridgeEvents.UPDATE_COLLECTIONS.getEvent();

        byte[] updateCollectionsSignatureTopic = updateCollectionsEvent.encodeSignatureLong();
        List<DataWord> topics = new ArrayList<>();
        topics.add(DataWord.valueOf(updateCollectionsSignatureTopic));

        byte[] encodedData = updateCollectionsEvent.encodeEventData(senderAddress.toString());

        return new LogInfo(PrecompiledContracts.BRIDGE_ADDR.getBytes(),
            topics, encodedData);
    }

    public static LogInfo creatRejectedPeginLog(Sha256Hash pegoutBtcTxHash, RejectedPeginReason reason) {
        CallTransaction.Function rejectedPeginEvent = BridgeEvents.REJECTED_PEGIN.getEvent();

        byte[] rejectedPeginSignatureTopic = rejectedPeginEvent.encodeSignatureLong();
        List<DataWord> topics = new ArrayList<>();
        topics.add(DataWord.valueOf(rejectedPeginSignatureTopic));
        topics.add(DataWord.valueOf(pegoutBtcTxHash.getBytes()));

        byte[] encodedData = rejectedPeginEvent.encodeEventData(reason.getValue());

        return new LogInfo(PrecompiledContracts.BRIDGE_ADDR.getBytes(),
            topics, encodedData);
    }

    /*
    TODO: Remove this method once {@link co.rsk.peg.utils.BridgeEventLoggerImpl#serializeRskTxHashes(List<Keccak256> rskTxHashes)} is moved to a util class
     */
    private static byte[] serializeRskTxHashes(List<Keccak256> rskTxHashes) {
        List<byte[]> rskTxHashesList = rskTxHashes.stream()
            .map(Keccak256::getBytes)
            .collect(Collectors.toList());
        int rskTxHashesLength = rskTxHashesList.stream().mapToInt(key -> key.length).sum();

        byte[] serializedRskTxHashes = new byte[rskTxHashesLength];
        int copyPos = 0;
        for (byte[] rskTxHash : rskTxHashesList) {
            System.arraycopy(rskTxHash, 0, serializedRskTxHashes, copyPos, rskTxHash.length);
            copyPos += rskTxHash.length;
        }

        return serializedRskTxHashes;
    }
}
