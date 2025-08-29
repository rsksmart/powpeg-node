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

import co.rsk.federate.signing.PowPegNodeKeyId;
import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.HSMClientException;
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
        HSMVersion hsmVersion = this.hsmClientProtocol.getVersion();
        logger.debug("[getSigningClient] version: {}, keyId: {}", hsmVersion, keyId);

        try {
            HSMSigningClient client = buildHSMSigningClient(hsmVersion);
            logger.debug("[getSigningClient] HSM client: {}", client.getClass());
            return client;
        } catch (IllegalArgumentException e) {
            logger.warn("[getSigningClient] {}", e.getMessage(), e);
            throw new HSMUnsupportedTypeException(e.getMessage());
        }
    }

    private HSMSigningClient buildHSMSigningClient(HSMVersion version) {
        if (!version.isPowHSM()) {
            return new HSMSigningClientV1(this.hsmClientProtocol);
        }

        return switch (PowPegNodeKeyId.fromString(keyId)) {
            case BTC -> new PowHSMSigningClientBtc(hsmClientProtocol, version);
            case RSK, MST -> new PowHSMSigningClientRskMst(hsmClientProtocol, version);
        };
    }
}
