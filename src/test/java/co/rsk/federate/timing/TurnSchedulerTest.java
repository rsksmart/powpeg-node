package co.rsk.federate.timing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurnSchedulerTest {
    
    @Test
    void getInterval() {
        int[][] cases = new int[][]{
            // period, # of participants, expected interval
            new int[]{1000, 3, 3000},
            new int[]{100, 1, 100},
            new int[]{2, 100, 200},
        };
        for (int[] aCase : cases) {
            TurnScheduler scheduler = new TurnScheduler(aCase[0], aCase[1]);
            assertEquals(aCase[2], scheduler.getInterval());
        }
    }

    @Test
    void getDelay() {
        int[][] cases = new int[][]{
            // current time, period, # of participants, participant #, expected delay
            new int[]{0, 10, 6, 0, 0},
            new int[]{2, 10, 6, 0, 58},
            new int[]{5, 10, 6, 0, 55},
            new int[]{22, 10, 6, 0, 38},
            new int[]{82, 10, 6, 0, 38},
            new int[]{142, 10, 6, 0, 38},
            new int[]{142, 10, 6, 1, 48},
            new int[]{142, 10, 6, 2, 58},
            new int[]{142, 10, 6, 3, 8},
            new int[]{142, 10, 6, 4, 18},
            new int[]{142, 10, 6, 5, 28}
        };
        for (int[] aCase : cases) {
            TurnScheduler scheduler = new TurnScheduler(aCase[1], aCase[2]);
            assertEquals(aCase[4], scheduler.getDelay(aCase[0], aCase[3]));
        }
    }

    @Test
    void getDelay_exception() {
        boolean thrown = false;
        int[] cases = new int[]{ -1, 2, 3 };
        for (int aCase : cases) {
            try {
                TurnScheduler scheduler = new TurnScheduler(5, 2);
                scheduler.getDelay(100, aCase);
            } catch (IllegalArgumentException e) {
                thrown = true;
            }
            assertTrue(thrown);
        }
    }
}
