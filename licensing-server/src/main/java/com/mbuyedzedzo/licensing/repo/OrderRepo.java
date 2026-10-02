package com.mbuyedzedzo.licensing.repo;

import com.mbuyedzedzo.licensing.domain.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepo extends JpaRepository<Order, Long> {
    Optional<Order> findByReference(String reference);

    /**
     * Row-locked read, held for the rest of the caller's transaction. PayFast
     * can and does resend ITNs; without this, two concurrent deliveries for
     * the same order could both read status=PENDING before either writes
     * PAID, and both issue a license — a free extra activation. The lock
     * makes the second request block until the first commits, so it then
     * sees PAID and exits via the existing idempotency check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.reference = :reference")
    Optional<Order> findByReferenceForUpdate(@Param("reference") String reference);
}
