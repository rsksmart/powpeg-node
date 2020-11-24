package co.rsk.federate.io;

import co.rsk.federate.CoinbaseInformation;
import co.rsk.federate.Proof;
import org.apache.commons.io.FileUtils;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BtcToRskClientFileStorageImpl implements BtcToRskClientFileStorage {

    private final FileStorageInfo storageInfo;

    public BtcToRskClientFileStorageImpl(FileStorageInfo storageInfo) {
        this.storageInfo = storageInfo;
    }

    @Override
    public FileStorageInfo getInfo() {
        return this.storageInfo;
    }

    @Override
    public void write(BtcToRskClientFileData data) throws IOException {
        if (data == null) {
            throw new IOException("Data is null");
        }
        File directory = new File(storageInfo.getPegDirectoryPath());
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create directory " + directory.getAbsolutePath());
        }

        File dataFile = new File(storageInfo.getFilePath());

        byte[] proofsForFile = Proof.encodeProofs(data.getTransactionProofs());
        byte[] coinbasesForFile = this.serializeCoinbaseInformation(data.getCoinbaseInformationMap());

        byte[] encodedData = RLP.encodeList(proofsForFile, coinbasesForFile);

        FileUtils.writeByteArrayToFile(dataFile, encodedData);
    }

    @Override
    public BtcToRskClientFileReadResult read(NetworkParameters networkParameters) throws IOException {
        File file = new File(this.storageInfo.getFilePath());

        if (!file.exists()) {
            return new BtcToRskClientFileReadResult(Boolean.TRUE, new BtcToRskClientFileData());
        }
        return this.readFromRlp(FileUtils.readFileToByteArray(file), networkParameters);
    }

    private BtcToRskClientFileReadResult readFromRlp(byte[] fileData, NetworkParameters networkParameters) {
        BtcToRskClientFileData data = new BtcToRskClientFileData();

        try {
            ArrayList<RLPElement> elements = RLP.decode2(fileData);
            if (elements.isEmpty()) {
                return new BtcToRskClientFileReadResult(Boolean.TRUE, data);
            }
            RLPList rlpList = (RLPList)elements.get(0);
            data.getTransactionProofs().putAll(Proof.deserializeProofs(rlpList.get(0).getRLPData(), networkParameters));
            data.getCoinbaseInformationMap().putAll(deserializeCoinbaseInformation(rlpList.get(1).getRLPData(), networkParameters));
        } catch (Exception e) {
            return new BtcToRskClientFileReadResult(Boolean.FALSE, null);
        }

        return new BtcToRskClientFileReadResult(Boolean.TRUE, data);
    }

    private byte[] serializeCoinbaseInformation(Map<Sha256Hash, CoinbaseInformation> coinbaseInformationMap) {
        int nProof = coinbaseInformationMap.size();
        byte[][] bytes = new byte[nProof][];
        int n = 0;
        for (Sha256Hash blockHash : coinbaseInformationMap.keySet()) {
            bytes[n++] = coinbaseInformationMap.get(blockHash).serializeToRLP();
        }
        return RLP.encodeList(bytes);
    }

    private Map<Sha256Hash, CoinbaseInformation> deserializeCoinbaseInformation(byte[] rlpData, NetworkParameters networkParameters) throws Exception {
        RLPList rlpList = (RLPList) RLP.decode2(rlpData).get(0);
        Map<Sha256Hash, CoinbaseInformation> result = new HashMap<>();

        for (int k = 0; k < rlpList.size(); k++) {
            RLPElement rlpElement = rlpList.get(k);
            CoinbaseInformation coinbaseInformation = CoinbaseInformation.fromRlp(rlpElement.getRLPData(), networkParameters);
            result.put(coinbaseInformation.getBlockHash(), coinbaseInformation);
        }

        return result;
    }
}
