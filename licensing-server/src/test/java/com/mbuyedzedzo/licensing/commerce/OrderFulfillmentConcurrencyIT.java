package com.mbuyedzedzo.licensing.commerce;

import com.mbuyedzedzo.licensing.domain.*;
import com.mbuyedzedzo.licensing.repo.LicenseRepo;
import com.mbuyedzedzo.licensing.repo.OrderRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the fix for a real race: PayFast can and does resend ITNs, and two
 * concurrent deliveries for the same order must not both issue a license.
 * A unit test with mocks can't prove a database row lock actually works —
 * only two genuinely concurrent connections against a real database can.
 *
 *   mvn -Dlicensing.it.jdbcUrl=jdbc:mysql://localhost:3306/mbuyedzedzo_licensing_test \
 *       -Dlicensing.it.user=root -Dlicensing.it.password=secret test
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "licensing.it.jdbcUrl", matches = ".+")
class OrderFulfillmentConcurrencyIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      () -> System.getProperty("licensing.it.jdbcUrl"));
        r.add("spring.datasource.username", () -> System.getProperty("licensing.it.user", "root"));
        r.add("spring.datasource.password", () -> System.getProperty("licensing.it.password", ""));
        r.add("spring.flyway.clean-disabled", () -> "false");
    }

    @Autowired OrderFulfillmentService fulfillment;
    @Autowired OrderRepo orders;
    @Autowired LicenseRepo licenses;

    private String reference;

    @BeforeEach
    void seed() {
        orders.deleteAll();
        licenses.deleteAll(); // cascades to activation/transfer_log (ON DELETE CASCADE)
        reference = "ORD-CONCURIT01";
        Order order = new Order();
        order.setReference(reference);
        order.setBuyerEmail("concurrency-test@example.com");
        order.setBuyerName("Concurrency Test");
        order.setProduct(Product.POS_RETAIL);
        order.setLicenseType(LicenseType.PERPETUAL);
        order.setAmount(new BigDecimal("2999.00"));
        order.setStatus(OrderStatus.PENDING);
        orders.save(order);
    }

    @Test
    void twoSimultaneousItnsForTheSameOrderIssueExactlyOneLicense() throws Exception {
        int threadCount = 8; // PayFast resending twice is the realistic case; this is deliberately excessive
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger fulfilled = new AtomicInteger();
        AtomicInteger alreadyPaid = new AtomicInteger();

        List<Future<OrderFulfillmentService.Outcome>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final String paymentId = "PF-CONCURRENT-" + i;
            futures.add(pool.submit(() -> {
                startGate.await();
                return fulfillment.handleVerifiedPayment(reference, new BigDecimal("2999.00"), paymentId);
            }));
        }
        startGate.countDown(); // release all threads at once, maximizing overlap
        for (Future<OrderFulfillmentService.Outcome> f : futures) {
            switch (f.get(30, TimeUnit.SECONDS)) {
                case FULFILLED -> fulfilled.incrementAndGet();
                case ALREADY_PAID -> alreadyPaid.incrementAndGet();
                default -> fail("Unexpected outcome");
            }
        }
        pool.shutdown();

        assertEquals(1, fulfilled.get(), "exactly one of the concurrent ITNs should have fulfilled the order");
        assertEquals(threadCount - 1, alreadyPaid.get(), "every other concurrent ITN should see it already paid");

        Order finalState = orders.findByReference(reference).orElseThrow();
        assertEquals(OrderStatus.PAID, finalState.getStatus());
        assertNotNull(finalState.getLicenseId());

        long licensesForThisOrder = licenses.findAll().stream()
                .filter(l -> ("Storefront purchase " + reference).equals(l.getNotes()))
                .count();
        assertEquals(1, licensesForThisOrder, "exactly one license should have been issued, not one per thread");
    }
}
