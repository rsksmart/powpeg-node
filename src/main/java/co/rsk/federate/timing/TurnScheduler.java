package co.rsk.federate.timing;

public class TurnScheduler {
    private int period;
    private int participants;

    public TurnScheduler(int period, int participants) {
        this.period = period;
        this.participants = participants;
    }

    public long getInterval() {
        return participants * period;
    }

    public long getDelay(long now, int position) {
        if (position < 0 || position >= participants) {
            throw new IllegalArgumentException(String.format("Position must be between %d and %d", 0, participants-1));
        }

        int totalPeriod = participants * period;
        int fedPos = position * period;

        long s = now % totalPeriod;
        long r = (fedPos - s) % totalPeriod;

        if (r < 0) {
            r += totalPeriod;
        }

        return r;
    }
}
