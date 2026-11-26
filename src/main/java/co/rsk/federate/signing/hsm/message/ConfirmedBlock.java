package co.rsk.federate.signing.hsm.message;

import java.util.List;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

public record ConfirmedBlock(Block block, List<BlockHeader> brothers) {}
