package com.mvelelo.licensing.admin;

import com.mvelelo.licensing.domain.Customer;
import com.mvelelo.licensing.repo.CustomerRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class CustomerAdminService {

    private final CustomerRepo customers;

    public CustomerAdminService(CustomerRepo customers) {
        this.customers = customers;
    }

    /** List/search, scoped to the agent's own customers unless they're a super admin. */
    public Page<Customer> list(AdminPrincipal me, String q, Pageable pageable) {
        boolean all = me.isSuperAdmin();
        if (q == null || q.isBlank()) {
            return all ? customers.findAll(pageable) : customers.findByCreatedBy(me.id(), pageable);
        }
        return all ? customers.search(q, pageable) : customers.searchForAgent(q, me.id(), pageable);
    }

    public Customer get(AdminPrincipal me, long id) {
        Customer c = customers.findById(id).orElseThrow(() -> new NotFound("customer " + id));
        if (!me.isSuperAdmin() && !c.getCreatedBy().equals(me.id())) {
            throw new NotFound("customer " + id);   // don't leak existence to other agents
        }
        return c;
    }

    public Customer create(AdminPrincipal me, String org, String contact, String email, String phone, String notes) {
        Customer c = new Customer();
        c.setOrgName(org.trim());
        c.setContactName(contact.trim());
        c.setEmail(email.trim());
        c.setPhone(phone == null ? null : phone.trim());
        c.setNotes(notes == null ? null : notes.trim());
        c.setCreatedBy(me.id());
        return customers.save(c);
    }

    public static class NotFound extends RuntimeException {
        public NotFound(String m) { super(m); }
    }
}
