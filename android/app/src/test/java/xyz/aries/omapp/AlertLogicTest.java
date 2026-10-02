package xyz.aries.omapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AlertLogicTest {
    private static AlertLogic.Alert alert(String kind, double level) {
        AlertLogic.Alert a = new AlertLogic.Alert();
        a.id = "t";
        a.venue = "BINANCE";
        a.symbol = "BTCUSDT";
        a.kind = kind;
        a.level = level;
        return a;
    }

    @Test
    public void aboveFiresOnceAtOrOverLevel() {
        AlertLogic.Alert a = alert("above", 100);
        assertFalse(AlertLogic.observe(a, 99.9, 1));
        assertTrue(AlertLogic.observe(a, 100, 2));
        assertEquals(Long.valueOf(2), a.triggeredAt);
        assertFalse("one-shot", AlertLogic.observe(a, 150, 3));
    }

    @Test
    public void belowFiresAtOrUnderLevel() {
        AlertLogic.Alert a = alert("below", 100);
        assertFalse(AlertLogic.observe(a, 100.1, 1));
        assertTrue(AlertLogic.observe(a, 99, 2));
    }

    @Test
    public void crossNeedsAPreviousSideAndFiresBothWays() {
        AlertLogic.Alert up = alert("cross", 100);
        assertFalse("first observation only records", AlertLogic.observe(up, 101, 1));
        assertFalse(AlertLogic.observe(up, 102, 2));
        assertTrue(AlertLogic.observe(up, 99, 3));

        AlertLogic.Alert dn = alert("cross", 100);
        assertFalse(AlertLogic.observe(dn, 95, 1));
        assertTrue("touching the level counts", AlertLogic.observe(dn, 100, 2));
    }

    @Test
    public void pctSetsMissingBaseThenFiresOnEitherDirection() {
        AlertLogic.Alert a = alert("pct", 5);
        assertFalse(AlertLogic.observe(a, 200, 1));
        assertEquals(200.0, a.basePrice, 0);
        assertFalse(AlertLogic.observe(a, 209.9, 2));
        assertTrue(AlertLogic.observe(a, 189, 3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void badPriceIsRaisedNotIgnored() {
        AlertLogic.observe(alert("above", 1), Double.NaN, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownKindIsRaised() {
        AlertLogic.observe(alert("sideways", 1), 1, 1);
    }

    @Test
    public void triggeredAtStaysNullUntilFired() {
        AlertLogic.Alert a = alert("above", 10);
        AlertLogic.observe(a, 5, 1);
        assertNull(a.triggeredAt);
    }

    @Test
    public void formatByMagnitude() {
        assertEquals("83,952", AlertLogic.fmt(83952.4));
        assertEquals("2,680.03", AlertLogic.fmt(2680.03));
        assertEquals("0.4312", AlertLogic.fmt(0.43121));
    }

    @Test
    public void pctMoveIsSignedFromBase() {
        AlertLogic.Alert a = alert("pct", 5);
        a.basePrice = 200.0;
        assertEquals("-5.50%", AlertLogic.pctMove(a, 189));
    }
}
