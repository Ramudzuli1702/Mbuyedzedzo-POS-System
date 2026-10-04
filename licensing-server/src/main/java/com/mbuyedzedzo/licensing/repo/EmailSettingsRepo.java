package com.mbuyedzedzo.licensing.repo;

import com.mbuyedzedzo.licensing.domain.EmailSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailSettingsRepo extends JpaRepository<EmailSettings, Long> {
}
