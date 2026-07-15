package com.domus.api.modules.auth;


import com.domus.api.modules.auth.DTO.AuthenticationDTO;
import com.domus.api.modules.auth.DTO.ForgotPasswordDTO;
import com.domus.api.modules.auth.DTO.GoogleLoginDTO;
import com.domus.api.modules.auth.DTO.GoogleRegistrarDTO;
import com.domus.api.modules.auth.DTO.LoginResponseDTO;
import com.domus.api.modules.auth.DTO.RefreshRequestDTO;
import com.domus.api.modules.auth.DTO.ResetPasswordDTO;
import com.domus.api.modules.auth.DTO.TokenPairDTO;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final GoogleAuthService googleAuthService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid AuthenticationDTO data ) {
        return ResponseEntity.ok(authService.login(data));
    }

    @PostMapping("/google/login")
    public ResponseEntity<LoginResponseDTO> googleLogin(@RequestBody @Valid GoogleLoginDTO data) {
        return ResponseEntity.ok(googleAuthService.login(data.idToken()));
    }

    @PostMapping("/google/registrar")
    public ResponseEntity<RegistrarIgrejaResponse> googleRegistrar(@RequestBody @Valid GoogleRegistrarDTO data) {
        return ResponseEntity.ok(googleAuthService.registrar(data));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairDTO> refresh(@RequestBody @Valid RefreshRequestDTO data) {
        return ResponseEntity.ok(authService.refresh(data.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid RefreshRequestDTO data) {
        authService.logout(data.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody @Valid ForgotPasswordDTO data) {
        passwordResetService.solicitar(data.email());
        // Resposta genérica de propósito: não revela se o e-mail existe (anti-enumeração).
        return ResponseEntity.ok(Map.of(
                "message", "Se houver uma conta com esse e-mail, enviamos um link para redefinir a senha."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody @Valid ResetPasswordDTO data) {
        passwordResetService.redefinir(data.token(), data.novaSenha());
        return ResponseEntity.ok(Map.of("message", "Senha redefinida com sucesso. Faça login com a nova senha."));
    }
}
