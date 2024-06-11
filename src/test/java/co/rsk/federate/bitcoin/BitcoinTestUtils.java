package co.rsk.federate.bitcoin;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BitcoinTestUtils {

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
}
