package com.domus.api.modules.admin;

import com.domus.api.modules.admin.dto.AdminLoginRequestDTO;
import com.domus.api.modules.admin.dto.AdminLoginResponseDTO;
import com.domus.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final UsuarioDomusAdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtService adminJwtService;

    @Transactional(readOnly = true)
    public AdminLoginResponseDTO login(AdminLoginRequestDTO request) {
        var admin = adminRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new BusinessException("Credenciais inválidas."));

        if (!admin.isAtivo()) {
            throw new BusinessException("Conta de administrador inativa.");
        }

        if (!passwordEncoder.matches(request.getSenha(), admin.getSenhaHash())) {
            throw new BusinessException("Credenciais inválidas.");
        }

        String token = adminJwtService.generateToken(admin);

        return AdminLoginResponseDTO.builder()
                .id(admin.getId())
                .nome(admin.getNome())
                .email(admin.getEmail())
                .token(token)
                .build();
    }
}
