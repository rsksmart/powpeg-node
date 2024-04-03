package co.rsk.federate;

import co.rsk.util.HexUtils;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class Utxo {

    private String btcTxHash;
    private int btcTxOutputIndex;
    private long valueInSatoshis;

    public Utxo(String btcTxHash, int btcTxOutputIndex, long valueInSatoshis) {
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

    public long getValueInSatoshis() {
        return valueInSatoshis;
    }

    public static List<Utxo> parseRLPToActiveFederationUtxos(String rlpHex) {

        List<Utxo> activeFederationUtxos = new ArrayList<>();

        if (rlpHex.startsWith("0x")) {
            rlpHex = rlpHex.substring(2);
        }

        byte[] rlpBytes = Hex.decode(rlpHex);

        RLPList rlpActiveFederationUtxosList = (RLPList) RLP.decode2(rlpBytes).get(0);

        for (int i = 0; i < rlpActiveFederationUtxosList.size(); i++) {

            RLPElement rlpElement = rlpActiveFederationUtxosList.get(i);
            byte[] utxoBytes = rlpElement.getRLPData();

            long valueInSatoshis = getValueInSatoshis(utxoBytes);

            String btcTxHashString = getBtcTxHashString(utxoBytes);

            int btcTxOutputIndex = utxoBytes[67];

            activeFederationUtxos.add(new Utxo(btcTxHashString, btcTxOutputIndex, valueInSatoshis));

        }

        return activeFederationUtxos;
    }

    private static String getBtcTxHashString(byte[] utxoBytes) {
        byte[] btcTxHash = new byte[32];
        System.arraycopy(utxoBytes, 35, btcTxHash, 0, 32);
        return HexUtils.toJsonHex(btcTxHash);
    }

    private static long getValueInSatoshis(byte[] utxoBytes) {
        byte[] valueBuffer = new byte[7];
        System.arraycopy(utxoBytes, 0, valueBuffer, 0, 7);
        valueBuffer = Arrays.reverse(valueBuffer);
        return new BigInteger(valueBuffer).longValue();
    }

    @Override
    public String toString() {
        return "Utxo{" +
                "btcTxHash='" + btcTxHash +
                ", valueInSatoshis=" + valueInSatoshis +
                ", btcTxOutputIndex=" + btcTxOutputIndex +
                '}';
    }
}
