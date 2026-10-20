package neuvillette.libertas.wireless;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.junit.Test;

public class WirelessEnergyBudgetTest {

    private final WirelessEnergyBudget budget = new WirelessEnergyBudget();
    private final AtomicReference<BigInteger> balance = new AtomicReference<>(BigInteger.valueOf(1000));
    private final Predicate<BigInteger> withdraw = delta -> {
        BigInteger next = balance.get()
            .add(delta);
        if (next.signum() < 0) return false;
        balance.set(next);
        return true;
    };

    @Test
    public void repeatedReadsDoNotWithdrawOrReserveEnergy() {
        for (int i = 0; i < 10; i++) assertEquals(128, budget.available(1, 128, balance.get()));
        assertEquals(BigInteger.valueOf(1000), balance.get());
    }

    @Test
    public void consumingTheVirtualBufferChargesOnlyTheDifference() {
        assertTrue(budget.update(1, 128, balance.get(), 96, withdraw));
        assertEquals(BigInteger.valueOf(968), balance.get());
        assertEquals(96, budget.available(1, 128, balance.get()));
        assertTrue(budget.update(1, 128, balance.get(), 0, withdraw));
        assertEquals(BigInteger.valueOf(872), balance.get());
        assertEquals(0, budget.available(1, 128, balance.get()));
    }

    @Test
    public void theNextWorldTickRestoresTheThroughputBudgetWithoutCharging() {
        budget.update(1, 128, balance.get(), 0, withdraw);
        assertEquals(128, budget.available(2, 128, balance.get()));
        assertEquals(BigInteger.valueOf(872), balance.get());
    }

    @Test
    public void profileChangesDoNotResetSpentEnergyWithinTheSameTick() {
        budget.update(1, 128, balance.get(), 64, withdraw);
        assertEquals(192, budget.available(1, 256, balance.get()));
        assertEquals(0, budget.available(1, 32, balance.get()));
        assertEquals(64, budget.available(1, 128, balance.get()));
    }

    @Test
    public void disablingAControllerStopsImmediatelyWithoutCharging() {
        assertEquals(0, budget.available(1, 0, balance.get()));
        assertTrue(budget.update(1, 0, balance.get(), 0, withdraw));
        assertEquals(BigInteger.valueOf(1000), balance.get());
    }

    @Test
    public void depositsCannotCreateEnergyOrIncreaseTheThroughputBudget() {
        budget.update(1, 128, balance.get(), 99999, withdraw);
        assertEquals(BigInteger.valueOf(1000), balance.get());
        assertEquals(128, budget.available(1, 128, balance.get()));
    }

    @Test
    public void depletedAccountsExposeOnlyTheirRemainingBalance() {
        balance.set(BigInteger.valueOf(17));
        assertEquals(17, budget.available(1, 128, balance.get()));
        budget.update(1, 128, balance.get(), 0, withdraw);
        assertEquals(BigInteger.ZERO, balance.get());
        assertEquals(0, budget.available(1, 128, balance.get()));
    }

    @Test
    public void failedWithdrawalsLeaveTheThroughputBudgetUntouched() {
        assertFalse(budget.update(1, 128, balance.get(), 0, delta -> false));
        assertEquals(128, budget.available(1, 128, balance.get()));
    }

    @Test
    public void multipleHatchesShareTheAccountWithoutSharingTheirThroughputBudget() {
        WirelessEnergyBudget other = new WirelessEnergyBudget();
        balance.set(BigInteger.valueOf(200));
        budget.update(1, 128, balance.get(), 0, withdraw);
        assertEquals(72, other.available(1, 128, balance.get()));
        other.update(1, 128, balance.get(), 0, withdraw);
        assertEquals(BigInteger.ZERO, balance.get());
        assertEquals(0, budget.available(1, 128, balance.get()));
    }

    @Test
    public void arbitrarilyLargeBalancesDoNotOverflowLong() {
        balance.set(BigInteger.TEN.pow(100));
        assertEquals(Long.MAX_VALUE, budget.available(1, Long.MAX_VALUE, balance.get()));
        budget.update(1, Long.MAX_VALUE, balance.get(), 0, withdraw);
        assertEquals(
            BigInteger.TEN.pow(100)
                .subtract(BigInteger.valueOf(Long.MAX_VALUE)),
            balance.get());
        assertEquals(0, budget.available(1, Long.MAX_VALUE, balance.get()));
    }

    @Test
    public void highPowerProfilesSaturateInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, new WirelessPowerProfile(1L << 40, 1L << 30).maxEuPerTick());
        assertEquals(8192, new WirelessPowerProfile(128, 64).maxEuPerTick());
        assertEquals(0, new WirelessPowerProfile(128, 0).maxEuPerTick());
    }
}
