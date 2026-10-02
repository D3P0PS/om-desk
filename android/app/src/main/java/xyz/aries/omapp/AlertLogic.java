package xyz.aries.omapp;

/**
 * Pure alert evaluation, no Android types, so it runs under plain JUnit.
 *
 * Kinds (one-shot, like TradingView's "once only"):
 *   above  price >= level
 *   below  price <= level
 *   cross  price moved from one side of level to the other (or onto it) since the previous check;
 *          the first observation only records the price
 *   pct    |price - base| / base >= level percent; base is set by the first observation if missing
 */
public final class AlertLogic {
    private AlertLogic() {}

    public static final class Alert {
        public String id;
        public String venue;
        public String symbol;
        public String kind;
        public double level;
        public Double basePrice; // null until known
        public Double lastPrice; // null until the first check
        public boolean triggered;
        public Long triggeredAt; // ms
        public String note = "";
    }

    /** Applies one price observation. Returns true when the alert fires on this observation. */
    public static boolean observe(Alert a, double price, long nowMs) {
        if (a.triggered) return false;
        if (Double.isNaN(price) || Double.isInfinite(price) || price <= 0) {
            throw new IllegalArgumentException("bad price " + price + " for " + a.symbol);
        }
        Double prev = a.lastPrice;
        a.lastPrice = price;
        boolean fire;
        switch (a.kind) {
            case "above":
                fire = price >= a.level;
                break;
            case "below":
                fire = price <= a.level;
                break;
            case "cross":
                fire = prev != null && ((prev < a.level && price >= a.level) || (prev > a.level && price <= a.level));
                break;
            case "pct":
                if (a.basePrice == null) {
                    a.basePrice = price;
                    return false;
                }
                fire = Math.abs(price - a.basePrice) / a.basePrice * 100.0 >= a.level;
                break;
            default:
                throw new IllegalArgumentException("unknown alert kind '" + a.kind + "'");
        }
        if (fire) {
            a.triggered = true;
            a.triggeredAt = nowMs;
        }
        return fire;
    }

    /** Signed percent move from base, e.g. "+5.20%"; the notification text wraps it. */
    static String pctMove(Alert a, double price) {
        double chg = a.basePrice == null ? 0 : (price - a.basePrice) / a.basePrice * 100.0;
        return String.format(java.util.Locale.US, "%+.2f%%", chg);
    }

    static String fmt(double v) {
        double x = Math.abs(v);
        int d = x >= 10000 ? 0 : x >= 100 ? 2 : x >= 1 ? 3 : x >= 0.01 ? 4 : 8;
        return String.format(java.util.Locale.US, "%,." + d + "f", v);
    }
}
