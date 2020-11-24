package co.rsk.federate.util;


import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptPattern;
import org.ethereum.crypto.HashUtil;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Class to generate:
 * 1) Federators private and public keys
 * 2) Federation address
  */
public class FederationKeysGenerator {
    public static void main(String[] params) throws Exception {
        int numberOfKeys = 10;
        int federatorsRequiredToSign = 7;
        if (params.length == 2) {
            numberOfKeys = Integer.valueOf(params[0]);
            federatorsRequiredToSign = Integer.valueOf(params[1]);
        }

        SecureRandom random = new SecureRandom();
        byte[] secretBytes = new byte[32];

        String[] federatorPrivateKeys = new String[numberOfKeys];
        ECKey[] federatorPublicKeys = new ECKey[numberOfKeys];


        for (int i = 0; i < numberOfKeys; i++) {
            random.nextBytes(secretBytes);

            federatorPrivateKeys[i] = Hex.toHexString(HashUtil.keccak256(Hex.toHexString(secretBytes).getBytes(StandardCharsets.UTF_8)));
            federatorPublicKeys[i] = ECKey.fromPublicOnly(ECKey.fromPrivate(Hex.decode(federatorPrivateKeys[i])).getPubKey());

            System.out.printf("federator%dPrivateKey = %s\n", i, federatorPrivateKeys[i]);
        }
        System.out.println("");

        for (int i = 0; i < numberOfKeys; i++) {
            System.out.printf("federator%dPublicKeyString = %s\n", i, Hex.toHexString(federatorPublicKeys[i].getPubKey()));
        }
        System.out.println("");

        Script redeemScript = ScriptBuilder.createRedeemScript(federatorsRequiredToSign, Arrays.asList(federatorPublicKeys));
        Script federationPubScript = ScriptBuilder.createP2SHOutputScript(redeemScript);

        Address federationAddress = LegacyAddress.fromScriptHash(TestNet3Params.get(), ScriptPattern.extractHashFromP2SH(federationPubScript));
        System.out.println("federationAddress = " + federationAddress);
    }

}