package co.rsk.federate;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.ScriptException;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.script.*;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PeginInformation;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static co.rsk.peg.PegUtils.getFlyoverFederationRedeemScript;

public class PegUtils {
    public static final Coin MINIMUM_PEGIN_TX_VALUE = Coin.valueOf(500_000);
    private static final Logger logger = LoggerFactory.getLogger(PegUtils.class);

    private PegUtils() {}

    public static boolean isSVPSpendTx(
        BridgeConstants bridgeConstants,
        BtcTransaction btcTx,
        Federation proposedFederation,
        Federation activeFederation
    ) {
        int svpSpendTxInputsCount = 2;
        if (btcTx.getInputs().size() != svpSpendTxInputsCount) {
            return false;
        }

        int svpSpendTxOutputsCount = 1;
        if (btcTx.getOutputs().size() != svpSpendTxOutputsCount) {
            return false;
        }

        Script proposedFederationRedeemScript = proposedFederation.getRedeemScript();
        int proposedFedInputIndex = 0;
        Script proposedFedScript = ScriptBuilder.createP2SHP2WSHOutputScript(proposedFederationRedeemScript);

        int flyoverProposedFedInputIndex = 1;
        Keccak256 proposedFederationFlyoverPrefix = bridgeConstants.getProposedFederationFlyoverPrefix();
        Script flyoverProposedFederationRedeemScript = getFlyoverFederationRedeemScript(proposedFederationFlyoverPrefix, proposedFederationRedeemScript);
        Script flyoverProposedFedScript = ScriptBuilder.createP2SHP2WSHOutputScript(flyoverProposedFederationRedeemScript);

        int activeFedOutputIndex = 0;
        Script activeFedScript = activeFederation.getP2SHScript();

        return isInputFromScript(proposedFedScript, btcTx, proposedFedInputIndex)
            && isInputFromScript(flyoverProposedFedScript, btcTx, flyoverProposedFedInputIndex)
            && isOutputToScript(activeFedScript, btcTx.getOutput(activeFedOutputIndex));
    }

    public static boolean isMigrationTx(
        BtcTransaction btcTx,
        Federation retiringFederation,
        Federation activeFederation
    ) {
        boolean moveFromRetiring = isPegOutTx(btcTx, retiringFederation);
        boolean moveToActive = allTxOutputsAreToFed(btcTx, activeFederation);

        return moveFromRetiring && moveToActive;
    }

    public static boolean isPegOutTx(BtcTransaction btcTx, Federation activeFederation) {
        Script federationP2SHScript = getFederationStandardP2SHScript(activeFederation);

        int inputsSize = btcTx.getInputs().size();
        for (int i = 0; i < inputsSize; i++) {
            if (isInputFromScript(federationP2SHScript, btcTx, i)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isInputFromScript(Script expectedSpendingScript, BtcTransaction btcTx, int inputIndex) {
        Optional<Script> redeemScriptOptional = BitcoinUtils.extractRedeemScriptFromInput(btcTx, inputIndex);
        if (redeemScriptOptional.isEmpty()) {
            return false;
        }

        List<ScriptChunk> redeemScriptChunks = redeemScriptOptional.get().getChunks();
        // Extract standard redeem script chunks since the registered utxo could be from a fast bridge or erp federation
        try {
            RedeemScriptParser redeemScriptParser = RedeemScriptParserFactory.get(redeemScriptChunks);
            redeemScriptChunks = redeemScriptParser.extractStandardRedeemScriptChunks();
        } catch (ScriptException e) {
            logger.debug("[isPegOutTx] There is no redeem script", e);
            return false;
        }

        Script redeemScript = new ScriptBuilder().addChunks(redeemScriptChunks).build();
        Script p2shOutputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        Script p2wshOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(redeemScript);
        return expectedSpendingScript.equals(p2shOutputScript) || expectedSpendingScript.equals(p2wshOutputScript);
    }

    private static Script getFederationStandardP2SHScript(Federation federation) {
        if (!(federation instanceof ErpFederation)) {
            return federation.getP2SHScript();
        }

        return ((ErpFederation) federation).getDefaultP2SHScript();
    }

    private static boolean allTxOutputsAreToFed(BtcTransaction btcTx, Federation federation) {
        for (TransactionOutput output : btcTx.getOutputs()) {
            if (!isOutputToScript(federation.getP2SHScript(), output)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOutputToScript(Script expectedOutputScript, TransactionOutput output) {
        Script txOutputScript = output.getScriptPubKey();
        return expectedOutputScript.equals(txOutputScript);
    }

    public static boolean isValidPegInTx(BtcTransaction btcTx, Wallet federationWallet, PeginInformation peginInformation) {
        boolean isAnyValueSentBelowMinimumPeginValue = !allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            federationWallet
        );
        if (isAnyValueSentBelowMinimumPeginValue) {
            return false;
        }

        return isPegInTx(btcTx, peginInformation);
    }

    private static boolean allUTXOsToFedAreAboveMinimumPeginValue(
        BtcTransaction btcTx,
        Wallet fedWallet
    ) {
        List<co.rsk.bitcoinj.core.TransactionOutput> fedUtxos = btcTx.getWalletOutputs(fedWallet);
        if (fedUtxos.isEmpty()) {
            return false;
        }

        return fedUtxos.stream().allMatch(transactionOutput ->
            transactionOutput.getValue().compareTo(PegUtils.MINIMUM_PEGIN_TX_VALUE) >= 0
        );
    }

    private static boolean isPegInTx(BtcTransaction btcTx, PeginInformation peginInformation) {
        try {
            peginInformation.parse(btcTx);
        } catch (PeginInstructionsException e) {
            // pegin instructions exception is only being thrown when a pegin has an INVALID op return output structure
            // meaning if there's NO op return found, or the protocol version is unknown, it won't throw.
            co.rsk.bitcoinj.core.Address refundAddress = peginInformation.getSenderBtcAddress();
            // if there's a refund address, i.e., refundAddress is not null, tx should be sent
            return refundAddress != null;
        }

        // at this point, tx could be a legacy pegin or a pegin with instructions with valid op return structure
        if (isLegacyPegin(peginInformation)) {
            // if there's a valid lock sender, i.e., sender address type is not UNKNOWN, tx should be sent
            return peginInformation.getSenderBtcAddressType() != BtcLockSender.TxSenderAddressType.UNKNOWN;
        }

        // at this point, tx is a pegin with instructions with valid structure,
        // but we just want to send it if the protocol version is valid
        return isPeginWithInstructionsProtocolVersionValid(peginInformation);
    }

    private static boolean isLegacyPegin(PeginInformation peginInformation) {
        return peginInformation.getProtocolVersion() == 0;
    }

    private static boolean isPeginWithInstructionsProtocolVersionValid(PeginInformation peginInformation) {
        return peginInformation.getProtocolVersion() == 1;
    }
}
