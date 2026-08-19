package com.domus.api.modules.auth;

import com.domus.api.config.TokenService;
import com.domus.api.modules.auth.DTO.GoogleRegistrarDTO;
import com.domus.api.modules.auth.DTO.LoginResponseDTO;
import com.domus.api.modules.igreja.DadosNovaIgreja;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse;
import com.domus.api.modules.igreja.IgrejaService;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioCapacidade;
import com.domus.api.modules.usuario.UsuarioCapacidadeRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.security.RefreshTokenService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Google só identifica a pessoa; a emissão de sessão (JWT + refresh) reusa o caminho do login nativo. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final IgrejaService igrejaService;
    private final UsuarioCapacidadeRepository capacidadeRepository;

    public LoginResponseDTO login(String idToken) {
        GoogleIdToken.Payload payload = verificar(idToken);
        String sub = payload.getSubject();
        String email = payload.getEmail();

        // Vínculo: primeiro por google_sub (rápido e imune a troca de e-mail);
        // se não achar, por e-mail — e nesse caso grava o sub para as próximas vezes.
        Usuario usuario = usuarioRepository.findByGoogleSub(sub).orElse(null);
        if (usuario == null) {
            usuario = usuarioRepository.findByEmail(email).orElse(null);
            if (usuario != null) {
                usuario.setGoogleSub(sub);
                usuarioRepository.save(usuario);
                log.info("Vínculo Google criado no primeiro login. usuario_id={}", usuario.getId());
            }
        }

        if (usuario == null) {
            throw new BusinessException("CONTA_NAO_ENCONTRADA",
                    "Não encontramos uma conta vinculada a este Google. Se você é responsável por uma igreja, cadastre-a primeiro. Se você é membro de uma igreja já cadastrada, peça ao administrador dela para conceder seu acesso.");
        }
        if (!usuario.isAtivo()) {
            throw new BusinessException("USUARIO_INATIVO",
                    "Sua conta está desativada. Entre em contato com o administrador.");
        }

        usuario.registrarLogin();
        usuarioRepository.save(usuario);

        String token = tokenService.generateToken(usuario);
        String refreshToken = refreshTokenService.criar(usuario.getId());
        log.info("Login Google bem-sucedido. usuario_id={}, igreja_id={}", usuario.getId(), usuario.getIgreja().getId());

        return new LoginResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getRole().getNome(),
                usuario.getIgreja().getId(),
                usuario.getIgreja().getNome(),
                usuarioRepository.findFotoIdById(usuario.getId()),
                usuario.getPessoa().getCargo(),
                usuario.getIgreja().getSigla(),
                usuario.getIgreja().getLogoFoto() != null
                        ? usuario.getIgreja().getLogoFoto().getId() : null,
                token,
                refreshToken,
                capacidadeRepository.findByUsuarioId(usuario.getId()).stream()
                        .map(UsuarioCapacidade::getCapacidade).toList()
        );
    }

    public RegistrarIgrejaResponse registrar(GoogleRegistrarDTO dados, String ip) {
        GoogleIdToken.Payload payload = verificar(dados.idToken());
        String sub = payload.getSubject();
        String email = payload.getEmail();
        String nome = (String) payload.get("name");

        Usuario admin = igrejaService.criarIgrejaComAdmin(new DadosNovaIgreja(
                dados.nomeIgreja(),
                email,                     // emailContato = e-mail do dono (verificado pelo Google)
                dados.cnpj(),
                dados.telefoneContato(),
                nome,
                email,
                null,                      // sem senha nativa (conta só-Google)
                sub
        ), dados.aceitouTermos(), ip);

        String token = tokenService.generateToken(admin);
        String refreshToken = refreshTokenService.criar(admin.getId());
        log.info("Cadastro Google concluído. usuario_id={}, igreja_id={}", admin.getId(), admin.getIgreja().getId());

        return new RegistrarIgrejaResponse(
                admin.getId(),
                token,
                refreshToken,
                admin.getNome(),
                admin.getRole().getNome(),
                admin.getIgreja().getId(),
                admin.getIgreja().getNome()
        );
    }

    /** Reautenticação (step-up auth) fora do fluxo de login — ex.: confirmar exclusão de igreja. */
    public String reautenticarPorGoogle(String idToken) {
        return verificar(idToken).getSubject();
    }

    /**
     * Valida o ID token contra o Google (assinatura, aud, validade) e exige e-mail verificado.
     * Lança TOKEN_GOOGLE_INVALIDO em qualquer falha.
     */
    GoogleIdToken.Payload verificar(String idToken) {
        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (Exception e) {
            log.warn("Falha ao verificar ID token do Google.", e);
            throw new BusinessException("TOKEN_GOOGLE_INVALIDO", "Não foi possível validar seu login com o Google. Tente novamente.");
        }
        if (token == null || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())) {
            throw new BusinessException("TOKEN_GOOGLE_INVALIDO", "Não foi possível validar seu login com o Google. Tente novamente.");
        }
        return token.getPayload();
    }
}
