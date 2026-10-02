package com.mbuyedzedzo.licensing.repo;

import com.mbuyedzedzo.licensing.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepo extends JpaRepository<Order, Long> {
    Optional<Order> findByReference(String reference);
}
