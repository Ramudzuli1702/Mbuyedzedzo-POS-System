package com.mvelelo.licensing.admin;

import com.mvelelo.licensing.config.LicensingProperties;
import com.mvelelo.licensing.domain.AdminUser;
import com.mvelelo.licensing.domain.Role;
import com.mvelelo.licensing.repo.AdminUserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first SUPER_ADMIN from config if the table has none. */
@Component
class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final AdminUserRepo repo;
    private final PasswordEncoder encoder;
    private final LicensingProperties props;

    BootstrapAdmin(AdminUserRepo repo, PasswordEncoder encoder, LicensingProperties props) {
        this.repo = repo;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repo.countByRole(Role.SUPER_ADMIN) > 0) return;

        var cfg = props.bootstrapAdmin();
        AdminUser a = new AdminUser();
        a.setEmail(cfg.email());
        a.setPasswordHash(encoder.encode(cfg.password()));
        a.setFullName("Administrator");
        a.setRole(Role.SUPER_ADMIN);
        a.setActive(true);
        repo.save(a);

        log.warn("Created bootstrap SUPER_ADMIN '{}'. Change this password after first login.", cfg.email());
    }
}
