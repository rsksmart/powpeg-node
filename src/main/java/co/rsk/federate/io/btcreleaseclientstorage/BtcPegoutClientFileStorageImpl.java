package co.rsk.federate.io.btcreleaseclientstorage;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.io.FileStorageInfo;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BtcPegoutClientFileStorageImpl implements BtcPegoutClientFileStorage {

    private static final Logger logger = LoggerFactory.getLogger(BtcPegoutClientFileStorageImpl.class);
    private final FileStorageInfo storageInfo;

    public BtcPegoutClientFileStorageImpl(FileStorageInfo storageInfo) {
        this.storageInfo = storageInfo;
    }

    @Override
    public FileStorageInfo getInfo() {
        return this.storageInfo;
    }

    @Override
    public void write(BtcPegoutClientFileData data) throws IOException {
        if (data == null) {
            throw new IOException("Data is null");
        }
        File directory = new File(storageInfo.getPegDirectoryPath());
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create directory " + directory.getAbsolutePath());
        }

        File dataFile = new File(storageInfo.getFilePath());

        byte[] serializedMap = this.serializePegoutHashes(data.getPegoutHashesMap());
        Optional<Keccak256> optionalblockHash = data.getBestBlockHash();
        byte[] serializedBlockHash = RLP.encodeElement(
            optionalblockHash.isPresent() ? optionalblockHash.get().getBytes() : new byte[]{}
        );

        byte[] encodedData = RLP.encodeList(serializedMap, serializedBlockHash);

        FileUtils.writeByteArrayToFile(dataFile, encodedData);
    }

    @Override
    public BtcPegoutClientFileReadResult read() throws IOException {
        File file = new File(this.storageInfo.getFilePath());
        if (!file.exists()) {
            return new BtcPegoutClientFileReadResult(Boolean.TRUE, new BtcPegoutClientFileData());
        }

        return this.readFromRlp(FileUtils.readFileToByteArray(file));
    }

    private BtcPegoutClientFileReadResult readFromRlp(byte[] fileData) {
        BtcPegoutClientFileData data = new BtcPegoutClientFileData();
        if (fileData.length == 0) {
            return new BtcPegoutClientFileReadResult(Boolean.TRUE, data);
        }

        try {
            ArrayList<RLPElement> elements = RLP.decode2(fileData);
            if (elements.isEmpty()) {
                return new BtcPegoutClientFileReadResult(Boolean.TRUE, data);
            }
            RLPList rlpList = (RLPList)elements.get(0);
            if (rlpList.size() == 0) {
                return new BtcPegoutClientFileReadResult(Boolean.TRUE, data);
            }
            // Map
            byte[] mapData = rlpList.get(0).getRLPData();
            RLPList mapList = (RLPList)RLP.decode2(mapData).get(0);
            data.getPegoutHashesMap().putAll(this.deserializePegoutHashes(mapList));
            // Block hash
            if (rlpList.size() == 2) {
                byte[] blockHashData = rlpList.get(1).getRLPData();
                if (blockHashData != null && blockHashData.length > 0) {
                    data.setBestBlockHash(new Keccak256(blockHashData));
                }
            }
        } catch (Exception e) {
            logger.error("[readFromRlp] error trying to file data.", e);
            return new BtcPegoutClientFileReadResult(Boolean.FALSE, null);
        }

        return new BtcPegoutClientFileReadResult(Boolean.TRUE, data);
    }

    private byte[] serializePegoutHashes(Map<Sha256Hash, Keccak256> pegoutHashesMap) {
        int items = pegoutHashesMap.size();
        byte[][] bytes = new byte[items * 2][];
        int n = 0;
        for (Map.Entry<Sha256Hash, Keccak256> entry : pegoutHashesMap.entrySet()) {
            bytes[n] = RLP.encodeElement(entry.getKey().getBytes());
            bytes[n + 1] = RLP.encodeElement(entry.getValue().getBytes());
            n += 2;
        }
        return RLP.encodeList(bytes);
    }

    private Map<Sha256Hash, Keccak256> deserializePegoutHashes(RLPList rlpList) {
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
