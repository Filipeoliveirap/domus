package com.domus.api.modules.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminDataInitializer implements CommandLineRunner {

    private final UsuarioDomusAdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${domus.admin.email:admin@domus.com}")
    private String adminEmail;

    @Value("${domus.admin.password:AdminDomus2026!}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        if (adminRepository.count() == 0) {
            log.info("Nenhum administrador Domus encontrado. Criando superadmin inicial a partir das configurações...");
            var admin = UsuarioDomusAdmin.builder()
                    .nome("SuperAdmin Domus")
                    .email(adminEmail.toLowerCase().trim())
                    .senhaHash(passwordEncoder.encode(adminPassword))
                    .ativo(true)
                    .build();

            adminRepository.save(admin);
            log.info("Superadmin inicial criado com sucesso. email={}", adminEmail);
        }
    }
}
