package neuvillette.libertas.wireless;

import java.math.BigInteger;
import java.util.function.Predicate;

/**
 * Presents a GT energy buffer backed directly by a wireless account. Reading never reserves or withdraws EU;
 * decreasing the exposed value charges only the consumed difference. Deposits are deliberately ignored.
 */
public final class WirelessEnergyBudget {

    private long tick = Long.MIN_VALUE;
    private long spent;

    public long available(long worldTick, long limit, BigInteger balance) {
        if (tick != worldTick) {
            tick = worldTick;
            spent = 0;
        }
        long remaining = limit > spent ? limit - spent : 0;
        return balance.signum() <= 0 ? 0
            : balance.min(BigInteger.valueOf(remaining))
                .longValue();
    }

    public boolean update(long worldTick, long limit, BigInteger balance, long newValue,
        Predicate<BigInteger> withdraw) {
        long available = available(worldTick, limit, balance);
        long requested = Math.max(0, newValue);
        if (requested >= available) return true;
        long cost = available - requested;
        if (!withdraw.test(
            BigInteger.valueOf(cost)
                .negate()))
            return false;
        spent += cost;
        return true;
    }
}
