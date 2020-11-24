package co.rsk.federate;

import co.rsk.bitcoinj.core.Address;
import co.rsk.peg.Federation;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Watches the RSK blockchain for federation changes, and informs
 * when a federation changes.
 * @author Ariel Mendelzon
 */
public class FederationWatcher {
    private static final Logger logger = LoggerFactory.getLogger("FederationWatcher");

    private final Ethereum rsk;

    private FederationProvider federationProvider;

    private List<Listener> listeners = new ArrayList<>();

    // Previous recorded federations
    private Optional<Federation> activeFederation = Optional.empty();
    private Optional<Federation> retiringFederation = Optional.empty();

    public FederationWatcher(Ethereum rsk) {
        this.rsk = rsk;
    }

    public interface Listener {
        void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation);
        void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation);
    }

    public void setup(FederationProvider federationProvider) throws Exception {
        this.federationProvider = federationProvider;
        rsk.addListener(new FederationWatcherRskListener());
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private class FederationWatcherRskListener extends EthereumListenerAdapter {
        @Override
        public void onBestBlock(org.ethereum.core.Block block, List<TransactionReceipt> receipts) {
            // Updating state only when the best block changes still "works",
            // since we're interested in finding out only when the active or retiring federation(s) changed.
            // If there was a side chain in which any of these changed in, say, block 4500, but
            // that side chain became main chain in block 7800, it would still be ok to
            // start monitoring the new federation(s) on that block.
            // The main reasons for this is that RSK nodes would have never reported the active or
            // retiring federation(s) being different *before* the best chain change. Therefore,
            // there should be no new Bitcoin transactions directed to these new addresses
            // until this change effectively becomes a part of RSK's "reality".
            // The case in which we go back and forth to and from a sidechain in which the
            // federation effectively changed is still to be explored deeply, but the same reasoning
            // should apply since going back and forth would trigger two federation changes.
            // A client trying to send bitcoins to the new federation without waiting
            // a good number of confirmations would be, essentially, "playing with fire".
            logger.info("New best block, updating state");
            updateState();
        }
    }

    public void updateState() {
        // Active federation changed?
        // We compare addresses first so as not to do innecessary calls to the bridge
        Address currentlyActiveFederationAddress = federationProvider.getActiveFederationAddress();
        if (!currentlyActiveFederationAddress.equals(activeFederation.map(Federation::getAddress).orElse(null))) {
            // Gather the active federation and inform listeners of the change
            Federation currentlyActiveFederation = federationProvider.getActiveFederation();
            String oldActive = activeFederation.map(f -> f.getAddress().toString()).orElse("NONE");
            String newActive = currentlyActiveFederation.getAddress().toString();
            logger.info(String.format("Active federation changed from %s to %s", oldActive, newActive));
            for (Listener l : listeners) {
                l.onActiveFederationChange(activeFederation, currentlyActiveFederation);
            }
            activeFederation = Optional.of(currentlyActiveFederation);
        }

        // Retiring federation changed?
        // We compare addresses first so as not to do innecessary calls to the bridge
        Optional<Address> currentlyRetiringFederationAddress = federationProvider.getRetiringFederationAddress();
        if (!retiringFederation.map(Federation::getAddress).equals(currentlyRetiringFederationAddress)) {
            // Gather the retiring federation and inform listeners of the change
            Optional<Federation> currentlyRetiringFederation = federationProvider.getRetiringFederation();
            String oldRetiring = retiringFederation.map(f -> f.getAddress().toString()).orElse("NONE");
            String newRetiring = currentlyRetiringFederation.map(f -> f.getAddress().toString()).orElse("NONE");
            logger.info(String.format("Retiring federation changed from %s to %s", oldRetiring, newRetiring));
            for (Listener l : listeners) {
                l.onRetiringFederationChange(retiringFederation, currentlyRetiringFederation);
            }
            retiringFederation = currentlyRetiringFederation;
        }
    }
}
