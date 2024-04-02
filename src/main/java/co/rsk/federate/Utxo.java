package co.rsk.federate;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class Utxo {

    private static final Logger logger = LoggerFactory.getLogger("Utxo");

    private String btcTxHash;
    private int btcTxOutputIndex;
    private BigInteger valueInSatoshis;

    public Utxo(String btcTxHash, int btcTxOutputIndex, BigInteger valueInSatoshis) {
        this.btcTxHash = btcTxHash;
        this.btcTxOutputIndex = btcTxOutputIndex;
        this.valueInSatoshis = valueInSatoshis;
    }

    public String getBtcTxHash() {
        return btcTxHash;
    }

    public int getBtcTxOutputIndex() {
        return btcTxOutputIndex;
    }

    public BigInteger getValueInSatoshis() {
        return valueInSatoshis;
    }

    public static List<Utxo> parseRLPToActiveFederationUtxos(String rlpHex) {

        logger.trace("rlpHex: {}", rlpHex);

        List<Utxo> activeFederationUtxos = new ArrayList<>();

        if (rlpHex.startsWith("0x")) {
            rlpHex = rlpHex.substring(2);
        }

        logger.trace("rlpHex without 0x: {}", rlpHex);

        byte[] rlpBytes = Hex.decode(rlpHex);
        ArrayList<RLPElement> rlpActiveFederationUtxosList = RLP.decode2(rlpBytes);

        logger.trace("rlpActiveFederationUtxosList: {}", rlpActiveFederationUtxosList);

        logger.trace("RLP.decodeList(rlpBytes: {}", RLP.decodeList(rlpBytes));

        for (RLPElement rlpElement : rlpActiveFederationUtxosList) {
            byte[] utxo = rlpElement.getRLPData();

            byte[] valueBuffer = new byte[15];
            System.arraycopy(utxo, 0, valueBuffer, 0, 15);
            reverseArray(valueBuffer);
            BigInteger valueInSatoshis = new BigInteger(bytesToHex(valueBuffer), 16);

            byte[] btcTxHash = new byte[32];
            System.arraycopy(utxo, 70, btcTxHash, 0, 32);
            String btcTxHashString = bytesToHex(btcTxHash);

            int btcTxOutputIndex = Integer.parseInt(bytesToHex(new byte[]{utxo[134]}), 16);

            activeFederationUtxos.add(new Utxo(btcTxHashString, btcTxOutputIndex, valueInSatoshis));
        }

        logger.trace("activeFederationUtxos: {}", activeFederationUtxos);

        return activeFederationUtxos;
    }

    private static void reverseArray(byte[] array) {
        int i = 0;
        int j = array.length - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte aByte : bytes) {
            String hex = Integer.toHexString(0xff & aByte);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Override
    public String toString() {
        return "Utxo{" +
                "btcTxHash='" + btcTxHash + '\'' +
                ", btcTxOutputIndex=" + btcTxOutputIndex +
                ", valueInSatoshis=" + valueInSatoshis +
                '}';
    }
}
