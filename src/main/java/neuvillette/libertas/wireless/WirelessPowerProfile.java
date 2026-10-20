package neuvillette.libertas.wireless;

/** A controller's per-hatch specification. The 64 template hatches do not multiply its amperage. */
public final class WirelessPowerProfile {

    public static final WirelessPowerProfile DISABLED = new WirelessPowerProfile(0, 0);

    public final long voltage;
    public final long amperage;

    public WirelessPowerProfile(long voltage, long amperage) {
        this.voltage = Math.max(0, voltage);
        this.amperage = Math.max(0, amperage);
    }

    public boolean isEnabled() {
        return voltage > 0 && amperage > 0;
    }

    public long maxEuPerTick() {
        if (!isEnabled()) return 0;
        return voltage > Long.MAX_VALUE / amperage ? Long.MAX_VALUE : voltage * amperage;
    }
}
