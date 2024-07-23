/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.federate.util;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.federate.rpc.SocketBasedJsonRpcClientProvider;
import co.rsk.federate.signing.hsm.client.HSMSigningClient;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.hsm.client.HSMSigningClientProvider;
import co.rsk.federate.signing.hsm.config.PowHSMConfigParameter;
import co.rsk.federate.signing.hsm.client.HSMSignature;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.message.SignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessageV1;
import org.ethereum.crypto.ECKey;
import org.bouncycastle.util.encoders.Hex;
import java.util.Arrays;
import java.util.Random;

public class HSMChecker {
    /**
     * Check the HSM device for soundness
     * Optionally provide host and port of the server
     * using the first two command line arguments (resp.)
     * Otherwise assume localhost:9999
     */
    public static void main(String[] args) {
        try {
            final String[] KEY_IDS = new String[] {
                    "m/44'/1'/0'/0/0",
                    "m/44'/137'/0'/0/0"
            };

            ECKey[] hsmPublicKeys = new ECKey[KEY_IDS.length];

            final String MESSAGE = "aabbccddeeff";
            final byte[] MESSAGE_BYTES = Hex.decode(MESSAGE);
            final Sha256Hash MESSAGE_HASH = Sha256Hash.of(MESSAGE_BYTES);
            final SignerMessage messageObject = new SignerMessageV1(new byte[0]);

            String host = "127.0.0.1"; //NOSONAR
            int port = 9999;

            // Host/port given as parameters?
            if (args.length == 2) {
                host = args[0];
                port = Integer.parseInt(args[1]);
            }

            System.out.printf("Connecting to HSM @ %s:%d...\n", host, port);
            SocketBasedJsonRpcClientProvider jsonRpcClientProvider = SocketBasedJsonRpcClientProvider.fromHostPort(host, port);
            jsonRpcClientProvider.setSocketTimeout(PowHSMConfigParameter.SOCKET_TIMEOUT.getDefaultValue(Integer::parseInt));
            HSMClientProtocol hsmClientProtocol = new HSMClientProtocol(
                jsonRpcClientProvider,
                PowHSMConfigParameter.MAX_ATTEMPTS.getDefaultValue(Integer::parseInt),
                PowHSMConfigParameter.INTERVAL_BETWEEN_ATTEMPTS.getDefaultValue(Integer::parseInt));
            HSMSigningClientProvider hsmSigningClientProvider = new HSMSigningClientProvider(hsmClientProtocol, "");
            HSMSigningClient client = hsmSigningClientProvider.getSigningClient();
            System.out.printf("Connected. Testing.\n");

            for (int k = 0; k < KEY_IDS.length; k++) {
                String keyId = KEY_IDS[k];

                System.out.printf("Using key id '%s'\n", keyId);
                System.out.printf("Version: %d\n", client.getVersion());

                ECKey hsmPublicKey = getPublicKey(client, keyId);
                // Save for later verification
                hsmPublicKeys[k] = hsmPublicKey;

                System.out.printf("Public key: %s\n", Hex.toHexString(hsmPublicKey.getPubKey()));

                HSMSignature hsmSignature = client.sign(keyId, messageObject);
                ECKey.ECDSASignature signature = hsmSignature.toEthSignature();

                boolean signatureValid = hsmPublicKey.verify(MESSAGE_HASH.getBytes(), signature);

                System.out.printf("Signature valid for public key: %s\n", yesOrNo(signatureValid));
                System.out.printf("Signature recovery byte valid: %s\n", yesOrNo(signature.validateComponents()));

                for (int i = 0; i < 10; i++) {
                    byte[] randomMessage = new byte[randomInRange(10, 20)];
                    new Random().nextBytes(randomMessage);
                    SignerMessage randomMessageHash = new SignerMessageV1(randomMessage);
                    long startTime = System.currentTimeMillis();
                    ECKey hsmPublicKeyBis = getPublicKey(client, keyId);

                    System.out.printf("Soundness check #%d... ", i + 1);

                    // Check that public key is the same as the one previously requested
                    if (!Arrays.equals(hsmPublicKey.getPubKey(), hsmPublicKeyBis.getPubKey())) {
                        System.out.printf("Public keys do not match: %s - %s\n", Hex.toHexString(hsmPublicKey.getPubKey()), Hex.toHexString(hsmPublicKeyBis.getPubKey()));
                        break;
                    }

                    // Sign and check signature
                    hsmSignature = client.sign(keyId, randomMessageHash);
                    signature = hsmSignature.toEthSignature();
                    System.out.printf("elapsed time:%dms ... ",System.currentTimeMillis() - startTime);
                    signatureValid = hsmPublicKey.verify(((SignerMessageV1)randomMessageHash).getBytes(), signature);

                    if (!signatureValid) {
                        System.out.printf("Signature of message %s invalid for public key %s\n", Hex.toHexString(randomMessage), Hex.toHexString(hsmPublicKeyBis.getPubKey()));
                        break;
                    }

                    if (!signature.validateComponents()) {
                        System.out.printf("Signature of message %s has invalid recovery byte\n", Hex.toHexString(randomMessage));
                        break;
                    }

                    System.out.printf("OK\n");
                }

                System.out.printf("Testing OK for key id \"%s\"\n", keyId);
                System.out.printf("====================================================================\n", keyId);
            }

            // Check all public keys are different
            if (hsmPublicKeys.length > Arrays.stream(hsmPublicKeys).distinct().count()) {
                System.out.printf("There are duplicate public keys found for different key ids\n");
                System.out.printf("Key ids and public keys are:\n");
                for (int i = 0; i < KEY_IDS.length; i++) {
                    System.out.printf("%s - %s\n", KEY_IDS[i], Hex.toHexString(hsmPublicKeys[i].getPubKey(true)));
                }
            } else {
                System.out.printf("All OK\n");
            }
        } catch (Exception e) {
            System.out.printf("Oops\n");
            e.printStackTrace(); //NOSONAR
        }
    }

    private static ECKey getPublicKey(HSMSigningClient client, String keyId) throws HSMClientException {
        final byte[] hsmPublicKeyBytes = client.getPublicKey(keyId);
        return ECKey.fromPublicOnly(hsmPublicKeyBytes);
    }

    private static String yesOrNo(boolean b) {
        return b ? "YES" : "NO";
    }

    private static int randomInRange(int min, int max) {
        return new Random().nextInt(max - min + 1) + min;
    }
}
