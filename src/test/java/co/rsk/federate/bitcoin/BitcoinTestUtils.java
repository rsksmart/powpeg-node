package co.rsk.federate.bitcoin;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;

public final class BitcoinTestUtils {

    public static final byte[]  WITNESS_COMMITMENT_HEADER = Hex.decode("aa21a9ed");
    public static final Sha256Hash WITNESS_RESERVED_VALUE = Sha256Hash.ZERO_HASH;
    public static final int WITNESS_COMMITMENT_LENGTH = WITNESS_COMMITMENT_HEADER.length + Sha256Hash.LENGTH;

    private BitcoinTestUtils() { }

    public static List<Coin> coinListOf(long... values) {
        return Arrays.stream(values)
            .mapToObj(Coin::valueOf)
            .collect(Collectors.toList());
    }

    public static Sha256Hash createHash(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) (0xFF & nHash);
        bytes[1] = (byte) (0xFF & nHash >> 8);
        bytes[2] = (byte) (0xFF & nHash >> 16);
        bytes[3] = (byte) (0xFF & nHash >> 24);

        return Sha256Hash.wrap(bytes);
    }

    public static Address createP2PKHAddress(NetworkParameters networkParameters, String seed) {
        BtcECKey key = BtcECKey.fromPrivate(
            HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8)));
        return key.toAddress(networkParameters);
    }
}
