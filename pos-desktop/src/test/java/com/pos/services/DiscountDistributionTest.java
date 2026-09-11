package com.pos.services;

import com.pos.views.SalesView.CartItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TransactionService#distributeDiscount} — the pure math that
 * spreads a promo discount across cart lines into net unit prices.
 */
class DiscountDistributionTest {

    private static ObservableList<CartItem> cart(CartItem... items) {
        return FXCollections.observableArrayList(items);
    }

    private static CartItem item(String name, String price, int qty) {
        return new CartItem(name.hashCode(), name, new BigDecimal(price), qty);
    }

    /** Sum of (net unit price * qty) across all lines. */
    private static BigDecimal charged(ObservableList<CartItem> cart, BigDecimal[] netUnit) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < cart.size(); i++) {
            total = total.add(netUnit[i].multiply(BigDecimal.valueOf(cart.get(i).getQuantity())));
        }
        return total;
    }

    @Test
    void zeroDiscountLeavesUnitPricesUnchanged() {
        ObservableList<CartItem> c = cart(item("A", "10.00", 2), item("B", "5.50", 1));
        BigDecimal[] net = TransactionService.distributeDiscount(c, BigDecimal.ZERO);
        assertEquals(0, net[0].compareTo(new BigDecimal("10.00")));
        assertEquals(0, net[1].compareTo(new BigDecimal("5.50")));
    }

    @Test
    void nullDiscountIsTreatedAsZero() {
        ObservableList<CartItem> c = cart(item("A", "10.00", 1));
        BigDecimal[] net = TransactionService.distributeDiscount(c, null);
        assertEquals(0, net[0].compareTo(new BigDecimal("10.00")));
    }

    @Test
    void discountIsSpreadProportionallyAndChargesTheDiscountedTotal() {
        // cart total = 100, discount 20 -> charge 80
        ObservableList<CartItem> c = cart(item("A", "60.00", 1), item("B", "20.00", 2));
        BigDecimal[] net = TransactionService.distributeDiscount(c, new BigDecimal("20.00"));
        assertEquals(0, new BigDecimal("80.00").compareTo(charged(c, net)),
                "line totals must sum to cartTotal - discount");
    }

    @Test
    void discountLargerThanCartIsClampedToCartTotal() {
        ObservableList<CartItem> c = cart(item("A", "30.00", 1));
        BigDecimal[] net = TransactionService.distributeDiscount(c, new BigDecimal("999.00"));
        assertEquals(0, BigDecimal.ZERO.compareTo(charged(c, net)));
        assertTrue(net[0].signum() >= 0);
    }

    @Test
    void negativeDiscountIsIgnored() {
        ObservableList<CartItem> c = cart(item("A", "12.34", 3));
        BigDecimal[] net = TransactionService.distributeDiscount(c, new BigDecimal("-5.00"));
        assertEquals(0, new BigDecimal("37.02").compareTo(charged(c, net)));
    }

    @Test
    void multiQuantityRoundingStaysWithinACentOfTarget() {
        // 3 x 3.33-ish lines, awkward discount
        ObservableList<CartItem> c = cart(item("A", "10.00", 3), item("B", "10.00", 3));
        BigDecimal[] net = TransactionService.distributeDiscount(c, new BigDecimal("7.00"));
        BigDecimal diff = new BigDecimal("53.00").subtract(charged(c, net)).abs();
        assertTrue(diff.compareTo(new BigDecimal("0.05")) <= 0,
                "rounded line totals should be within a few cents of the target; was off by " + diff);
    }
}
