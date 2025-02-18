/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.federate.signing;

import co.rsk.federate.UnrecoverableErrorEventListener;
import co.rsk.federate.signing.hsm.HSMChangedVersionException;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.client.HSMSigningClient;
import co.rsk.federate.signing.hsm.client.HSMSigningClientProvider;
import co.rsk.federate.signing.hsm.client.HSMSignature;
import co.rsk.federate.signing.hsm.message.SignerMessage;
import org.ethereum.crypto.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Signer that signs using an HSM.
 *
 * Key ids need to be initially configured
 * to the key ids that the HSM needs, which
 * might not match, e.g., what the federate
 * node might know as the "BTC" key id,
 * might be a bip44 path to the HSM.
 *
 * @author Ariel Mendelzon
 */
public class ECDSAHSMSigner implements ECDSASigner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ECDSAHSMSigner.class);

    private final HSMSigningClientProvider clientProvider;
    private final Map<KeyId, String> keyIdMapping;
    private HSMSigningClient client;
    private List<UnrecoverableErrorEventListener> listeners;

    public ECDSAHSMSigner(HSMSigningClientProvider clientProvider) {
        this.clientProvider = clientProvider;
        this.keyIdMapping = new HashMap<>();
        this.listeners = new ArrayList<>();
    }

    public void addKeyMapping(KeyId keyId, String hsmId) {
        keyIdMapping.put(keyId, hsmId);
    }

    @Override
    public void addListener(UnrecoverableErrorEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public boolean canSignWith(KeyId keyId) {
        return keyIdMapping.containsKey(keyId);
    }

    @Override
    public ECDSASignerCheckResult check() {
        try {
            ensureHsmClient();
        } catch (HSMClientException e) {
            String keysIds = keyIdMapping.keySet().stream().map(keyId -> keyId.toString()).collect(Collectors.joining(", "));
            return new ECDSASignerCheckResult(Collections.singletonList("HSM "+ keysIds + " Signer: " + e.getMessage()));
        }

        // Make sure all public keys are retrievable
        List<String> messages = new LinkedList<>();

        for (Map.Entry<KeyId, String> mapping : keyIdMapping.entrySet()) {
            try {
                client.getPublicKey(mapping.getValue());
            } catch (HSMClientException e) {
                messages.add(e.getMessage());
                LOGGER.error("[check] Unable to retrieve public key", e);
            }
        }

        return new ECDSASignerCheckResult(messages);
    }

    @Override
    public ECPublicKey getPublicKey(KeyId keyId) throws SignerException {
        return invokeWithVersionRetry(keyId, client1 -> {
            byte[] publicKeyBytes = client.getPublicKey(keyIdMapping.get(keyId));
            return new ECPublicKey(publicKeyBytes);
        });
    }

    @Override
    public int getVersionForKeyId(KeyId keyId) throws SignerException {
        int version;
        if (!canSignWith(keyId)) {
            throw new SignerException(String.format("Can't find version for this key for the requested signing key: %s", keyId));
        }
        try {
            ensureHsmClient();
            version = client.getVersion();
        } catch (HSMClientException e) {
            throw new SignerException(String.format("Error trying to retrieve version from HSM %s Signer", keyId), e);
        }
        return version;
    }

    @Override
    public ECKey.ECDSASignature sign(KeyId keyId, SignerMessage message) throws SignerException {
        return invokeWithVersionRetry(keyId, client1 -> {
            HSMSignature signature = client1.sign(keyIdMapping.get(keyId), message);
            return signature.toEthSignature();
        });
    }

    @Override
    public String getVersionString() throws SignerException {
        String keysIds = keyIdMapping.keySet().stream().map(keyId -> keyId.toString()).collect(Collectors.joining(", "));
        try {
            ensureHsmClient();
            return "HSM " + keysIds + " Signer Version:" + client.getVersion();
        } catch (HSMClientException e) {
            throw new SignerException("Error trying to retrieve version from HSM " + keysIds + " Signer", e);
        }
    }

    public HSMSigningClient getClient() throws HSMClientException {
        this.ensureHsmClient();
        return this.client;
    }

    private void ensureHsmClient() throws HSMClientException {
        if (client == null) {
            client = clientProvider.getSigningClient();
        }
    }

    private <T> T invokeWithVersionRetry(KeyId keyId, SignerCall<T> call) throws SignerException {
        if (!keyIdMapping.containsKey(keyId)) {
            throw new SignerException(String.format("No mapped HSM key id found for the requested signing key: %s", keyId));
        }
        try {
            ensureHsmClient();
            return call.invoke(client);
        } catch(HSMChangedVersionException e) {
            client = null;
            return invokeWithVersionRetry(keyId, call);
        } catch (HSMClientException e) {
            // Notify the subscribed listeners that the signer is no longer operating
            this.listeners.stream().forEach(l -> l.unrecoverableErrorOccured(e));
            throw new SignerException("There was an error trying to sign message", e);
        }
    }

    private interface SignerCall<T> {
        T invoke(HSMSigningClient client) throws HSMClientException;
    }
}