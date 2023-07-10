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

package co.rsk.federate.signing.hsm.message;

import java.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

/**
 * Immutable plain byte array message
 *
 * @author Ariel Mendelzon
 */
public class SignerMessageV1 extends SignerMessage {
    private final byte[] message;

    public SignerMessageV1(byte[] message) {
        // Save a copy
        this.message = copy(message);
    }

    @Override
    public byte[] getBytes() {
        return copy(message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }

        return Arrays.equals(this.message, ((SignerMessageV1) o).message);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(message);
    }

    @Override
    public String toString() {
        return Hex.toHexString(getBytes());
    }

    private byte[] copy(byte[] a) {
        return Arrays.copyOf(a, a.length);
    }
}
