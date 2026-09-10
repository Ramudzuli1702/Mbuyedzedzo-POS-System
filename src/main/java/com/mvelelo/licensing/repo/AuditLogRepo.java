package com.mvelelo.licensing.repo;

import com.mvelelo.licensing.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepo extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findAllByOrderByAtDesc(Pageable pageable);
}
