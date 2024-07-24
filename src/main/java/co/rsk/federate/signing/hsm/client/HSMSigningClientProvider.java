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

package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This particular HSMClient can
 * provide specific HSMClient instances
 * depending on the connected module's
 * version.
 *
 * @author Ariel Mendelzon
 */
public class HSMSigningClientProvider {
    private static final Logger logger = LoggerFactory.getLogger(HSMSigningClientProvider.class);

    private final HSMClientProtocol hsmClientProtocol;
    private final String keyId;

    public HSMSigningClientProvider(HSMClientProtocol protocol, String keyId) {
        this.hsmClientProtocol = protocol;
        this.keyId = keyId;
    }

    public HSMSigningClient getSigningClient() throws HSMClientException {
        int version = this.hsmClientProtocol.getVersion();
        HSMSigningClient client;
        logger.debug("[getSigningClient] version: {}, keyId: {}", version, keyId);
        if (version == HSMVersion.V1.getNumber()) {
            client = new HSMSigningClientV1(this.hsmClientProtocol);
        } else if (HSMVersion.isPowHSM(version)) {
            client = buildPowHSMClient(version);
        } else {
            String message = String.format("Unsupported HSM version %d", version);
            logger.debug("[getSigningClient] {}", message);
            throw new HSMUnsupportedVersionException(message);
        }

        logger.debug("[getSigningClient] HSM client: {}", client.getClass());
        return client;
    }

    private HSMSigningClient buildPowHSMClient(int version) throws HSMUnsupportedTypeException {
        HSMSigningClient client;
        switch (keyId) {
            case "BTC":
                client = new PowHSMSigningClientBtc(this.hsmClientProtocol, version);
                break;
            case "RSK":
            case "MST":
                client = new PowHSMSigningClientRskMst(this.hsmClientProtocol, version);
                break;
            default:
                String message = String.format("Unsupported key id %s", keyId);
                logger.debug("[buildPowHSMClient] {}", message);
                throw new HSMUnsupportedTypeException(message);
        }
        return client;
    }
}
