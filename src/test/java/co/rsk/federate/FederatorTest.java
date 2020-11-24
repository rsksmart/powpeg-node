package co.rsk.federate;

import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.KeyId;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.security.Key;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by ajlopez on 12/01/2017.
 */
public class FederatorTest {

    private FederatorPeersChecker peersChecker;
    private ECDSASigner signer;
    private Federator federator;
    private List<KeyId> requiredKeys;

    @Before
    public void init() throws IOException {
        signer = mock(ECDSASigner.class);
        peersChecker = mock(FederatorPeersChecker.class);
        requiredKeys = Arrays.asList(new KeyId("a-key-id"), new KeyId("another-key-id"));
        federator = new Federator(signer, requiredKeys, peersChecker);
    }

    @Test
    public void validWhenCheckerAndSignerValid() throws UnknownHostException {
        when(peersChecker.checkPeerAddresses()).thenReturn(Collections.emptyList());
        when(signer.check()).thenReturn(getCheckResult(true));
        when(signer.canSignWith(any(KeyId.class))).thenReturn(true);

        Assert.assertTrue(federator.validFederator());
    }

    @Test
    public void invalidWhenCheckerInvalid() throws UnknownHostException {
        when(peersChecker.checkPeerAddresses()).thenReturn(Arrays.asList("error A", "error B"));
        when(signer.check()).thenReturn(getCheckResult(true));
        when(signer.canSignWith(any(KeyId.class))).thenReturn(true);

        Assert.assertFalse(federator.validFederator());
    }

    @Test
    public void invalidWhenSignerInvalid() throws UnknownHostException {
        when(peersChecker.checkPeerAddresses()).thenReturn(Collections.emptyList());
        when(signer.check()).thenReturn(getCheckResult(false));
        when(signer.canSignWith(any(KeyId.class))).thenReturn(true);

        Assert.assertFalse(federator.validFederator());
    }

    @Test
    public void invalidWhenSignerIsMissingAKey() throws UnknownHostException {
        when(peersChecker.checkPeerAddresses()).thenReturn(Collections.emptyList());
        when(signer.check()).thenReturn(getCheckResult(true));
        when(signer.canSignWith(new KeyId("a-key-id"))).thenReturn(true);
        when(signer.canSignWith(new KeyId("another-key-id"))).thenReturn(false);

        Assert.assertFalse(federator.validFederator());
    }

    @Test
    public void invalidWhenCheckerAndSignerInalid() throws UnknownHostException {
        when(peersChecker.checkPeerAddresses()).thenReturn(Arrays.asList("error A", "error B"));
        when(signer.check()).thenReturn(getCheckResult(false));

        Assert.assertFalse(federator.validFederator());
    }

    private ECDSASigner.ECDSASignerCheckResult getCheckResult(boolean success) {
        List<String> messages = new ArrayList<>();
        if (!success) {
            messages.add("Error message #1");
            messages.add("Error message #2");
        }
        return new ECDSASigner.ECDSASignerCheckResult(messages);
    }
}
