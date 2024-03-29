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

import co.rsk.federate.config.SignerConfig;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.hsm.client.HSMClientProtocolFactory;
import co.rsk.federate.signing.hsm.client.HSMSigningClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds signers given configuration
 *
 * @author Ariel Mendelzon
 */
public class ECDSASignerFactory {
    private static final Logger logger = LoggerFactory.getLogger(ECDSASignerFactory.class);
    public static final int DEFAULT_SOCKET_TIMEOUT = 10_000;
    public static final int DEFAULT_ATTEMPTS = 2;
    public static final int DEFAULT_INTERVAL = 1000;

    public ECDSASigner buildFromConfig(SignerConfig config) throws SignerException {
        if (config == null) {
            throw new SignerException("'signers' entry not found in config file.");
        }
        String type = config.getType();
        logger.debug("[buildFromConfig] SignerConfig type {}", type);
        switch (type) {
            case "keyFile":
                return new ECDSASignerFromFileKey(
                        new KeyId(config.getId()),
                        config.getConfig().getString("path")
                );
            case "hsm":
                try {
                    HSMClientProtocol hsmClientProtocol = new HSMClientProtocolFactory().buildHSMClientProtocolFromConfig(config);
                    HSMSigningClientProvider hsmSigningClientProvider = new HSMSigningClientProvider(hsmClientProtocol, config.getId());
                    ECDSAHSMSigner signer = new ECDSAHSMSigner(hsmSigningClientProvider);
                    // Add the key mapping
                    String hsmKeyId = config.getConfig().getString("keyId");
                    signer.addKeyMapping(new KeyId(config.getId()), hsmKeyId);
                    return signer;
                } catch (Exception e) {
                    String message = "Something went wrong while trying to build HSM Signer";
                    logger.debug("[buildFromConfig] {} - {}", message, e.getMessage());
                    throw new RuntimeException(e.getMessage());
                }
            default:
                throw new RuntimeException(String.format("Unsupported signer type: %s", type));
        }
    }
}
