package co.rsk.federate.signing.hsm.requirements;

import co.rsk.federate.signing.hsm.message.ReleaseCreationInformation;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ReleaseRequirementsEnforcerTest {

    private AncestorBlockUpdater ancestorBlockUpdater;
    private ReleaseRequirementsEnforcer enforcer;

    @Before
    public void setup() {
        ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        enforcer = new ReleaseRequirementsEnforcer(ancestorBlockUpdater);
    }

    @Test
    public void enforce_does_nothing_if_version_one() throws Exception {
        AncestorBlockUpdater ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        ReleaseRequirementsEnforcer enforcer = new ReleaseRequirementsEnforcer(ancestorBlockUpdater);

        enforcer.enforce(1, mock(ReleaseCreationInformation.class));

        verify(ancestorBlockUpdater, never()).ensureAncestorBlockInPosition(any());
    }

    @Test
    public void enforce_version_two_ok() throws Exception {
        test_enforce_version(ancestorBlockUpdater, enforcer, 2);
    }

    @Test
    public void enforce_version_three_ok() throws Exception {
        test_enforce_version(ancestorBlockUpdater, enforcer, 3);
    }

    @Test
    public void enforce_version_four_ok() throws Exception {
        test_enforce_version(ancestorBlockUpdater, enforcer, 4);
    }

    public void test_enforce_version(AncestorBlockUpdater ancestorBlockUpdater, ReleaseRequirementsEnforcer enforcer, int version) throws Exception {
        enforcer.enforce(version, mock(ReleaseCreationInformation.class));

        verify(ancestorBlockUpdater, times(1)).ensureAncestorBlockInPosition(any());
    }

    @Test(expected = ReleaseRequirementsEnforcerException.class)
    public void enforce_version_two_updater_fails() throws Exception {
        AncestorBlockUpdater ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        doThrow(new Exception()).when(ancestorBlockUpdater).ensureAncestorBlockInPosition(any());
        ReleaseRequirementsEnforcer enforcer = new ReleaseRequirementsEnforcer(ancestorBlockUpdater);

        enforcer.enforce(2, mock(ReleaseCreationInformation.class));
    }

    @Test(expected = ReleaseRequirementsEnforcerException.class)
    public void enforce_invalid_version() throws Exception {
        ReleaseRequirementsEnforcer enforcer = new ReleaseRequirementsEnforcer(mock(AncestorBlockUpdater.class));

        enforcer.enforce(-5, mock(ReleaseCreationInformation.class));
    }
}
