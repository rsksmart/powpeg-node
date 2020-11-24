package co.rsk.federate.timing;

import org.junit.Assert;
import org.junit.Test;

public class TurnSchedulerTest {
    @Test
    public void getInterval() {
        int[][] cases = new int[][]{
            // period, # of participants, expected interval
            new int[]{1000, 3, 3000},
            new int[]{100, 1, 100},
            new int[]{2, 100, 200},
        };
        for (int i = 0; i < cases.length; i++) {
            TurnScheduler scheduler = new TurnScheduler(cases[i][0], cases[i][1]);
            Assert.assertEquals(cases[i][2], scheduler.getInterval());
        }
    }

    @Test
    public void getDelay() {
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
        for (int i = 0; i < cases.length; i++) {
            TurnScheduler scheduler = new TurnScheduler(cases[i][1], cases[i][2]);
            Assert.assertEquals(cases[i][4], scheduler.getDelay(cases[i][0], cases[i][3]));
        }
    }

    @Test
    public void getDelay_exception() {
        boolean thrown = false;
        int[] cases = new int[]{ -1, 2, 3 };
        for (int i = 0; i < cases.length ; i++) {
            try {
                TurnScheduler scheduler = new TurnScheduler(5, 2);
                scheduler.getDelay(100, cases[i]);
            } catch (IllegalArgumentException e) {
                thrown = true;
            }
            Assert.assertTrue(thrown);
        }
    }
}
