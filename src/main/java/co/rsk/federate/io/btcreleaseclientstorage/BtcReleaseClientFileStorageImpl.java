package co.rsk.federate.io.btcreleaseclientstorage;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.io.FileStorageInfo;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

public class BtcReleaseClientFileStorageImpl implements BtcReleaseClientFileStorage {

    private final FileStorageInfo storageInfo;

    public BtcReleaseClientFileStorageImpl(FileStorageInfo storageInfo) {
        this.storageInfo = storageInfo;
    }

    @Override
    public FileStorageInfo getInfo() {
        return this.storageInfo;
    }

    @Override
    public void write(BtcReleaseClientFileData data) throws IOException {
        if (data == null) {
            throw new IOException("Data is null");
        }
        File directory = new File(storageInfo.getPegDirectoryPath());
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create directory " + directory.getAbsolutePath());
        }

        File dataFile = new File(storageInfo.getFilePath());

        byte[] encodedData = this.serializeReleaseHashes(data.getReleaseHashesMap());

        FileUtils.writeByteArrayToFile(dataFile, encodedData);
    }

    @Override
    public BtcReleaseClientFileReadResult read(NetworkParameters networkParameters)
        throws IOException {
        File file = new File(this.storageInfo.getFilePath());

        if (!file.exists()) {
            return new BtcReleaseClientFileReadResult(Boolean.TRUE, new BtcReleaseClientFileData());
        }
        return this.readFromRlp(FileUtils.readFileToByteArray(file));
    }

    private BtcReleaseClientFileReadResult readFromRlp(byte[] fileData) {
        BtcReleaseClientFileData data = new BtcReleaseClientFileData();
        if (fileData.length == 0) {
            return new BtcReleaseClientFileReadResult(Boolean.TRUE, data);
        }

        try {
            RLPList rlpList = RLP.decodeList(fileData);
            if (rlpList.size() == 0) {
                return new BtcReleaseClientFileReadResult(Boolean.TRUE, data);
            }
            data.getReleaseHashesMap().putAll(this.deserializeReleaseHashes(rlpList));
        } catch (Exception e) {
            return new BtcReleaseClientFileReadResult(Boolean.FALSE, null);
        }

        return new BtcReleaseClientFileReadResult(Boolean.TRUE, data);
    }

    private byte[] serializeReleaseHashes(Map<Sha256Hash, Keccak256> releaseHashesMap) {
        int items = releaseHashesMap.size();
        byte[][] bytes = new byte[items * 2][];
        int n = 0;
        for (Map.Entry<Sha256Hash, Keccak256> entry : releaseHashesMap.entrySet()) {
            bytes[n] = RLP.encodeElement(entry.getKey().getBytes());
            bytes[n + 1] = RLP.encodeElement(entry.getValue().getBytes());
            n += 2;
        }
        return RLP.encodeList(bytes);
    }

    private Map<Sha256Hash, Keccak256> deserializeReleaseHashes(RLPList rlpList) {
        Map<Sha256Hash, Keccak256> result = new HashMap<>();

        for (int k = 0; k < rlpList.size(); k += 2) {
            byte[] e1 = rlpList.get(k).getRLPData();
            byte[] e2 = rlpList.get(k + 1).getRLPData();
            if (e1 != null && e2 != null) {
                result.put(Sha256Hash.wrap(e1), new Keccak256(e2));
            }
        }

        return result;
    }
}
