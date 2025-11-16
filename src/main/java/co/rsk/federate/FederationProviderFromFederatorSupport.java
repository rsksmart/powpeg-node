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
import co.rsk.peg.bitcoin.ScriptCreationException;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
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
        int federationSize = federatorSupport.getFederationSize();
        boolean useTypedPublicKeyGetter = federatorSupport.getConfigForBestBlock()
            .isActive(RSKIP123);

        List<FederationMember> members = buildMembers(
            federationSize,
            useTypedPublicKeyGetter,
            federatorSupport::getFederatorPublicKeyOfType,
            federatorSupport::getFederatorPublicKey
        );

        FederationArgs federationArgs = new FederationArgs(
            members,
            federatorSupport.getFederationCreationTime(),
            federatorSupport.getFederationCreationBlockNumber(),
            federatorSupport.getBtcParams()
        );

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

        Address retiringFederationAddress = getRetiringFederationAddress()
            .orElseThrow(() -> new IllegalStateException(
                "Retiring federation size is present but address is missing"));

        boolean useTypedPublicKeyGetter = federatorSupport.getConfigForBestBlock()
            .isActive(RSKIP123);

        List<FederationMember> members = buildMembers(
            federationSize,
            useTypedPublicKeyGetter,
            federatorSupport::getRetiringFederatorPublicKeyOfType,
            federatorSupport::getRetiringFederatorPublicKey
        );

        FederationArgs federationArgs = new FederationArgs(members,
            federatorSupport.getRetiringFederationCreationTime(),
            federatorSupport.getRetiringFederationCreationBlockNumber(),
            federatorSupport.getBtcParams()
        );

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

        BiFunction<Integer, KeyType, ECKey> proposedKeyGetter = (index, keyType) ->
            federatorSupport.getProposedFederatorPublicKeyOfType(index, keyType)
                .orElseThrow(() -> new IllegalStateException(
                    String.format(
                        "Proposed federator %s public key missing for index %d",
                        keyType,
                        index
                    )
                ));

        List<FederationMember> federationMembers = buildFederationMembers(
            federationSize,
            proposedKeyGetter
        );

        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            federatorSupport.getProposedFederationCreationTime()
                .orElseThrow(() -> new IllegalStateException(
                    "Proposed federation creation time is missing")),
            federatorSupport.getProposedFederationCreationBlockNumber()
                .orElseThrow(() -> new IllegalStateException(
                    "Proposed federation creation block number is missing")),
            federatorSupport.getBtcParams()
        );

        Federation proposedFederation = buildProposedFederation(federationArgs);
        return Optional.of(proposedFederation);
    }

    private Federation buildProposedFederation(FederationArgs federationArgs) {
        if (!federatorSupport.getConfigForBestBlock().isActive(RSKIP305)) {
            return FederationFactory.buildP2shErpFederation(
                federationArgs,
                federationConstants.getErpFedPubKeysList(),
                federationConstants.getErpFedActivationDelay());
        }

        return FederationFactory.buildP2shP2wshErpFederation(
            federationArgs,
            federationConstants.getErpFedPubKeysList(),
            federationConstants.getErpFedActivationDelay());
    }

    @Override
    public Optional<Address> getProposedFederationAddress() {
        if (!federatorSupport.getConfigForBestBlock().isActive(RSKIP419)) {
            return Optional.empty();
        }
        return federatorSupport.getProposedFederationAddress();
    }

    private Federation buildFederation(FederationArgs federationArgs,
        Address expectedFederationAddress) {

        return tryStandardMultiSigFederation(federationArgs, expectedFederationAddress)
            .or(() -> tryP2shP2wshErpFederation(federationArgs,
                expectedFederationAddress))
            .orElseThrow(() ->
                new IllegalStateException(
                    String.format(
                        "Cannot determine federation type for federation with address %s. Tried: standard multiSig, P2SH ERP, and P2SH-P2WSH ERP federations.",
                        expectedFederationAddress
                    )
                )
            );
    }

    private Optional<Federation> tryStandardMultiSigFederation(FederationArgs federationArgs,
        Address expectedFederationAddress) {
        Federation standardMultiSigFederation = FederationFactory.buildStandardMultiSigFederation(
            federationArgs);
        if (standardMultiSigFederation.getAddress().equals(expectedFederationAddress)) {
            logger.debug("[getExpectedFederation] Expected federation is a standard multiSig one.");
            return Optional.of(standardMultiSigFederation);
        }
        logger.debug("[getExpectedFederation] Expected federation is not a standard multiSig one.");
        return Optional.empty();
    }

    private Optional<Federation> tryP2shP2wshErpFederation(FederationArgs federationArgs,
        Address expectedFederationAddress) {
        try {
            ErpFederation p2shP2wshErpFederation = FederationFactory.buildP2shP2wshErpFederation(
                federationArgs, federationConstants.getErpFedPubKeysList(),
                federationConstants.getErpFedActivationDelay());

            if (p2shP2wshErpFederation.getAddress().equals(expectedFederationAddress)) {
                logger.debug(
                    "[getExpectedFederation] Expected federation is a p2sh-p2wsh erp one.");
                return Optional.of(p2shP2wshErpFederation);
            }
        } catch (ErpFederationCreationException | ScriptCreationException e) {
            logger.debug("[getExpectedFederation] Expected federation is not a p2sh-p2wsh erp one.",
                e);
        }
        return Optional.empty();
    }

    private List<FederationMember> buildFederationMembers(
        int federationSize,
        BiFunction<Integer, FederationMember.KeyType, ECKey> federatorPublicKeyGetter) {
        return IntStream.range(0, federationSize)
            .mapToObj(i -> buildFederationMember(i, federatorPublicKeyGetter)).toList();
    }

    private List<FederationMember> buildMembers(
        int federationSize,
        boolean useTypedPublicKeyGetter,
        BiFunction<Integer, FederationMember.KeyType, ECKey> typedKeyGetter,
        Function<Integer, BtcECKey> singleKeyGetter
    ) {
        return useTypedPublicKeyGetter
            ? buildFederationMembers(federationSize, typedKeyGetter)
            : buildSingleKeyFederationMembers(federationSize, singleKeyGetter);
    }

    private static List<FederationMember> buildSingleKeyFederationMembers(
        int federationSize,
        Function<Integer, BtcECKey> federatorSinglePublicKeyGetter
    ) {
        return IntStream.range(0, federationSize)
            .mapToObj(i -> buildSingleKeyFederationMember(i, federatorSinglePublicKeyGetter))
            .toList();
    }

    private static FederationMember buildSingleKeyFederationMember(int i,
        Function<Integer, BtcECKey> federatorSinglePublicKeyGetter) {
        BtcECKey btcKey = federatorSinglePublicKeyGetter.apply(i);
        ECKey rskMstKey = ECKey.fromPublicOnly(btcKey.getPubKey());
        return new FederationMember(btcKey, rskMstKey, rskMstKey);
    }

    private static FederationMember buildFederationMember(int i,
        BiFunction<Integer, KeyType, ECKey> federatorPublicKeyGetter) {
        BtcECKey btcKey = BtcECKey.fromPublicOnly(
            federatorPublicKeyGetter.apply(i, KeyType.BTC).getPubKey());
        ECKey rskKey = federatorPublicKeyGetter.apply(i, KeyType.RSK);
        ECKey mstKey = federatorPublicKeyGetter.apply(i, KeyType.MST);
        return new FederationMember(btcKey, rskKey, mstKey);
    }
}
