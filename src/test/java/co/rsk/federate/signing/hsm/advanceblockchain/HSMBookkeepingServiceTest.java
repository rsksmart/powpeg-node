package co.rsk.federate.signing.hsm.advanceblockchain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMDeviceException;
import co.rsk.federate.signing.hsm.HSMInvalidResponseException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.message.AdvanceBlockchainMessage;
import co.rsk.federate.signing.hsm.message.PowHSMState;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.net.NodeBlockProcessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HSMBookkeepingServiceTest {

    @Test
    void start_ok() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        HSMBookkeepingService service = new HSMBookkeepingService(
            mock(BlockStore.class),
            mockHsmBookkeepingClient,
            mock(ConfirmedBlockHeadersProvider.class),
            mock(NodeBlockProcessor.class),
            2_000,
            false
        );

        service.start();
        Thread.sleep(10);

        assertTrue(service.isStarted());
    }

    @Test
    void start_already_started_service() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        HSMBookkeepingService service = new HSMBookkeepingService(
            mock(BlockStore.class),
            mockHsmBookkeepingClient,
            mock(ConfirmedBlockHeadersProvider.class),
            mock(NodeBlockProcessor.class),
            2_000,
            false
        );

        service.start();
        Thread.sleep(10);
        service.start();
        Thread.sleep(10);

        assertTrue(service.isStarted());
        verify(mockHsmBookkeepingClient, times(1)).getHSMPointer();
    }

    @Test
    void start_with_stopBookkepingConf_True() throws InterruptedException {
        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService service = new HSMBookkeepingService(
            mock(BlockStore.class),
            mock(HSMBookkeepingClient.class),
            mock(ConfirmedBlockHeadersProvider.class),
            mock(NodeBlockProcessor.class),
            2_000,
            true
        );

        service.addListener(mockListener);
        service.start();
        Thread.sleep(10);

        assertFalse(service.isStarted());
        verifyNoInteractions(mockListener);
    }

    @Test
    void start_with_stopBookkepingConf_False() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService service = new HSMBookkeepingService(
            mock(BlockStore.class),
            mockHsmBookkeepingClient,
            mock(ConfirmedBlockHeadersProvider.class),
            mock(NodeBlockProcessor.class),
            2_000,
            false
        );

        service.addListener(mockListener);
        service.start();
        Thread.sleep(10);

        assertTrue(service.isStarted());
        verifyNoInteractions(mockListener);
    }

    @Test
    void start_neededReset_Ok() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), true);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService service = new HSMBookkeepingService(
            mock(BlockStore.class),
            mockHsmBookkeepingClient,
            mock(ConfirmedBlockHeadersProvider.class),
            mock(NodeBlockProcessor.class),
            2_000,
            false
        );

        service.addListener(mockListener);
        service.start();
        Thread.sleep(10);

        assertTrue(service.isStarted());
        verifyNoInteractions(mockListener);
        verify(mockHsmBookkeepingClient, times(1)).resetAdvanceBlockchain();
    }

    @Test
    void start_neededReset_FailGettingState() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenThrow(new HSMInvalidResponseException(""));

        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService service = new HSMBookkeepingService(
            mock(BlockStore.class),
            mockHsmBookkeepingClient,
            mock(ConfirmedBlockHeadersProvider.class),
            mock(NodeBlockProcessor.class),
            2_000,
            false
        );

        service.addListener(mockListener);
        service.start();
        Thread.sleep(10);

        assertFalse(service.isStarted());
        verify(mockListener, times(1)).onIrrecoverableError(any());
    }

    @Test
    void start_neededReset_FailReset() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), true);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);
        doThrow(new HSMInvalidResponseException("")).when(mockHsmBookkeepingClient).resetAdvanceBlockchain();

        HSMBookeepingServiceListener mockListener = mock(HSMBookeepingServiceListener.class);

        HSMBookkeepingService service = new HSMBookkeepingService(
            mock(BlockStore.class),
            mockHsmBookkeepingClient,
            mock(ConfirmedBlockHeadersProvider.class),
            mock(NodeBlockProcessor.class),
            2_000,
            false
        );

        service.addListener(mockListener);
        service.start();
        Thread.sleep(10);

        assertFalse(service.isStarted());
        verify(mockHsmBookkeepingClient, times(1)).resetAdvanceBlockchain();
        verify(mockListener, times(1)).onIrrecoverableError(any());
    }

    @Test
    void start_fails_on_scheduler_startup() throws HSMClientException, InterruptedException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        // Configuring the Scheduler with 0ms interval throws an IllegalArgumentException
        HSMBookkeepingService service = new HSMBookkeepingService(
            mock(BlockStore.class),
            mockHsmBookkeepingClient,
            mock(ConfirmedBlockHeadersProvider.class),
            mock(NodeBlockProcessor.class),
            0,
            false
        );

        HSMBookeepingServiceListener listener = mock(HSMBookeepingServiceListener.class);
        service.addListener(listener);

        service.start();
        Thread.sleep(10);

        // Scheduler error is captured and the listener's onIrrecoverableError method is invoked
        verify(listener, times(1)).onIrrecoverableError(any());
        assertFalse(service.isStarted());
    }

    // Stop
    @Test
    void stop_Ok() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(mockHsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        HSMBookkeepingService service = new HSMBookkeepingService(
            mock(BlockStore.class),
            mockHsmBookkeepingClient,
            mock(ConfirmedBlockHeadersProvider.class),
            mock(NodeBlockProcessor.class),
            2_000,
            false
        );

        service.start();
        Thread.sleep(10);
        assertTrue(service.isStarted());

        service.stop();
        Thread.sleep(10);

        assertFalse(service.isStarted());
    }

    @Test
    void stop_already_stopped_service() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient hsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
        when(hsmBookkeepingClient.getHSMPointer()).thenReturn(state);

        HSMBookkeepingService service = new HSMBookkeepingService(
            mock(BlockStore.class),
            hsmBookkeepingClient,
            mock(ConfirmedBlockHeadersProvider.class),
            mock(NodeBlockProcessor.class),
            2_000,
            false
        );

        service.stop();
        Thread.sleep(10);
        assertFalse(service.isStarted());

        service.start();
        Thread.sleep(10);
        assertTrue(service.isStarted());

        service.stop();
        Thread.sleep(10);

        assertFalse(service.isStarted());
    }

    // Add and remove Listener
    @Test
    void addListener_Listener_Working_Ok() throws HSMClientException, InterruptedException {
        HSMBookkeepingClient hsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        when(hsmBookkeepingClient.getHSMPointer()).thenThrow(new HSMDeviceException("", 1));

        HSMBookkeepingService service = new HSMBookkeepingService(
            mock(BlockStore.class),
            hsmBookkeepingClient,
            mock(ConfirmedBlockHeadersProvider.class),
            mock(NodeBlockProcessor.class),
            2_000,
            false
        );

        HSMBookeepingServiceListener listener = mock(HSMBookeepingServiceListener.class);
        service.addListener(listener);

        service.start();
        Thread.sleep(10);

        verify(listener, times(1)).onIrrecoverableError(any());
    }

    @Test
    void removeListener_Ok() throws HSMClientException, InterruptedException {
        HSMBookkeepingClient hsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        when(hsmBookkeepingClient.getHSMPointer()).thenThrow(new HSMDeviceException("", 1));

        HSMBookkeepingService service = new HSMBookkeepingService(
            mock(BlockStore.class),
            hsmBookkeepingClient,
            mock(ConfirmedBlockHeadersProvider.class),
            mock(NodeBlockProcessor.class),
            2_000,
            false
        );

        HSMBookeepingServiceListener listener = mock(HSMBookeepingServiceListener.class);
        service.addListener(listener);
        service.removeListener(listener);

        service.start();
        Thread.sleep(10);

        // Assert
        verify(listener, never()).onIrrecoverableError(any());
        Mockito.verifyNoInteractions(listener);
    }

    // informConfirmedBlockHeaders
    @Test
    void informConfirmedBlockHeaders_onIrrecoverableError() throws HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
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
            false
        );

        service.addListener(mockListener);
        service.informConfirmedBlockHeaders();

        // Assert
        verify(mockHsmBookkeepingClient, times(1)).getHSMPointer();
        verify(mockListener, times(1)).onIrrecoverableError(any());
    }

    @Test
    void informConfirmedBlockHeaders_already_informing() throws HSMClientException, InterruptedException {
        BlockHeader mockBlockHeaderToInform = TestUtils.createBlockHeaderMock(1);
        when(mockBlockHeaderToInform.getEncoded(true, true, true)).thenReturn(Keccak256.ZERO_HASH.getBytes());
        List<BlockHeader> blockHeadersToInform = Collections.singletonList(mockBlockHeaderToInform);

        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
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
            false
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
    void informConfirmedBlockHeaders_Ok() throws InterruptedException, HSMClientException {
        BlockHeader mockBlockHeaderToInform = TestUtils.createBlockHeaderMock(1);
        when(mockBlockHeaderToInform.getEncoded(true, true, true)).thenReturn(Keccak256.ZERO_HASH.getBytes());
        List<BlockHeader> blockHeadersToInform = Collections.singletonList(mockBlockHeaderToInform);

        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
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
            false
        );

        hsmBookkeepingService.addListener(mockListener);
        hsmBookkeepingService.start();

        Thread.sleep(150);

        hsmBookkeepingService.informConfirmedBlockHeaders();

        // Assert
        assertTrue(hsmBookkeepingService.isStarted());
        verify(mockHsmBookkeepingClient, times(1)).advanceBlockchain(any(AdvanceBlockchainMessage.class));
        verify(mockBlockStore, times(2)).getBlockByHash(any());
        Mockito.verifyNoInteractions(mockListener);

        Thread.sleep(150); // delay for next call
        // getHSMPointer() is called twice for the first call and once for the second call
        verify(mockHsmBookkeepingClient, times(3)).getHSMPointer();
    }

    @Test
    void informConfirmedBlockHeaders_hasBetterBlockToSync_true() throws InterruptedException {
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
            false
        );

        hsmBookkeepingService.addListener(mockListener);

        Thread.sleep(150);

        hsmBookkeepingService.informConfirmedBlockHeaders();

        // Assert
        verify(mockNodeBlockProcessor, times(1)).hasBetterBlockToSync();
        verifyNoInteractions(mockHsmBookkeepingClient);
        verifyNoInteractions(mockListener);
    }

    @Test
    void informConfirmedBlockHeaders_getHsmBestBlock_null() throws InterruptedException, HSMClientException {
        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
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
            false
        );

        hsmBookkeepingService.addListener(mockListener);

        Thread.sleep(150);

        hsmBookkeepingService.informConfirmedBlockHeaders();

        // Assert
        verify(mockHsmBookkeepingClient, times(1)).getHSMPointer();
        verifyNoMoreInteractions(mockHsmBookkeepingClient);
        verify(mockBlockStore, times(1)).getBlockByHash(any());
        verifyNoInteractions(mockConfirmedBlockHeadersProvider);
        verifyNoInteractions(mockListener);
    }

    @Test
    void informConfirmedBlockHeaders_emptyBlockHeaders() throws InterruptedException, HSMClientException {
        BlockHeader mockBlockHeaderToInform = TestUtils.createBlockHeaderMock(1);
        when(mockBlockHeaderToInform.getFullEncoded()).thenReturn(Keccak256.ZERO_HASH.getBytes());

        HSMBookkeepingClient mockHsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        PowHSMState state = new PowHSMState(Keccak256.ZERO_HASH.toHexString(), Keccak256.ZERO_HASH.toHexString(), false);
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
            false
        );

        hsmBookkeepingService.addListener(mockListener);

        Thread.sleep(150);

        hsmBookkeepingService.informConfirmedBlockHeaders();

        // Assert
        verify(mockConfirmedBlockHeadersProvider, times(1)).getConfirmedBlockHeaders(any());
        verify(mockHsmBookkeepingClient, never()).advanceBlockchain(any(AdvanceBlockchainMessage.class));
        verify(mockBlockStore, times(1)).getBlockByHash(any());
        verifyNoInteractions(mockListener);
    }
}
