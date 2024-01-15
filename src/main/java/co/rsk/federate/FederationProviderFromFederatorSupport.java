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
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import co.rsk.peg.federation.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
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
        NetworkParameters btcParams = federatorSupport.getBtcParams();
        FederationArgs federationArgs = new FederationArgs(members, creationTime, creationBlockNumber, btcParams);

        Federation initialFederation = FederationFactory.buildStandardMultiSigFederation(federationArgs);

        return getExpectedFederation(initialFederation, getActiveFederationAddress());
    }

    @Override
    public Address getActiveFederationAddress() {
        return federatorSupport.getFederationAddress();
    }

    @Override
    public Optional<Federation> getRetiringFederation() {
        Integer federationSize = federatorSupport.getRetiringFederationSize();
        Optional<Address> optionalRetiringFederationAddress = getRetiringFederationAddress();

        if (federationSize == -1 || !optionalRetiringFederationAddress.isPresent()) {
            return Optional.empty();
        }

        Address retiringFederationAddress = optionalRetiringFederationAddress.get();
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
        NetworkParameters btcParams = federatorSupport.getBtcParams();
        FederationArgs federationArgs = new FederationArgs(members, creationTime, creationBlockNumber, btcParams);

        Federation initialFederation = FederationFactory.buildStandardMultiSigFederation(federationArgs);

        return Optional.of(getExpectedFederation(initialFederation, retiringFederationAddress));
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

    private Federation getExpectedFederation(Federation initialFederation, Address expectedFederationAddress) {
        // First check if the initial federation address matches the expected one
        if (initialFederation.getAddress().equals(expectedFederationAddress)) {
            return initialFederation;
        }
        List<FederationMember> members = initialFederation.getMembers();
        Instant creationTime = initialFederation.getCreationTime();
        long creationBlockNumber = initialFederation.getCreationBlockNumber();
        NetworkParameters btcParams = federatorSupport.getBtcParams();
        List<BtcECKey> erpPubKeys = bridgeConstants.getErpFedPubKeysList();
        long activationDelay = bridgeConstants.getErpFedActivationDelay();
        ActivationConfig.ForBlock activations = federatorSupport.getConfigForBestBlock();

        FederationArgs federationArgs =
            new FederationArgs(members, creationTime, creationBlockNumber, btcParams);

        // If addresses match build a Non-Standard ERP federation
        ErpFederation nonStandardErpFederation =
            FederationFactory.buildNonStandardErpFederation(federationArgs, erpPubKeys, activationDelay, activations);

        if (nonStandardErpFederation.getAddress().equals(expectedFederationAddress)) {
            return nonStandardErpFederation;
        }

        // Finally, try building a P2SH ERP federation
        return FederationFactory.buildP2shErpFederation(federationArgs, erpPubKeys, activationDelay);

        // TODO: what if no federation built matches the expected address?
        //  It could mean that there is a different type of federation in the Bridge that we are not considering here
        //  We should consider throwing an exception and shutting down the node
    }
}
