package com.pos.services;

import com.pos.views.SalesView.CartItem;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SaleAllocationTest {
    @Test void largeQuantityRetainsEveryDiscountedCent() {
        var cart=FXCollections.observableArrayList(new CartItem(1,"Audit item",new BigDecimal("1.00"),1000));
        var lines=TransactionService.allocateLines(cart,new BigDecimal("1.00"));
        assertEquals(new BigDecimal("999.00"),total(lines));
        assertEquals(1000,lines.stream().mapToInt(TransactionService.PricedLine::quantity).sum());
        assertEquals(2,lines.size());
    }
    @Test void awkwardQuantitiesAndRoundingReconcileExactly() {
        Random random=new Random(19);
        for(int run=0;run<250;run++) {
            var cart=FXCollections.<CartItem>observableArrayList();BigDecimal subtotal=BigDecimal.ZERO;
            for(int id=1;id<=12;id++) {
                CartItem item=new CartItem(id,"Item",BigDecimal.valueOf(random.nextInt(10000),2),1+random.nextInt(1000));
                cart.add(item);subtotal=subtotal.add(item.getSubtotal());
            }
            BigDecimal discount=subtotal.multiply(new BigDecimal("0.137")).setScale(2,java.math.RoundingMode.HALF_UP);
            var lines=TransactionService.allocateLines(cart,discount);
            assertEquals(0,subtotal.subtract(discount).compareTo(total(lines)));
            for(CartItem item:cart) assertEquals(item.getQuantity(),lines.stream().filter(l->l.productId()==item.getProductId()).mapToInt(TransactionService.PricedLine::quantity).sum());
            assertTrue(lines.stream().allMatch(l->l.price().signum()>=0 && l.price().scale()<=2));
        }
    }
    @Test void invalidQuantitiesPricesAndDiscountsAreRejected() {
        var cart=FXCollections.observableArrayList(new CartItem(1,"Item",new BigDecimal("1.00"),3));
        assertThrows(IllegalArgumentException.class,()->TransactionService.allocateLines(cart,new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class,()->TransactionService.allocateLines(cart,new BigDecimal("3.01")));
        cart.get(0).setQuantity(0);
        assertThrows(IllegalArgumentException.class,()->TransactionService.allocateLines(cart,BigDecimal.ZERO));
    }
    @Test void freeItemsAndFullDiscountStayZeroWithoutDivisionErrors() {
        var cart=FXCollections.observableArrayList(new CartItem(1,"Free",BigDecimal.ZERO,7));
        assertEquals(0,total(TransactionService.allocateLines(cart,null)).signum());
        cart.add(new CartItem(2,"Paid",new BigDecimal("1.00"),3));
        assertEquals(0,total(TransactionService.allocateLines(cart,new BigDecimal("3.00"))).signum());
    }
    private BigDecimal total(List<TransactionService.PricedLine> lines) {
        return lines.stream().map(l->l.price().multiply(BigDecimal.valueOf(l.quantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
}
