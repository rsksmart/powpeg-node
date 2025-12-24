package co.rsk.federate.signing.hsm.requirements;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.message.ReleaseCreationInformation;
import co.rsk.federate.signing.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReleaseRequirementsEnforcerTest {

    private AncestorBlockUpdater ancestorBlockUpdater;
    private ReleaseRequirementsEnforcer releaseRequirementsEnforcer;
    private static final HSMVersion hsmVersion = TestUtils.getLatestHsmVersion();

    @BeforeEach
    void setup() {
        ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        releaseRequirementsEnforcer = new ReleaseRequirementsEnforcer(ancestorBlockUpdater);
    }

    @Test
    void enforce_does_nothing_if_version_one() throws Exception {
        releaseRequirementsEnforcer.enforce(HSMVersion.V1.getNumber(), mock(ReleaseCreationInformation.class));
        verify(ancestorBlockUpdater, never()).ensureAncestorBlockInPosition(any());
    }

    @Test
    void enforce_ok() throws Exception {
        releaseRequirementsEnforcer.enforce(hsmVersion.getNumber(), mock(ReleaseCreationInformation.class));

        verify(ancestorBlockUpdater, times(1)).ensureAncestorBlockInPosition(any());
    }

    @Test
    void enforce_whenUpdaterFail_shouldThrowReleaseRequirementsEnforcerException() throws Exception {
        doThrow(new Exception()).when(ancestorBlockUpdater).ensureAncestorBlockInPosition(any());
        ReleaseRequirementsEnforcer enforcer = new ReleaseRequirementsEnforcer(ancestorBlockUpdater);

        assertThrows(ReleaseRequirementsEnforcerException.class, () -> enforcer.enforce(hsmVersion.getNumber(), mock(ReleaseCreationInformation.class)));
    }

    @Test
    void enforce_invalid_version() {
        assertThrows(ReleaseRequirementsEnforcerException.class, () -> releaseRequirementsEnforcer.enforce(-5, mock(ReleaseCreationInformation.class)));
    }
}
