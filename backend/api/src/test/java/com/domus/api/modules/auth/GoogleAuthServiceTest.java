package com.domus.api.modules.auth;

import com.domus.api.config.TokenService;
import com.domus.api.modules.auth.DTO.LoginResponseDTO;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaService;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.security.RefreshTokenService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GoogleAuthServiceTest {

    GoogleIdTokenVerifier verifier;
    UsuarioRepository usuarioRepository;
    TokenService tokenService;
    RefreshTokenService refreshTokenService;
    IgrejaService igrejaService;
    GoogleAuthService service;

    @BeforeEach
    void setup() {
        verifier = mock(GoogleIdTokenVerifier.class);
        usuarioRepository = mock(UsuarioRepository.class);
        tokenService = mock(TokenService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        igrejaService = mock(IgrejaService.class);
        service = new GoogleAuthService(verifier, usuarioRepository, tokenService, refreshTokenService, igrejaService);
    }

    // Constrói um GoogleIdToken REAL (não mock) — getPayload() é final e não pode ser stubado.
    // Assinaturas vazias bastam: quem valida assinatura é o verifier, que está mockado nos testes.
    private GoogleIdToken tokenComPayload(String sub, String email, boolean emailVerified, String nome) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(sub);
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        payload.set("name", nome);
        JsonWebSignature.Header header = new JsonWebSignature.Header();
        return new GoogleIdToken(header, payload, new byte[0], new byte[0]);
    }

    private Usuario usuarioFake() {
        Igreja igreja = Igreja.builder().nome("Igreja X").build();
        igreja.setId(UUID.randomUUID());
        Membro membro = Membro.builder().nome("Fulano").email("fulano@x.com").igreja(igreja).build();
        Role role = Role.builder().nome("ADMIN_IGREJA").build();
        return Usuario.builder()
                .id(UUID.randomUUID())
                .igreja(igreja)
                .membro(membro)
                .role(role)
                .ativo(true)
                .build();
    }

    @Test
    void login_tokenInvalido_lancaTokenGoogleInvalido() throws Exception {
        when(verifier.verify("ruim")).thenReturn(null);
        assertThatThrownBy(() -> service.login("ruim"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Google");
    }

    @Test
    void login_emailNaoVerificado_lancaTokenGoogleInvalido() throws Exception {
        when(verifier.verify("t")).thenReturn(tokenComPayload("sub1", "a@a.com", false, "A"));
        assertThatThrownBy(() -> service.login("t")).isInstanceOf(BusinessException.class);
    }

    @Test
    void login_achaPorGoogleSub_emiteSessao() throws Exception {
        when(verifier.verify("t")).thenReturn(tokenComPayload("sub1", "a@a.com", true, "A"));
        Usuario u = usuarioFake();
        u.setGoogleSub("sub1");
        when(usuarioRepository.findByGoogleSub("sub1")).thenReturn(Optional.of(u));
        when(tokenService.generateToken(u)).thenReturn("jwt");
        when(refreshTokenService.criar(u.getId())).thenReturn("refresh");

        LoginResponseDTO resp = service.login("t");

        assertThat(resp.token()).isEqualTo("jwt");
        assertThat(resp.refreshToken()).isEqualTo("refresh");
        verify(usuarioRepository, never()).findByEmail(any());
    }

    @Test
    void login_achaPorEmail_gravaGoogleSub() throws Exception {
        when(verifier.verify("t")).thenReturn(tokenComPayload("sub1", "a@a.com", true, "A"));
        Usuario u = usuarioFake();
        when(usuarioRepository.findByGoogleSub("sub1")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("a@a.com")).thenReturn(Optional.of(u));
        when(tokenService.generateToken(u)).thenReturn("jwt");
        when(refreshTokenService.criar(u.getId())).thenReturn("refresh");

        service.login("t");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(saved -> "sub1".equals(saved.getGoogleSub()));
    }

    @Test
    void login_naoAcha_lancaContaNaoEncontrada() throws Exception {
        when(verifier.verify("t")).thenReturn(tokenComPayload("sub1", "a@a.com", true, "A"));
        when(usuarioRepository.findByGoogleSub("sub1")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("a@a.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("t"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Não encontramos");
    }

    @Test
    void registrar_criaIgrejaComAdminSemSenha() throws Exception {
        when(verifier.verify("t")).thenReturn(tokenComPayload("sub9", "dono@ig.com", true, "Dono"));
        Usuario admin = usuarioFake();
        admin.setGoogleSub("sub9");
        when(igrejaService.criarIgrejaComAdmin(any())).thenReturn(admin);
        when(tokenService.generateToken(admin)).thenReturn("jwt");
        when(refreshTokenService.criar(admin.getId())).thenReturn("refresh");

        var dados = new com.domus.api.modules.auth.DTO.GoogleRegistrarDTO("t", "Nova Igreja", null, "11999999999");
        var resp = service.registrar(dados);

        assertThat(resp.token()).isEqualTo("jwt");
        assertThat(resp.refreshToken()).isEqualTo("refresh");

        ArgumentCaptor<com.domus.api.modules.igreja.DadosNovaIgreja> captor =
                ArgumentCaptor.forClass(com.domus.api.modules.igreja.DadosNovaIgreja.class);
        verify(igrejaService).criarIgrejaComAdmin(captor.capture());
        assertThat(captor.getValue().senhaHashOuNull()).isNull();
        assertThat(captor.getValue().googleSubOuNull()).isEqualTo("sub9");
        assertThat(captor.getValue().emailAdmin()).isEqualTo("dono@ig.com");
        assertThat(captor.getValue().nomeAdmin()).isEqualTo("Dono");
    }

    @Test
    void registrar_tokenInvalido_lanca() throws Exception {
        when(verifier.verify("t")).thenReturn(null);
        var dados = new com.domus.api.modules.auth.DTO.GoogleRegistrarDTO("t", "Nova Igreja", null, "11999999999");
        assertThatThrownBy(() -> service.registrar(dados)).isInstanceOf(BusinessException.class);
    }
}
