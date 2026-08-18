package com.domus.api.modules.igreja;

import com.domus.api.config.TokenService;
import com.domus.api.shared.security.RefreshTokenService;
import com.domus.api.modules.igreja.DTO.IgrejaDetalheDTO;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        igrejaService = new IgrejaService(
                igrejaRepository, usuarioRepository, roleRepository, passwordEncoder,
                tokenService, refreshTokenService, pessoaRepository, cacheManager,
                fotoService, outboxRegistrador);
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
}
