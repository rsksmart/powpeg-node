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

import co.rsk.federate.signing.config.SignerConfig;
import co.rsk.federate.signing.config.SignerType;
import co.rsk.federate.signing.hsm.config.PowHSMConfig;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.hsm.client.HSMClientProtocolFactory;
import co.rsk.federate.signing.hsm.client.HSMSigningClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ECDSASignerFactory {
    private static final Logger logger = LoggerFactory.getLogger(ECDSASignerFactory.class);

    public ECDSASigner buildFromConfig(SignerConfig config) throws SignerException {
        if (config == null) {
            throw new SignerException("'signers' entry not found in config file.");
        }
        SignerType type = config.getSignerType();
        logger.debug("[buildFromConfig] SignerConfig type {}", type);
        switch (type) {
            case KEYFILE:
                return new ECDSASignerFromFileKey(
                    new KeyId(config.getId()),
                    config.getConfig().getString("path")
                );
            case HSM:
                return buildHSMFromConfig(config);
            default:
                throw new IllegalArgumentException(String.format("Unsupported signer type: %s", type));
        }
    }

    private ECDSAHSMSigner buildHSMFromConfig(SignerConfig config) throws SignerException {
        PowHSMConfig powHSMConfig = new PowHSMConfig(config);
        HSMClientProtocol hsmClientProtocol = new HSMClientProtocolFactory().buildHSMClientProtocolFromConfig(
            powHSMConfig
        );
        HSMSigningClientProvider hsmSigningClientProvider = new HSMSigningClientProvider(
            hsmClientProtocol,
            config.getId()
        );
        ECDSAHSMSigner signer = new ECDSAHSMSigner(hsmSigningClientProvider);
        // Add the key mapping
        String hsmKeyId = config.getConfig().getString("keyId");
        signer.addKeyMapping(new KeyId(config.getId()), hsmKeyId);

        return signer;
    }
}
