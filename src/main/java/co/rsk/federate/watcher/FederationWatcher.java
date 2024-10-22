package co.rsk.federate.watcher;

import co.rsk.bitcoinj.core.Address;
import co.rsk.federate.FederationProvider;
import co.rsk.peg.federation.Federation;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;

/**
 * Watches the RSK blockchain for changes to the active and retiring federations.
 * This class listens for new blocks in the RSK blockchain and checks if the active or
 * retiring federations have changed, notifying listeners when such changes occur.
 */
public class FederationWatcher {

    private static final Logger logger = LoggerFactory.getLogger(FederationWatcher.class);

    private final Ethereum rsk;

    private FederationProvider federationProvider;
    private FederationWatcherListener federationWatcherListener; 

    private Federation activeFederation;
    private Federation retiringFederation;

    /**
     * Constructs a new {@code FederationWatcher} with the specified RSK client.
     *
     * @param rsk the Ethereum client used to listen for new blocks on the RSK blockchain
     */
    public FederationWatcher(Ethereum rsk) {
        this.rsk = rsk;
    }

    /**
     * Starts the {@code FederationWatcher} by setting the {@code FederationProvider}
     * and {@code FederationWatcherListener}, and begins listening for new blocks on
     * the RSK blockchain.
     *
     * @param federationProvider        the provider used to obtain federation information (active and retiring federations)
     * @param federationWatcherListener the listener that will be notified when the federations change
     */
    public void start(FederationProvider federationProvider, FederationWatcherListener federationWatcherListener) {
        this.federationProvider = federationProvider;
        this.federationWatcherListener = federationWatcherListener;
     
        rsk.addListener(new EthereumListenerAdapter() {
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
                logger.info("[updateState] New best block, updating state");
                updateState();
            }
        });
    }

    /**
     * Updates the current state of the federations by checking if the active or
     * retiring federations have changed. If a federation change is detected, it notifies
     * the {@code FederationWatcherListener}.
     */
    private void updateState() {
        updateActiveFederation();
        updateRetiringFederation();
    }

    private void updateActiveFederation() {
        // Check if active federation has changed
        Optional<Address> currentlyActiveFederationAddress = Optional.ofNullable(federationProvider.getActiveFederationAddress());
        Optional<Address> oldActiveFederationAddress = Optional.ofNullable(activeFederation).map(Federation::getAddress);
        boolean hasActiveFederationChanged = !currentlyActiveFederationAddress.equals(oldActiveFederationAddress);

        if (hasActiveFederationChanged) {
            // Active federation changed
            logger.info("[updateActiveFederation] Active federation changed from {} to {}",
                oldActiveFederationAddress.orElse(null),
                currentlyActiveFederationAddress.orElse(null));

            Federation currentlyActiveFederation = federationProvider.getActiveFederation();

            // Notify listener and update state
            federationWatcherListener.onActiveFederationChange(currentlyActiveFederation);
            this.activeFederation = currentlyActiveFederation;
        }
    }

    private void updateRetiringFederation() {
        // Check if retiring federation has changed
        Optional<Address> currentlyRetiringFederationAddress = federationProvider.getRetiringFederationAddress();
        Optional<Address> oldRetiringFederationAddress = Optional.ofNullable(retiringFederation).map(Federation::getAddress);
        boolean hasRetiringFederationChanged = !currentlyRetiringFederationAddress.equals(oldRetiringFederationAddress);

        if (hasRetiringFederationChanged) {
            // Retiring federation changed
            logger.info("[updateRetiringFederation] Retiring federation changed from {} to {}",
                    oldRetiringFederationAddress.orElse(null),
                    currentlyRetiringFederationAddress.orElse(null));

            Federation currentlyRetiringFederation = federationProvider.getRetiringFederation().orElse(null);

            // Notify listener and update state
            federationWatcherListener.onRetiringFederationChange(currentlyRetiringFederation);
            this.retiringFederation = currentlyRetiringFederation;
        }
    }    
}
