package com.mbuyedzedzo.licensing.admin;

import com.mbuyedzedzo.licensing.config.LicensingProperties;
import com.mbuyedzedzo.licensing.domain.AdminUser;
import com.mbuyedzedzo.licensing.domain.Role;
import com.mbuyedzedzo.licensing.repo.AdminUserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first SUPER_ADMIN from config if the table has none — or, if
 * {@code BOOTSTRAP_ADMIN_RESET=true} is set, (re)creates/resets the
 * BOOTSTRAP_ADMIN_EMAIL/PASSWORD account on every startup even when admins
 * already exist. That's the supported way to issue yourself fresh admin
 * credentials on a live deployment: set BOOTSTRAP_ADMIN_EMAIL,
 * BOOTSTRAP_ADMIN_PASSWORD and BOOTSTRAP_ADMIN_RESET=true as App Service
 * settings, restart, log in, then remove BOOTSTRAP_ADMIN_RESET (or set it
 * back to false) so a later restart can't silently reset it again.
 */
@Component
class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final AdminUserRepo repo;
    private final PasswordEncoder encoder;
    private final LicensingProperties props;
    private final boolean resetRequested;

    BootstrapAdmin(AdminUserRepo repo, PasswordEncoder encoder, LicensingProperties props,
                   @Value("${BOOTSTRAP_ADMIN_RESET:false}") boolean resetRequested) {
        this.repo = repo;
        this.encoder = encoder;
        this.props = props;
        this.resetRequested = resetRequested;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean hasSuperAdmin = repo.countByRole(Role.SUPER_ADMIN) > 0;
        if (hasSuperAdmin && !resetRequested) return;

        var cfg = props.bootstrapAdmin();
        AdminUser a = repo.findByEmailIgnoreCase(cfg.email()).orElseGet(AdminUser::new);
        a.setEmail(cfg.email());
        a.setPasswordHash(encoder.encode(cfg.password()));
        a.setFullName("Administrator");
        a.setRole(Role.SUPER_ADMIN);
        a.setActive(true);
        repo.save(a);

        if (hasSuperAdmin) {
            log.warn("BOOTSTRAP_ADMIN_RESET=true: reset SUPER_ADMIN '{}'. "
                    + "Remove BOOTSTRAP_ADMIN_RESET now that you've logged in.", cfg.email());
        } else {
            log.warn("Created bootstrap SUPER_ADMIN '{}'. Change this password after first login.", cfg.email());
        }
    }
}
