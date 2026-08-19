package com.domus.api.modules.auth;


import com.domus.api.modules.auth.DTO.AuthenticationDTO;
import com.domus.api.modules.auth.DTO.ChangePasswordDTO;
import com.domus.api.modules.auth.DTO.ForgotPasswordDTO;
import com.domus.api.modules.auth.DTO.GoogleLoginDTO;
import com.domus.api.modules.auth.DTO.GoogleRegistrarDTO;
import com.domus.api.modules.auth.DTO.LoginResponseDTO;
import com.domus.api.modules.auth.DTO.ResetPasswordDTO;
import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.auth.DTO.TokenPairDTO;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.shared.exception.SessaoExpiradaException;
import com.domus.api.shared.security.AuthCookieFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final AuthCookieFactory cookieFactory;
    private final com.domus.api.shared.web.ClienteIpResolver clienteIpResolver;
    private final com.domus.api.modules.termos.TermoAceiteService termoAceiteService;

    @PostMapping("/login")
    public ResponseEntity<SessaoDTO> login(@RequestBody @Valid AuthenticationDTO data) {
        LoginResponseDTO r = authService.login(data);
        return comCookies(r.token(), r.refreshToken()).body(sessaoDe(r));
    }

    @PostMapping("/google/login")
    public ResponseEntity<SessaoDTO> googleLogin(@RequestBody @Valid GoogleLoginDTO data) {
        LoginResponseDTO r = googleAuthService.login(data.idToken());
        return comCookies(r.token(), r.refreshToken()).body(sessaoDe(r));
    }

    @PostMapping("/google/registrar")
    public ResponseEntity<SessaoDTO> googleRegistrar(
            @RequestBody @Valid GoogleRegistrarDTO data,
            jakarta.servlet.http.HttpServletRequest request) {
        RegistrarIgrejaResponse r = googleAuthService.registrar(data, clienteIpResolver.resolver(request));
        return comCookies(r.token(), r.refreshToken())
                .body(new SessaoDTO(r.id(), r.nome(), r.role(), r.igrejaId(), r.igrejaNome(),
                        null, null, null, null, java.util.List.of(),
                        termoAceiteService.precisaAceitar(r.id()),
                        termoAceiteService.dataUltimoAceite(r.id())));
    }

    // O principal é carregado no SecurityFilter (antes do open-in-view): ler campo LAZY lança
    // LazyInitializationException. Usa o ID e busca o resto.
    @GetMapping("/me")
    public ResponseEntity<SessaoDTO> me(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(authService.sessaoDe(usuario.getId()));
    }

    @PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal Usuario usuario,
            @CookieValue(name = AuthCookieFactory.COOKIE_REFRESH, required = false) String refreshToken,
            @RequestBody @Valid ChangePasswordDTO data) {
        authService.alterarSenha(usuario.getId(), refreshToken, data);
        return ResponseEntity.ok(Map.of("message", "Senha alterada com sucesso."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = AuthCookieFactory.COOKIE_REFRESH, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new SessaoExpiradaException("REFRESH_INVALIDO", "Sessão expirada. Faça login novamente.");
        }
        TokenPairDTO par = authService.refresh(refreshToken);
        return comCookies(par.token(), par.refreshToken()).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookieFactory.COOKIE_REFRESH, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        // Expira os cookies mesmo sem refresh válido: o JS não consegue apagá-los sozinho.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.accessExpirado().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refreshExpirado().toString())
                .build();
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

    private ResponseEntity.BodyBuilder comCookies(String access, String refresh) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.access(access).toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refresh(refresh).toString());
    }

    private SessaoDTO sessaoDe(LoginResponseDTO r) {
        return new SessaoDTO(r.id(), r.nome(), r.role(), r.igrejaId(), r.igrejaNome(),
                r.fotoId(), r.cargo(), r.igrejaSigla(), r.igrejaLogoId(), r.capacidadesExtras(),
                termoAceiteService.precisaAceitar(r.id()), termoAceiteService.dataUltimoAceite(r.id()));
    }
}
