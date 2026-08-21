package com.domus.api.modules.usuario;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrincipalCacheServiceTest {

    UsuarioRepository usuarioRepository;
    PrincipalCacheService service;

    @BeforeEach
    void setup() {
        usuarioRepository = mock(UsuarioRepository.class);
        service = new PrincipalCacheService(usuarioRepository);
    }

    @Test
    void buscarMapeiaSoOsCamposUsadosPeloPrincipal() {
        UUID usuarioId = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Usuario usuario = Usuario.builder()
                .id(usuarioId)
                .ativo(true)
                .igreja(Igreja.builder().id(igrejaId).build())
                .pessoa(Pessoa.builder().id(pessoaId).build())
                .role(Role.builder().id(roleId).nome("LIDER").build())
                .build();
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        PrincipalCache cache = service.buscar(usuarioId);

        assertThat(cache.id()).isEqualTo(usuarioId);
        assertThat(cache.igrejaId()).isEqualTo(igrejaId);
        assertThat(cache.pessoaId()).isEqualTo(pessoaId);
        assertThat(cache.roleId()).isEqualTo(roleId);
        assertThat(cache.roleNome()).isEqualTo("LIDER");
        assertThat(cache.ativo()).isTrue();
    }

    @Test
    void buscarDevolveNullQuandoUsuarioNaoExiste() {
        UUID usuarioId = UUID.randomUUID();
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThat(service.buscar(usuarioId)).isNull();
    }

    @Test
    void reidratarReconstroiUsuarioComOsMesmosCampos() {
        PrincipalCache cache = new PrincipalCache(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "SECRETARIO", false);

        Usuario usuario = service.reidratar(cache);

        assertThat(usuario.getId()).isEqualTo(cache.id());
        assertThat(usuario.isAtivo()).isEqualTo(cache.ativo());
        assertThat(usuario.getIgreja().getId()).isEqualTo(cache.igrejaId());
        assertThat(usuario.getPessoa().getId()).isEqualTo(cache.pessoaId());
        assertThat(usuario.getRole().getId()).isEqualTo(cache.roleId());
        assertThat(usuario.getRole().getNome()).isEqualTo(cache.roleNome());
    }
}
