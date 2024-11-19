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

import static co.rsk.peg.federation.FederationChangeResponseCode.FEDERATION_NON_EXISTENT;
import static co.rsk.peg.federation.FederationMember.KeyType;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP123;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP419;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.crypto.ECKey;

/**
 * A provider that supplies the current active, retiring, and proposed federations by
 * using a {@link FederatorSupport} instance, which interacts with the Bridge contract.
 *
 * <p>The {@code FederationProviderFromFederatorSupport} enables access to:
 * <ul>
 *     <li><strong>Active Federation</strong>
 *     <li><strong>Retiring Federation</strong>
 *     <li><strong>Proposed Federation</strong>
 * </ul>
 */
public class FederationProviderFromFederatorSupport implements FederationProvider {

    private final FederatorSupport federatorSupport;
    private final FederationConstants federationConstants;

    public FederationProviderFromFederatorSupport(
        FederatorSupport federatorSupport,
        FederationConstants federationConstants) {
        this.federatorSupport = federatorSupport;
        this.federationConstants = federationConstants;
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

        if (federationSize == FEDERATION_NON_EXISTENT.getCode()) {
            return Optional.empty();
        }

        Address retiringFederationAddress = getRetiringFederationAddress().orElseThrow(IllegalStateException::new);
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
    public Optional<Federation> getProposedFederation() {
        if (!federatorSupport.getConfigForBestBlock().isActive(RSKIP419)) {
            return Optional.empty();
        }

        Integer federationSize = federatorSupport.getProposedFederationSize()
            .orElse(FEDERATION_NON_EXISTENT.getCode());
        if (federationSize == FEDERATION_NON_EXISTENT.getCode()) {
            return Optional.empty();
        }

        List<FederationMember> federationMembers = IntStream.range(0, federationSize)
            .mapToObj(i -> new FederationMember(
                federatorSupport.getProposedFederatorPublicKeyOfType(i, KeyType.BTC)
                    .map(ECKey::getPubKey)
                    .map(BtcECKey::fromPublicOnly)
                    .orElseThrow(IllegalStateException::new),
                federatorSupport.getProposedFederatorPublicKeyOfType(i, KeyType.RSK)
                    .orElseThrow(IllegalStateException::new),
                federatorSupport.getProposedFederatorPublicKeyOfType(i, KeyType.MST)
                    .orElseThrow(IllegalStateException::new)
            ))
            .toList();

        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            federatorSupport.getProposedFederationCreationTime()
                .orElseThrow(IllegalStateException::new),
            federatorSupport.getProposedFederationCreationBlockNumber()
                .orElseThrow(IllegalStateException::new),
            federatorSupport.getBtcParams()
        );

        Federation federation = FederationFactory.buildP2shErpFederation(
            federationArgs,
            federationConstants.getErpFedPubKeysList(),
            federationConstants.getErpFedActivationDelay());

        return Optional.of(federation);
    }

    @Override
    public Optional<Address> getProposedFederationAddress() {
        return Optional.of(federatorSupport)
            .filter(fedSupport -> fedSupport.getConfigForBestBlock().isActive(RSKIP419))
            .flatMap(FederatorSupport::getProposedFederationAddress);
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
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();
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
