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
package co.rsk.federate;

import co.rsk.bitcoinj.core.Address;
import co.rsk.peg.federation.Federation;
import java.util.Optional;

/**
 * Provides access to various federation instances, including the active, retiring, and proposed federations.
 * Implementations of this interface must define how to retrieve the federations and their respective addresses.
 * These methods allow clients to manage and monitor federations in different lifecycle stages within the bridge.
 */
public interface FederationProvider {

    /**
     * Retrieves the currently active federation.
     *
     * @return the active {@link Federation} instance
     */
    Federation getActiveFederation();

    /**
     * Retrieves the address of the currently active federation.
     *
     * @return the {@link Address} of the active federation
     */
    Address getActiveFederationAddress();

    /**
     * Retrieves the currently retiring federation, if one exists. This federation is in transition 
     * and will soon be replaced by a new active federation.
     *
     * @return an {@link Optional} containing the retiring {@link Federation}, or {@link Optional#empty()} if none exists
     */
    Optional<Federation> getRetiringFederation();

    /**
     * Retrieves the address of the currently retiring federation, if one exists.
     *
     * @return an {@link Optional} containing the {@link Address} of the retiring federation, or {@link Optional#empty()} if none exists
     */
    Optional<Address> getRetiringFederationAddress();

    /**
     * Retrieves the currently proposed federation, if one exists. This federation is awaiting validation.
     *
     * @return an {@link Optional} containing the proposed {@link Federation}, or {@link Optional#empty()} if none exists
     */
    Optional<Federation> getProposedFederation();

    /**
     * Retrieves the address of the currently proposed federation, if one exists.
     *
     * @return an {@link Optional} containing the {@link Address} of the proposed federation, or {@link Optional#empty()} if none exists
     */
    Optional<Address> getProposedFederationAddress();
}
