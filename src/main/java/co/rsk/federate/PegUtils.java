package co.rsk.federate;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.ScriptException;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.script.*;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.PeginInformation;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.federation.ErpFederation;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PegUtils {
    public static final Coin MINIMUM_PEGIN_TX_VALUE = Coin.valueOf(500_000);
    private static final Logger logger = LoggerFactory.getLogger(PegUtils.class);

    private PegUtils() {}

    public static boolean isMigrationTx(
        BtcTransaction btcTx,
        Federation retiringFederation,
        Federation activeFederation
    ) {
        boolean moveFromRetiring = isPegOutTx(btcTx, retiringFederation);
        boolean moveToActive = allTxOutputsAreToFed(btcTx, activeFederation);

        return moveFromRetiring && moveToActive;
    }

    public static boolean isPegOutTx(BtcTransaction btcTx, Federation federation) {
        Script federationP2SHScript = getFederationStandardP2SHScript(federation);

        int inputsSize = btcTx.getInputs().size();
        for (int i = 0; i < inputsSize; i++) {
            Optional<Script> redeemScriptOptional = BitcoinUtils.extractRedeemScriptFromInput(btcTx, i);
            if (redeemScriptOptional.isEmpty()) {
                continue;
            }

            List<ScriptChunk> redeemScriptChunks = redeemScriptOptional.get().getChunks();
            // Extract standard redeem script chunks since the registered utxo could be from a fast bridge or erp federation
            try {
                RedeemScriptParser redeemScriptParser = RedeemScriptParserFactory.get(redeemScriptChunks);
                redeemScriptChunks = redeemScriptParser.extractStandardRedeemScriptChunks();
            } catch (ScriptException e) {
                logger.debug("[isPegOutTx] There is no redeem script", e);
                continue;
            }

            Script redeemScript = new ScriptBuilder().addChunks(redeemScriptChunks).build();
            Script p2shOutputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
            Script p2wshOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(redeemScript);

            if (federationP2SHScript.equals(p2shOutputScript) || federationP2SHScript.equals(p2wshOutputScript)) {
                return true;
            }
        }

        return false;
    }

    private static Script getFederationStandardP2SHScript(Federation federation) {
        if (!(federation instanceof ErpFederation)) {
            return federation.getP2SHScript();
        }

        return ((ErpFederation) federation).getDefaultP2SHScript();
    }

    private static boolean allTxOutputsAreToFed(BtcTransaction btcTx, Federation federation) {
        for (TransactionOutput output : btcTx.getOutputs()) {
            Script fedOutputScript = federation.getP2SHScript();
            Script txOutputScript = output.getScriptPubKey();
            if (!fedOutputScript.equals(txOutputScript)) {
                return false;
            }
        }
        return true;
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
