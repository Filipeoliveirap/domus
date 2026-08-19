package com.domus.api.modules.igreja;

import com.domus.api.config.TokenService;
import com.domus.api.shared.security.RefreshTokenService;
import com.domus.api.modules.igreja.DTO.IgrejaDetalheDTO;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.termos.TermoAceiteService;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IgrejaServiceTest {

    IgrejaRepository igrejaRepository;
    UsuarioRepository usuarioRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    TokenService tokenService;
    RefreshTokenService refreshTokenService;
    PessoaRepository pessoaRepository;
    CacheManager cacheManager;
    FotoService fotoService;
    OutboxRegistrador outboxRegistrador;
    TermoAceiteService termoAceiteService;
    IgrejaService igrejaService;

    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        igrejaRepository = mock(IgrejaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenService = mock(TokenService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        pessoaRepository = mock(PessoaRepository.class);
        cacheManager = mock(CacheManager.class);
        fotoService = mock(FotoService.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        termoAceiteService = mock(TermoAceiteService.class);
        igrejaService = new IgrejaService(
                igrejaRepository, usuarioRepository, roleRepository, passwordEncoder,
                tokenService, refreshTokenService, pessoaRepository, cacheManager,
                fotoService, outboxRegistrador, termoAceiteService);
    }

    @Test
    void detalheTrazDiasRestantesQuandoExclusaoAgendada() {
        Igreja igreja = Igreja.builder().id(igrejaId).nome("Teste")
                .exclusaoAgendadaEm(LocalDateTime.now().minusDays(3))
                .build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        IgrejaDetalheDTO dto = igrejaService.buscarDetalhe(igrejaId);

        assertThat(dto.exclusaoAgendadaEm()).isNotNull();
        assertThat(dto.diasRestantes()).isEqualTo(7);
    }

    @Test
    void detalheSemExclusaoAgendadaTemDiasRestantesNulo() {
        Igreja igreja = Igreja.builder().id(igrejaId).nome("Teste").build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        IgrejaDetalheDTO dto = igrejaService.buscarDetalhe(igrejaId);

        assertThat(dto.exclusaoAgendadaEm()).isNull();
        assertThat(dto.diasRestantes()).isNull();
    }

    @Test
    void criarIgrejaComAdminLancaQuandoNaoAceitouTermos() {
        doThrow(new BusinessException("TERMOS_NAO_ACEITOS", "É necessário aceitar os Termos de Uso e a Política de Privacidade para continuar."))
                .when(termoAceiteService).exigirAceite(false);

        assertThatThrownBy(() -> igrejaService.criarIgrejaComAdmin(
                new DadosNovaIgreja("Igreja X", "contato@x.com", null, "11999999999",
                        "Admin", "admin@x.com", "hash", null),
                false, "203.0.113.9"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Termos");

        verify(igrejaRepository, never()).save(any());
    }

    @Test
    void criarIgrejaComAdminRegistraAceiteQuandoTrue() {
        when(pessoaRepository.existsByEmail(anyString())).thenReturn(false);
        when(igrejaRepository.save(any(Igreja.class))).thenAnswer(inv -> {
            Igreja i = inv.getArgument(0);
            i.setId(igrejaId);
            return i;
        });
        when(roleRepository.findByNome("ADMIN_IGREJA")).thenReturn(Optional.of(
                Role.builder().id(UUID.randomUUID()).nome("ADMIN_IGREJA").build()));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        igrejaService.criarIgrejaComAdmin(
                new DadosNovaIgreja("Igreja Y", "contato@y.com", null, "11999999999",
                        "Admin", "admin@y.com", "hash", null),
                true, "203.0.113.9");

        verify(termoAceiteService).registrarAceite(any(UUID.class), eq("203.0.113.9"));
    }
}
