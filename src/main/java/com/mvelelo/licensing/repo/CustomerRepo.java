package com.mvelelo.licensing.repo;

import com.mvelelo.licensing.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepo extends JpaRepository<Customer, Long> {

    Page<Customer> findByCreatedBy(Long createdBy, Pageable pageable);

    @Query("""
           select c from Customer c
           where lower(c.orgName)     like lower(concat('%', :q, '%'))
              or lower(c.contactName) like lower(concat('%', :q, '%'))
              or lower(c.email)       like lower(concat('%', :q, '%'))
           """)
    Page<Customer> search(@Param("q") String q, Pageable pageable);

    @Query("""
           select c from Customer c
           where c.createdBy = :createdBy and (
                 lower(c.orgName)     like lower(concat('%', :q, '%'))
              or lower(c.contactName) like lower(concat('%', :q, '%'))
              or lower(c.email)       like lower(concat('%', :q, '%')))
           """)
    Page<Customer> searchForAgent(@Param("q") String q, @Param("createdBy") Long createdBy, Pageable pageable);
}
