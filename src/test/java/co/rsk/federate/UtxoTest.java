package co.rsk.federate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class UtxoTest {

    @Test
    void parseRLPToActiveFederationUtxos_correctBridgeUtxosRlpEncodedData_returnsListOfUtxos() {

        String rlpData = "0xf901d4b84c60d4c2030000000017000000a914896ed9f3446d51b5510f7f0b6ef81b2bde55140e87d050b1ea914a0fe96d04d550aa46d8b12bd8caff9150a2c578d18517bbddb119000000000000000000b84c60d4c2030000000017000000a914896ed9f3446d51b5510f7f0b6ef81b2bde55140e877d4fdb6e042c3a578ffe1f2d0cba389c53d5e66ace051105b14d1264a06beb91000000000000000000b84c60d4c2030000000017000000a914896ed9f3446d51b5510f7f0b6ef81b2bde55140e87cf2894545dcd8eec677f6040231620dfcd5d3676f7ec89eeec3cc9e299a3e606000000000000000000b84ce03d2a030000000017000000a914896ed9f3446d51b5510f7f0b6ef81b2bde55140e8767c7076e750c5c4996a6deb100fff07900b4af153efe37d1fb061c2aebb24c79000000000000000000b84c60d4c2030000000017000000a914896ed9f3446d51b5510f7f0b6ef81b2bde55140e8765928ab32d8c90347397d4c37d92e24ce2ae523eab2bc3d18624c9fde032c86e000000000000000000b84c00a3e1110000000017000000a914896ed9f3446d51b5510f7f0b6ef81b2bde55140e87ad050ab477270b65b4c830876d7f8a97e61c735adecf211de0fa53bf5bf133dc000000000000000000";

        List<Utxo> utxos = Utxo.parseRLPToActiveFederationUtxos(rlpData);

        Assertions.assertEquals(utxos.size(), 6);

        Utxo utxo0 = utxos.get(0);
        Assertions.assertEquals(utxo0.getBtcTxHash(), "0xd050b1ea914a0fe96d04d550aa46d8b12bd8caff9150a2c578d18517bbddb119");
        Assertions.assertEquals(utxo0.getValueInSatoshis(), 63100000);
        Assertions.assertEquals(utxo0.getBtcTxOutputIndex(), 0);

        Utxo utxo1 = utxos.get(1);
        Assertions.assertEquals(utxo1.getBtcTxHash(), "0x7d4fdb6e042c3a578ffe1f2d0cba389c53d5e66ace051105b14d1264a06beb91");
        Assertions.assertEquals(utxo1.getValueInSatoshis(), 63100000);
        Assertions.assertEquals(utxo1.getBtcTxOutputIndex(), 0);

        Utxo utxo2 = utxos.get(2);
        Assertions.assertEquals(utxo2.getBtcTxHash(), "0xcf2894545dcd8eec677f6040231620dfcd5d3676f7ec89eeec3cc9e299a3e606");
        Assertions.assertEquals(utxo2.getValueInSatoshis(), 63100000);
        Assertions.assertEquals(utxo2.getBtcTxOutputIndex(), 0);

        Utxo utxo3 = utxos.get(3);
        Assertions.assertEquals(utxo3.getBtcTxHash(), "0x67c7076e750c5c4996a6deb100fff07900b4af153efe37d1fb061c2aebb24c79");
        Assertions.assertEquals(utxo3.getValueInSatoshis(), 53100000);
        Assertions.assertEquals(utxo3.getBtcTxOutputIndex(), 0);

        Utxo utxo4 = utxos.get(4);
        Assertions.assertEquals(utxo4.getBtcTxHash(), "0x65928ab32d8c90347397d4c37d92e24ce2ae523eab2bc3d18624c9fde032c86e");
        Assertions.assertEquals(utxo4.getValueInSatoshis(), 63100000);
        Assertions.assertEquals(utxo4.getBtcTxOutputIndex(), 0);

        Utxo utxo5 = utxos.get(5);
        Assertions.assertEquals(utxo5.getBtcTxHash(), "0xad050ab477270b65b4c830876d7f8a97e61c735adecf211de0fa53bf5bf133dc");
        Assertions.assertEquals(utxo5.getValueInSatoshis(), 300000000);
        Assertions.assertEquals(utxo5.getBtcTxOutputIndex(), 0);

    }

}
