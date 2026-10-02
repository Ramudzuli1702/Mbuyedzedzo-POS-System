package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.domain.*;
import com.mbuyedzedzo.licensing.license.LicenseAdminService;
import com.mbuyedzedzo.licensing.repo.CustomerRepo;
import com.mbuyedzedzo.licensing.repo.OrderRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Can't click through PayFast's actual hosted sandbox page headlessly, so this
 * verifies the one thing that matters most — what happens once an ITN *has*
 * already been verified — in isolation from the network calls that get it there.
 * The verification layers themselves (signature + validate-postback) were
 * exercised live against a running server instead; see the session notes.
 */
@ExtendWith(MockitoExtension.class)
class OrderFulfillmentServiceTest {

    @Mock OrderRepo orders;
    @Mock CustomerRepo customers;
    @Mock LicenseAdminService licenseAdminService;
    @Mock EmailService email;
    @Mock PricingService pricing;
    @Mock Environment env;

    OrderFulfillmentService fulfillment;

    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        when(env.getProperty(eq("payfast.return-url"), anyString()))
                .thenReturn("http://localhost:8080/buy/success");
        lenient().when(pricing.displayName(any())).thenReturn("Mbuyedzedzo Retail POS");
        fulfillment = new OrderFulfillmentService(orders, customers, licenseAdminService, email, pricing, env);

        pendingOrder = new Order();
        pendingOrder.setId(1L);
        pendingOrder.setReference("ORD-TEST1234");
        pendingOrder.setBuyerEmail("buyer@example.com");
        pendingOrder.setBuyerName("Jane Buyer");
        pendingOrder.setProduct(Product.POS_RETAIL);
        pendingOrder.setLicenseType(LicenseType.PERPETUAL);
        pendingOrder.setAmount(new BigDecimal("2999.00"));
        pendingOrder.setStatus(OrderStatus.PENDING);
    }

    @Test
    void firstTimeBuyerGetsAnAccountALicenseAndASetPasswordLink() {
        when(customers.findByEmail("buyer@example.com")).thenReturn(Optional.empty());
        Customer saved = new Customer();
        saved.setId(42L);
        when(customers.save(any(Customer.class))).thenReturn(saved);

        License issued = new License();
        issued.setId(7L);
        issued.setLicenseKey("RET-2026-ABCD-EFGH-1234");
        when(licenseAdminService.issue(eq(Product.POS_RETAIL), eq(LicenseType.PERPETUAL), eq(1),
                isNull(), eq(42L), isNull(), anyString(), eq("storefront")))
                .thenReturn(issued);

        fulfillment.fulfil(pendingOrder, "PF-PAYMENT-1");

        assertEquals(OrderStatus.PAID, pendingOrder.getStatus());
        assertEquals("PF-PAYMENT-1", pendingOrder.getGatewayPaymentId());
        assertEquals(42L, pendingOrder.getCustomerId());
        assertEquals(7L, pendingOrder.getLicenseId());
        assertNotNull(pendingOrder.getPaidAt());

        // A set-password token was generated and persisted on the new customer.
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customers, times(2)).save(customerCaptor.capture()); // once to create, once to add the token
        Customer withToken = customerCaptor.getValue();
        assertNotNull(withToken.getSetPasswordToken());
        assertTrue(withToken.getSetPasswordTokenExpiresAt().isAfter(Instant.now()));

        verify(email).sendLicenseEmail(eq("buyer@example.com"), eq("Jane Buyer"), anyString(),
                eq("RET-2026-ABCD-EFGH-1234"), eq("perpetual licence"), contains("/account/set-password?token="));
        verify(orders).save(pendingOrder);
    }

    @Test
    void repeatCustomerWithAnExistingPasswordGetsNoSetPasswordLink() {
        Customer existing = new Customer();
        existing.setId(42L);
        existing.setPasswordHash("$2a$10$alreadyHasOne");
        when(customers.findByEmail("buyer@example.com")).thenReturn(Optional.of(existing));

        License issued = new License();
        issued.setId(7L);
        issued.setLicenseKey("RET-2026-ABCD-EFGH-1234");
        when(licenseAdminService.issue(any(), any(), anyInt(), any(), eq(42L), isNull(), anyString(), anyString()))
                .thenReturn(issued);

        fulfillment.fulfil(pendingOrder, "PF-PAYMENT-2");

        verify(customers, never()).save(any()); // no new account, no token to persist
        verify(email).sendLicenseEmail(anyString(), anyString(), anyString(), anyString(), anyString(), isNull());
    }

    @Test
    void subscriptionGetsAThirtyOneDayExpiry() {
        pendingOrder.setLicenseType(LicenseType.SUBSCRIPTION);
        Customer existing = new Customer();
        existing.setId(1L);
        existing.setPasswordHash("hash");
        when(customers.findByEmail(anyString())).thenReturn(Optional.of(existing));
        when(licenseAdminService.issue(any(), any(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(new License());

        fulfillment.fulfil(pendingOrder, "PF-3");

        ArgumentCaptor<Instant> expiry = ArgumentCaptor.forClass(Instant.class);
        verify(licenseAdminService).issue(eq(Product.POS_RETAIL), eq(LicenseType.SUBSCRIPTION), eq(1),
                expiry.capture(), any(), isNull(), anyString(), eq("storefront"));
        assertTrue(expiry.getValue().isAfter(Instant.now().plusSeconds(86_400 * 30)));
        assertTrue(expiry.getValue().isBefore(Instant.now().plusSeconds(86_400 * 32)));
    }

    @Test
    void alreadyPaidOrderIsIgnoredOnARepeatItn() {
        pendingOrder.setStatus(OrderStatus.PAID);

        fulfillment.fulfil(pendingOrder, "PF-DUPLICATE");

        verifyNoInteractions(licenseAdminService, email);
        verify(customers, never()).findByEmail(anyString());
    }
}
