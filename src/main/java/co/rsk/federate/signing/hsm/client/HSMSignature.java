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

import org.ethereum.crypto.ECKey;

/**
 * Represents a signature as gathered
 * from an HSMClient instance. It also
 * contains information about the hash
 * that was used to produce the signature
 * and the public key.
 *
 * @author Ariel Mendelzon
 */
public class HSMSignature {
    private byte[] r;
    private byte[] s;
    private byte[] hash;
    private byte[] publicKey;
    private Byte v;

    public HSMSignature(byte[] r, byte[] s, byte[] hash, byte[] publicKey, Byte v) {
        this.r = r;
        this.s = s;
        this.hash = hash;
        this.publicKey = publicKey;
        this.v = v;
    }

    public byte[] getR() {
        return r;
    }

    public byte[] getS() {
        return s;
    }

    public Byte getV() {
        return v;
    }

    public byte[] getHash() {
        return hash;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public ECKey.ECDSASignature toEthSignature() {
        if (v != null) {
            return ECKey.ECDSASignature.fromComponents(r, s, v);
        }

        // Calculate 'v'
        return ECKey.ECDSASignature.fromComponentsWithRecoveryCalculation(
                r, s, hash, publicKey
        );
    }
}
