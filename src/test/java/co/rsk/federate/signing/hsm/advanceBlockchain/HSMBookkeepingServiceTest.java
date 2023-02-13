package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.config.PowHSMBookkeepingConfig;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMDeviceException;
import co.rsk.federate.signing.hsm.HSMInvalidResponseException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.message.AdvanceBlockchainMessage;
import co.rsk.federate.signing.hsm.message.HSM2State;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.net.NodeBlockProcessor;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

public class HSMBookkeepingServiceTest {

    // Start
    @Test
    public void start_ok() throws InterruptedException, HSMClientException {

        PowHSMBookkeepingConfig mockPowHsmBookkeepingConfig = mock(PowHSMBookkeepingConfig.class);
        when(mockPowHsmBookkeepingConfig.isStopBookkeepingScheduler()).thenReturn(false);
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        HSMBookkeepingService service = new HSMBookkeepingService(
                mock(BlockStore.class),
                mockHsmBookkeepingClient,
                mock(ConfirmedBlockHeadersProvider.class),
                mock(NodeBlockProcessor.class),
                2_000,
                mockPowHsmBookkeepingConfig
        );

        service.start();
        Thread.sleep(10);

        Assert.assertTrue(service.isStarted());
    }

    @Test
    public void start_already_started_service() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);
        PowHSMBookkeepingConfig mockPowHsmBookkeepingConfig = mock(PowHSMBookkeepingConfig.class);
        when(mockPowHsmBookkeepingConfig.isStopBookkeepingScheduler()).thenReturn(false);

        HSMBookkeepingService service = new HSMBookkeepingService(
                mock(BlockStore.class),
                mockHsmBookkeepingClient,
                mock(ConfirmedBlockHeadersProvider.class),
                mock(NodeBlockProcessor.class),
                2_000,
                mockPowHsmBookkeepingConfig
        );

        service.start();
        Thread.sleep(10);
        service.start();
        Thread.sleep(10);

        Assert.assertTrue(service.isStarted());
        verify(mockHsmBookkeepingClient, times(1)).getHSMPointer();
    }

    @Test
    public void start_with_stopBookkepingConf_True() throws InterruptedException, HSMClientException {
        PowHSMBookkeepingConfig mockPowHsmBookkeepingConfig = mock(PowHSMBookkeepingConfig.class);
        when(mockPowHsmBookkeepingConfig.isStopBookkeepingScheduler()).thenReturn(true);
        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService service = new HSMBookkeepingService(
                mock(BlockStore.class),
                mock(HSMBookkeepingClient.class),
                mock(ConfirmedBlockHeadersProvider.class),
                mock(NodeBlockProcessor.class),
                2_000,
                mockPowHsmBookkeepingConfig
        );

        service.addListener(mockListener);
        service.start();
        Thread.sleep(10);

        Assert.assertFalse(service.isStarted());
        verifyZeroInteractions(mockListener);
    }

    @Test
    public void start_with_stopBookkepingConf_False() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        PowHSMBookkeepingConfig mockPowHsmBookkeepingConfig = mock(PowHSMBookkeepingConfig.class);
        when(mockPowHsmBookkeepingConfig.isStopBookkeepingScheduler()).thenReturn(false);

        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService service = new HSMBookkeepingService(
                mock(BlockStore.class),
                mockHsmBookkeepingClient,
                mock(ConfirmedBlockHeadersProvider.class),
                mock(NodeBlockProcessor.class),
                2_000,
                mockPowHsmBookkeepingConfig
        );

        service.addListener(mockListener);
        service.start();
        Thread.sleep(10);

        Assert.assertTrue(service.isStarted());
        verifyZeroInteractions(mockListener);
    }

    @Test
    public void start_neededReset_Ok() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), true);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService service = new HSMBookkeepingService(
                mock(BlockStore.class),
                mockHsmBookkeepingClient,
                mock(ConfirmedBlockHeadersProvider.class),
                mock(NodeBlockProcessor.class),
                2_000,
                mock(PowHSMBookkeepingConfig.class)
        );

        service.addListener(mockListener);
        service.start();
        Thread.sleep(10);

        Assert.assertTrue(service.isStarted());
        verifyZeroInteractions(mockListener);
        verify(mockHsmBookkeepingClient, times(1)).resetAdvanceBlockchain();
    }

    @Test
    public void start_neededReset_FailGettingState() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenThrow(new HSMInvalidResponseException(""));

        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService service = new HSMBookkeepingService(
                mock(BlockStore.class),
                mockHsmBookkeepingClient,
                mock(ConfirmedBlockHeadersProvider.class),
                mock(NodeBlockProcessor.class),
                2_000,
                mock(PowHSMBookkeepingConfig.class)
        );

        service.addListener(mockListener);
        service.start();
        Thread.sleep(10);

        Assert.assertFalse(service.isStarted());
        verify(mockListener, times(1)).onIrrecoverableError(any());
    }

    @Test
    public void start_neededReset_FailReset() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), true);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);
        doThrow(new HSMInvalidResponseException("")).when(mockHsmBookkeepingClient).resetAdvanceBlockchain();

        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService service = new HSMBookkeepingService(
                mock(BlockStore.class),
                mockHsmBookkeepingClient,
                mock(ConfirmedBlockHeadersProvider.class),
                mock(NodeBlockProcessor.class),
                2_000,
                mock(PowHSMBookkeepingConfig.class)
        );

        service.addListener(mockListener);
        service.start();
        Thread.sleep(10);

        Assert.assertFalse(service.isStarted());
        verify(mockHsmBookkeepingClient, times(1)).resetAdvanceBlockchain();
        verify(mockListener, times(1)).onIrrecoverableError(any());
    }

    @Test
    public void start_fails_on_scheduler_startup() throws HSMClientException, InterruptedException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        // Configuring the Scheduler with 0ms interval throws an IllegalArgumentException
        HSMBookkeepingService service = new HSMBookkeepingService(
                mock(BlockStore.class),
                mockHsmBookkeepingClient,
                mock(ConfirmedBlockHeadersProvider.class),
                mock(NodeBlockProcessor.class),
                0,
                mock(PowHSMBookkeepingConfig.class)
        );

        HSMBookeepingServiceListener listener = mock(HSMBookeepingServiceListener.class);
        service.addListener(listener);

        service.start();
        Thread.sleep(10);

        // Scheduler error is captured and the listener's onIrrecoverableError method is invoked
        verify(listener, times(1)).onIrrecoverableError(any());
        Assert.assertFalse(service.isStarted());
    }

    // Stop
    @Test
    public void stop_Ok() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);
        PowHSMBookkeepingConfig mockPowHsmBookkeepingConfig = mock(PowHSMBookkeepingConfig.class);
        when(mockPowHsmBookkeepingConfig.isStopBookkeepingScheduler()).thenReturn(false);

        HSMBookkeepingService service = new HSMBookkeepingService(
                mock(BlockStore.class),
                mockHsmBookkeepingClient,
                mock(ConfirmedBlockHeadersProvider.class),
                mock(NodeBlockProcessor.class),
                2_000,
                mockPowHsmBookkeepingConfig
        );

        service.start();
        Thread.sleep(10);
        Assert.assertTrue(service.isStarted());

        service.stop();
        Thread.sleep(10);

        Assert.assertFalse(service.isStarted());
    }

    @Test
    public void stop_already_stopped_service() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient hsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(hsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        HSMBookkeepingService service = new HSMBookkeepingService(
                mock(BlockStore.class),
                hsmBookkeepingClient,
                mock(ConfirmedBlockHeadersProvider.class),
                mock(NodeBlockProcessor.class),
                2_000,
                mock(PowHSMBookkeepingConfig.class)
        );

        service.stop();
        Thread.sleep(10);
        Assert.assertFalse(service.isStarted());

        service.start();
        Thread.sleep(10);
        Assert.assertTrue(service.isStarted());

        service.stop();
        Thread.sleep(10);

        Assert.assertFalse(service.isStarted());
    }

    // Add and remove Listener
    @Test
    public void addListener_Listener_Working_Ok() throws HSMClientException, InterruptedException {
        HSMBookkeepingClient hsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        when(hsmBookkeepingClient.getHSMPointer()).thenThrow(new HSMDeviceException("", 1));

        PowHSMBookkeepingConfig mockPowHsmBookkeepingConfig = mock(PowHSMBookkeepingConfig.class);
        when(mockPowHsmBookkeepingConfig.isStopBookkeepingScheduler()).thenReturn(false);

        HSMBookkeepingService service = new HSMBookkeepingService(
                mock(BlockStore.class),
                hsmBookkeepingClient,
                mock(ConfirmedBlockHeadersProvider.class),
                mock(NodeBlockProcessor.class),
                2_000,
                mockPowHsmBookkeepingConfig
        );

        HSMBookeepingServiceListener listener = mock(HSMBookeepingServiceListener.class);
        service.addListener(listener);

        service.start();
        Thread.sleep(10);

        verify(listener, times(1)).onIrrecoverableError(any());
    }

    @Test
    public void removeListener_Ok() throws HSMClientException, InterruptedException {
        HSMBookkeepingClient hsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        when(hsmBookkeepingClient.getHSMPointer()).thenThrow(new HSMDeviceException("", 1));

        PowHSMBookkeepingConfig mockPowHsmBookkeepingConfig = mock(PowHSMBookkeepingConfig.class);
        when(mockPowHsmBookkeepingConfig.isStopBookkeepingScheduler()).thenReturn(false);

        HSMBookkeepingService service = new HSMBookkeepingService(
                mock(BlockStore.class),
                hsmBookkeepingClient,
                mock(ConfirmedBlockHeadersProvider.class),
                mock(NodeBlockProcessor.class),
                2_000,
                mockPowHsmBookkeepingConfig
        );

        HSMBookeepingServiceListener listener = mock(HSMBookeepingServiceListener.class);
        service.addListener(listener);
        service.removeListener(listener);

        service.start();
        Thread.sleep(10);

        // Assert
        verify(listener, never()).onIrrecoverableError(any());
        verifyZeroInteractions(listener);
    }

    // informConfirmedBlockHeaders
    @Test
    public void informConfirmedBlockHeaders_onIrrecoverableError() throws HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);
        doThrow(new HSMInvalidResponseException("")).when(mockHsmBookkeepingClient).advanceBlockchain(any());

        BlockStore mockBlockStore = mock(BlockStore.class);
        when(mockBlockStore.getBlockByHash(any())).thenReturn(mock(Block.class));

        List<BlockHeader> blockHeaders = new ArrayList<>();
        blockHeaders.add(TestUtils.createBlockHeaderMock(1));

        ConfirmedBlockHeadersProvider mockConfirmedBlockHeadersProvider = mock(ConfirmedBlockHeadersProvider.class);
        when(mockConfirmedBlockHeadersProvider.getConfirmedBlockHeaders(any())).thenReturn(blockHeaders);

        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService service = new HSMBookkeepingService(
                mockBlockStore,
                mockHsmBookkeepingClient,
                mockConfirmedBlockHeadersProvider,
                mock(NodeBlockProcessor.class),
                2_000,
                mock(PowHSMBookkeepingConfig.class)

        );

        service.addListener(mockListener);
        service.informConfirmedBlockHeaders();

        // Assert
        verify(mockHsmBookkeepingClient, times(1)).getHSMPointer();
        verify(mockListener, times(1)).onIrrecoverableError(any());
    }

    @Test
    public void informConfirmedBlockHeaders_already_informing()
        throws HSMClientException, InterruptedException {
        BlockHeader mockBlockHeaderToInform = TestUtils.createBlockHeaderMock(1);
        when(mockBlockHeaderToInform.getFullEncoded()).thenReturn(Keccak256.ZERO_HASH.getBytes());
        List<BlockHeader> blockHeadersToInform = Arrays.asList(mockBlockHeaderToInform);

        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);
        // Delay informing to be able to force a second call
        doAnswer(a -> {
            Thread.sleep(1000);
            return null;
        }).when(mockHsmBookkeepingClient).advanceBlockchain(any(AdvanceBlockchainMessage.class));

        BlockStore mockBlockStore = mock(BlockStore.class);
        when(mockBlockStore.getBlockByHash(any())).thenReturn(mock(Block.class));

        ConfirmedBlockHeadersProvider mockConfirmedBlockHeadersProvider = mock(ConfirmedBlockHeadersProvider.class);
        when(mockConfirmedBlockHeadersProvider.getConfirmedBlockHeaders(any())).thenReturn(blockHeadersToInform);

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);

        HSMBookkeepingService hsmBookkeepingService = new HSMBookkeepingService(
            mockBlockStore,
            mockHsmBookkeepingClient,
            mockConfirmedBlockHeadersProvider,
            nodeBlockProcessor,
            2_000,
            mock(PowHSMBookkeepingConfig.class)
        );

        // Delegate execution to different thread to be able to call second inform immediately
        new Thread(hsmBookkeepingService::informConfirmedBlockHeaders).start();
        // Let some time so the thread starts
        Thread.sleep(100);
        // But not enough for the task to finish
        hsmBookkeepingService.informConfirmedBlockHeaders();

        // Assert
        verify(nodeBlockProcessor, times(1)).hasBetterBlockToSync();
        verify(mockHsmBookkeepingClient, times(1)).advanceBlockchain(any(AdvanceBlockchainMessage.class));
    }

    @Test
    public void informConfirmedBlockHeaders_Ok() throws InterruptedException, HSMClientException {
        BlockHeader mockBlockHeaderToInform = TestUtils.createBlockHeaderMock(1);
        when(mockBlockHeaderToInform.getFullEncoded()).thenReturn(Keccak256.ZERO_HASH.getBytes());
        List<BlockHeader> blockHeadersToInform = Arrays.asList(mockBlockHeaderToInform);

        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        BlockStore mockBlockStore = mock(BlockStore.class);
        when(mockBlockStore.getBlockByHash(any())).thenReturn(mock(Block.class));

        ConfirmedBlockHeadersProvider mockConfirmedBlockHeadersProvider = mock(ConfirmedBlockHeadersProvider.class);
        when(mockConfirmedBlockHeadersProvider.getConfirmedBlockHeaders(any())).thenReturn(blockHeadersToInform);

        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService hsmBookkeepingService = new HSMBookkeepingService(
                mockBlockStore,
                mockHsmBookkeepingClient,
                mockConfirmedBlockHeadersProvider,
                mock(NodeBlockProcessor.class),
                2_000,
                mock(PowHSMBookkeepingConfig.class)
        );

        hsmBookkeepingService.addListener(mockListener);
        hsmBookkeepingService.start();

        Thread.sleep(150);

        hsmBookkeepingService.informConfirmedBlockHeaders();

        // Assert
        Assert.assertTrue(hsmBookkeepingService.isStarted());
        verify(mockHsmBookkeepingClient, times(1)).advanceBlockchain(any(AdvanceBlockchainMessage.class));
        verify(mockBlockStore, times(2)).getBlockByHash(any());
        verifyZeroInteractions(mockListener);
    }

    @Test
    public void informConfirmedBlockHeaders_hasBetterBlockToSync_true() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);
        NodeBlockProcessor mockNodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(mockNodeBlockProcessor.hasBetterBlockToSync()).thenReturn(true);

        HSMBookkeepingService hsmBookkeepingService = new HSMBookkeepingService(
                mock(BlockStore.class),
                mockHsmBookkeepingClient,
                mock(ConfirmedBlockHeadersProvider.class),
                mockNodeBlockProcessor,
                2_000,
                mock(PowHSMBookkeepingConfig.class)
        );

        hsmBookkeepingService.addListener(mockListener);

        Thread.sleep(150);

        hsmBookkeepingService.informConfirmedBlockHeaders();

        // Assert
        verify(mockNodeBlockProcessor, times(1)).hasBetterBlockToSync();
        verifyZeroInteractions(mockHsmBookkeepingClient);
        verifyZeroInteractions(mockListener);
    }

    @Test
    public void informConfirmedBlockHeaders_getHsmBestBlock_null() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);
        BlockStore mockBlockStore = mock(BlockStore.class);
        when(mockBlockStore.getBlockByHash(any())).thenReturn(null);

        ConfirmedBlockHeadersProvider mockConfirmedBlockHeadersProvider = mock(ConfirmedBlockHeadersProvider.class);

        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService hsmBookkeepingService = new HSMBookkeepingService(
                mockBlockStore,
                mockHsmBookkeepingClient,
                mockConfirmedBlockHeadersProvider,
                mock(NodeBlockProcessor.class),
                2_000,
                mock(PowHSMBookkeepingConfig.class)
        );

        hsmBookkeepingService.addListener(mockListener);

        Thread.sleep(150);

        hsmBookkeepingService.informConfirmedBlockHeaders();

        // Assert
        verify(mockHsmBookkeepingClient, times(1)).getHSMPointer();
        verifyNoMoreInteractions(mockHsmBookkeepingClient);
        verify(mockBlockStore, times(1)).getBlockByHash(any());
        verifyZeroInteractions(mockConfirmedBlockHeadersProvider);
        verifyZeroInteractions(mockListener);
    }

    @Test
    public void informConfirmedBlockHeaders_emptyBlockHeaders() throws InterruptedException, HSMClientException {
        BlockHeader mockBlockHeaderToInform = TestUtils.createBlockHeaderMock(1);
        when(mockBlockHeaderToInform.getFullEncoded()).thenReturn(Keccak256.ZERO_HASH.getBytes());

        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        HSM2State state = new HSM2State(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        BlockStore mockBlockStore = mock(BlockStore.class);
        when(mockBlockStore.getBlockByHash(any())).thenReturn(mock(Block.class));

        ConfirmedBlockHeadersProvider mockConfirmedBlockHeadersProvider = mock(ConfirmedBlockHeadersProvider.class);
        when(mockConfirmedBlockHeadersProvider.getConfirmedBlockHeaders(any())).thenReturn(new ArrayList<>());

        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService hsmBookkeepingService = new HSMBookkeepingService(
                mockBlockStore,
                mockHsmBookkeepingClient,
                mockConfirmedBlockHeadersProvider,
                mock(NodeBlockProcessor.class),
                2_000,
                mock(PowHSMBookkeepingConfig.class)
        );

        hsmBookkeepingService.addListener(mockListener);

        Thread.sleep(150);

        hsmBookkeepingService.informConfirmedBlockHeaders();

        // Assert
        verify(mockConfirmedBlockHeadersProvider,times(1)).getConfirmedBlockHeaders(any());
        verify(mockHsmBookkeepingClient, never()).advanceBlockchain(any(AdvanceBlockchainMessage.class));
        verify(mockBlockStore, times(1)).getBlockByHash(any());
        verifyZeroInteractions(mockListener);
    }


}
