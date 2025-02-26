package co.rsk.federate.signing.hsm;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.ethereum.core.Block;

public enum HSMVersion {
    V1(1),
    V2(2),
    V4(4) {
        @Override
        public BigInteger getBlockDifficultyToConsider(Block block, BigInteger difficultyCap) {
            return getBlockDifficultyToConsiderSinceV4(block, difficultyCap);
        }
    },
    V5(5) {
        @Override
        public BigInteger getBlockDifficultyToConsider(Block block, BigInteger difficultyCap) {
            return getBlockDifficultyToConsiderSinceV4(block, difficultyCap);
        }
    },
    ;

    private final int number;

    HSMVersion(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    public BigInteger getBlockDifficultyToConsider(Block block, BigInteger difficultyCap) {
        return block.getDifficulty().asBigInteger();
    }

    private static BigInteger getBlockDifficultyToConsiderSinceV4(Block block, BigInteger difficultyCap) {
        BigInteger blockDifficulty = block.getDifficulty().asBigInteger();

        BigInteger unclesDifficulty = block.getUncleList().stream()
            .map(uncle -> uncle.getDifficulty().asBigInteger())
            .reduce(BigInteger.ZERO, BigInteger::add);
        blockDifficulty = blockDifficulty.add(unclesDifficulty);
        return difficultyCap.min(blockDifficulty);
    }

    public static List<HSMVersion> getPowHSMVersions() {
        return Arrays.asList(V2, V4, V5);
    }

    public static boolean isPowHSM(int version) {
        return getPowHSMVersions().stream().anyMatch(hsmVersion -> hsmVersion.number == version);
    }

    public static boolean isValidHSMVersion(int version) {
        return Arrays.stream(values()).anyMatch(hsmVersion -> hsmVersion.number == version);
    }
}
