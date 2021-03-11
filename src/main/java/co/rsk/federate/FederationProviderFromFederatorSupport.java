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
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.config.BridgeConstants;
import co.rsk.peg.ErpFederation;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
import org.ethereum.crypto.ECKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP123;

/**
 * Provides a federation using a FederatorSupport instance, which in turn
 * gathers the federation from the bridge contract of the ethereum
 * network it is attached to.
 *
 * @author Ariel Mendelzon
 */
public class FederationProviderFromFederatorSupport implements FederationProvider {
    private final FederatorSupport federatorSupport;
    private final BridgeConstants bridgeConstants;

    public FederationProviderFromFederatorSupport(FederatorSupport federatorSupport, BridgeConstants bridgeConstants) {
        this.federatorSupport = federatorSupport;
        this.bridgeConstants = bridgeConstants;
    }

    @Override
    public Federation getActiveFederation() {
        List<FederationMember> members = new ArrayList<>();
        int federationSize = federatorSupport.getFederationSize();
        boolean useTypedPublicKeyGetter = federatorSupport.getConfigForBestBlock().isActive(RSKIP123);
        for (int i = 0; i < federationSize; i++) {
            // Select method depending on network configuration for best block
            FederationMember member;
            if (useTypedPublicKeyGetter) {
                BtcECKey btcKey = BtcECKey.fromPublicOnly(federatorSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC).getPubKey());
                ECKey rskKey = federatorSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK);
                ECKey mstKey = federatorSupport.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST);

                member = new FederationMember(btcKey, rskKey, mstKey);
            } else {
                // Before the fork, all of BTC, RSK and MST keys are the same
                BtcECKey btcKey = federatorSupport.getFederatorPublicKey(i);
                ECKey rskMstKey = ECKey.fromPublicOnly(btcKey.getPubKey());
                member = new FederationMember(btcKey, rskMstKey, rskMstKey);
            }

            members.add(member);
        }
        Instant creationTime = federatorSupport.getFederationCreationTime();
        long creationBlockNumber = federatorSupport.getFederationCreationBlockNumber();

        Federation initialFederation =
            new Federation(members, creationTime, creationBlockNumber, federatorSupport.getBtcParams());

        Address federationAddress = federatorSupport.getFederationAddress();

        if (initialFederation.getAddress().equals(federationAddress)) {
            return initialFederation;
        }

        // There is no reason for addresses not to match but being an ERP federation
        return new ErpFederation(
            members,
            creationTime,
            creationBlockNumber,
            federatorSupport.getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay()
        );
    }

    @Override
    public Address getActiveFederationAddress() {
        return federatorSupport.getFederationAddress();
    }

    @Override
    public Optional<Federation> getRetiringFederation() {
        Integer federationSize = federatorSupport.getRetiringFederationSize();
        if (federationSize == -1) {
            return Optional.empty();
        }

        boolean useTypedPublicKeyGetter = federatorSupport.getConfigForBestBlock().isActive(RSKIP123);
        List<FederationMember> members = new ArrayList<>();
        for (int i = 0; i < federationSize; i++) {
            // Select method depending on network configuration for best block
            FederationMember member;
            if (useTypedPublicKeyGetter) {
                BtcECKey btcKey = BtcECKey.fromPublicOnly(federatorSupport.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC).getPubKey());
                ECKey rskKey = federatorSupport.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK);
                ECKey mstKey = federatorSupport.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST);

                member = new FederationMember(btcKey, rskKey, mstKey);
            } else {
                // Before the fork, all of BTC, RSK and MST keys are the same
                BtcECKey btcKey = federatorSupport.getRetiringFederatorPublicKey(i);
                ECKey rskMstKey = ECKey.fromPublicOnly(btcKey.getPubKey());

                member = new FederationMember(btcKey, rskMstKey, rskMstKey);
            }

            members.add(member);
        }

        Instant creationTime = federatorSupport.getRetiringFederationCreationTime();
        long creationBlockNumber = federatorSupport.getRetiringFederationCreationBlockNumber();

        Federation initialFederation =
            new Federation(members, creationTime, creationBlockNumber, federatorSupport.getBtcParams());

        Optional<Address> optionalFederationAddress = federatorSupport.getRetiringFederationAddress();
        Address federationAddress = null;

        if (optionalFederationAddress.isPresent()) {
            federationAddress = optionalFederationAddress.get();
        }

        if (initialFederation.getAddress().equals(federationAddress)) {
            return Optional.of(initialFederation);
        }

        return Optional.of(
            new ErpFederation(
                members,
                creationTime,
                creationBlockNumber,
                federatorSupport.getBtcParams(),
                bridgeConstants.getErpFedPubKeysList(),
                bridgeConstants.getErpFedActivationDelay()
            )
        );
    }

    @Override
    public Optional<Address> getRetiringFederationAddress() {
        return federatorSupport.getRetiringFederationAddress();
    }

    @Override
    public List<Federation> getLiveFederations() {
        List<Federation> result = new ArrayList<>();
        result.add(getActiveFederation());

        Optional<Federation> retiringFederation = getRetiringFederation();
        retiringFederation.ifPresent(result::add);

        return result;
    }
}
