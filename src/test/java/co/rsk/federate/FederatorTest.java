package co.rsk.federate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.KeyId;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 12/01/2017.
 */
class FederatorTest {

    private FederatorPeersChecker peersChecker;
    private ECDSASigner signer;
    private Federator federator;

    @BeforeEach
    void init() {
        signer = mock(ECDSASigner.class);
        peersChecker = mock(FederatorPeersChecker.class);

        List<KeyId> requiredKeys = Arrays.asList(
            new KeyId("a-key-id"),
            new KeyId("another-key-id")
        );
        federator = new Federator(signer, requiredKeys, peersChecker);
    }

    @Test
    void validWhenCheckerAndSignerValid() throws UnknownHostException {
        when(peersChecker.checkPeerAddresses()).thenReturn(Collections.emptyList());
        when(signer.check()).thenReturn(getCheckResult(true));
        when(signer.canSignWith(any(KeyId.class))).thenReturn(true);

        assertTrue(federator.validFederator());
    }

    @Test
    void invalidWhenCheckerInvalid() throws UnknownHostException {
        when(peersChecker.checkPeerAddresses()).thenReturn(Arrays.asList("error A", "error B"));
        when(signer.check()).thenReturn(getCheckResult(true));
        when(signer.canSignWith(any(KeyId.class))).thenReturn(true);

        assertFalse(federator.validFederator());
    }

    @Test
    void invalidWhenSignerInvalid() throws UnknownHostException {
        when(peersChecker.checkPeerAddresses()).thenReturn(Collections.emptyList());
        when(signer.check()).thenReturn(getCheckResult(false));
        when(signer.canSignWith(any(KeyId.class))).thenReturn(true);

        assertFalse(federator.validFederator());
    }

    @Test
    void invalidWhenSignerIsMissingAKey() throws UnknownHostException {
        when(peersChecker.checkPeerAddresses()).thenReturn(Collections.emptyList());
        when(signer.check()).thenReturn(getCheckResult(true));
        when(signer.canSignWith(new KeyId("a-key-id"))).thenReturn(true);
        when(signer.canSignWith(new KeyId("another-key-id"))).thenReturn(false);

        assertFalse(federator.validFederator());
    }

    @Test
    void invalidWhenCheckerAndSignerInalid() throws UnknownHostException {
        when(peersChecker.checkPeerAddresses()).thenReturn(Arrays.asList("error A", "error B"));
        when(signer.check()).thenReturn(getCheckResult(false));

        assertFalse(federator.validFederator());
    }

    private ECDSASigner.ECDSASignerCheckResult getCheckResult(boolean success) {
        List<String> messages = new ArrayList<>();
        if (!success) {
            messages.add("Error message #1");
            messages.add("Error message #2");
        }
        return new ECDSASigner.ECDSASignerCheckResult(messages);
    }

    @Test
    void testDummyTest_shouldPass() {
        assertFalse(false);
    }

}
