package com.domus.api.modules.igreja;

import com.domus.api.config.TokenService;
import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.igreja.dto.CadastroCongregacaoRequest;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.security.AuthCookieFactory;
import com.domus.api.shared.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/igrejas")
@RequiredArgsConstructor
public class CadastroCongregacaoController {

    private final CadastroCongregacaoService cadastroCongregacaoService;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieFactory authCookieFactory;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/registrar-congregacao")
    public ResponseEntity<SessaoDTO> registrarCongregacao(
            @Valid @RequestBody CadastroCongregacaoRequest request,
            HttpServletResponse response) {
        SessaoDTO sessao = cadastroCongregacaoService.registrarCongregacao(request);

        Usuario usuario = usuarioRepository.findById(sessao.id()).orElseThrow();
        String accessToken = tokenService.generateToken(usuario);
        var refreshToken = refreshTokenService.criar(usuario.getId());

        response.addHeader("Set-Cookie", authCookieFactory.access(accessToken).toString());
        response.addHeader("Set-Cookie", authCookieFactory.refresh(refreshToken).toString());

        return ResponseEntity.ok(sessao);
    }
}
