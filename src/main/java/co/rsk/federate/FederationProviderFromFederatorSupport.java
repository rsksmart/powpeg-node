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
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.bitcoin.ScriptCreationException;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.ethereum.crypto.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A provider that supplies the current active, retiring, and proposed federations by using a
 * {@link FederatorSupport} instance, which interacts with the Bridge contract.
 *
 * <p>The {@code FederationProviderFromFederatorSupport} enables access to:
 * <ul>
 *     <li><strong>Active Federation</strong>
 *     <li><strong>Retiring Federation</strong>
 *     <li><strong>Proposed Federation</strong>
 * </ul>
 */
public class FederationProviderFromFederatorSupport implements FederationProvider {

    private static final Logger logger = LoggerFactory.getLogger(
        FederationProviderFromFederatorSupport.class);

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

        Address activeFederationAddress = getActiveFederationAddress();
        logger.debug("[getActiveFederation] Attempting to get active federation with address {}", activeFederationAddress);
        return buildFederation(federationArgs, activeFederationAddress);
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

        logger.debug("[getRetiringFederation] Attempting to get retiring federation with address {}", retiringFederationAddress);
        Federation retiringFederation = buildFederation(federationArgs, retiringFederationAddress);
        return Optional.of(retiringFederation);
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

        int federationSize = federatorSupport.getProposedFederationSize()
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

        Federation proposedFederation = FederationFactory.buildP2shP2wshErpFederation(
            federationArgs,
            federationConstants.getErpFedPubKeysList(),
            federationConstants.getErpFedActivationDelay());
        return Optional.of(proposedFederation);
    }

    @Override
    public Optional<Address> getProposedFederationAddress() {
        return Optional.of(federatorSupport)
            .filter(fedSupport -> fedSupport.getConfigForBestBlock().isActive(RSKIP419))
            .flatMap(FederatorSupport::getProposedFederationAddress);
    }

    private Federation buildFederation(FederationArgs federationArgs, Address expectedFederationAddress) {
        return buildStandardMultiSigFederationIfMatches(federationArgs, expectedFederationAddress)
            .or(() -> buildP2shP2wshErpFederationIfMatches(federationArgs,
                expectedFederationAddress))
            .orElseThrow(() ->
                buildInvalidFederationException(expectedFederationAddress)
            );
    }

    private static IllegalStateException buildInvalidFederationException(
        Address expectedFederationAddress) {
        return new IllegalStateException(
            String.format(
                "Cannot determine federation type for federation with address %s. Tried: standard multiSig, and P2SH-P2WSH ERP federations.",
                expectedFederationAddress
            )
        );
    }

    private Optional<Federation> buildStandardMultiSigFederationIfMatches(FederationArgs federationArgs,
        Address expectedFederationAddress) {
        Federation standardMultiSigFederation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs);
        if (standardMultiSigFederation.getAddress().equals(expectedFederationAddress)) {
            logger.debug("[buildStandardMultiSigFederationIfMatches] Expected federation is a standard multiSig one.");
            return Optional.of(standardMultiSigFederation);
        }
        logger.debug("[buildStandardMultiSigFederationIfMatches] Expected federation is not a standard multiSig one.");
        return Optional.empty();
    }

    private Optional<Federation> buildP2shP2wshErpFederationIfMatches(FederationArgs federationArgs,
        Address expectedFederationAddress) {
        try {
            ErpFederation p2shP2wshErpFederation = FederationFactory.buildP2shP2wshErpFederation(
                federationArgs, federationConstants.getErpFedPubKeysList(),
                federationConstants.getErpFedActivationDelay());

            if (p2shP2wshErpFederation.getAddress().equals(expectedFederationAddress)) {
                logger.debug(
                    "[tryToBuildP2shP2wshErpFederation] Expected federation is a p2sh-p2wsh erp one.");
                return Optional.of(p2shP2wshErpFederation);
            }
        } catch (ErpFederationCreationException | ScriptCreationException e) {
            logger.debug("[buildP2shP2wshErpFederationIfMatches] Expected federation is not a p2sh-p2wsh erp one.",
                e);
        }
        return Optional.empty();
    }
}
