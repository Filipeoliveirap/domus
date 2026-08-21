package com.domus.api.modules.usuario;

import com.domus.api.modules.auth.DTO.SessaoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

// replace = NONE: usa o datasource configurado (Neon de testes), não um H2 em memória.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsuarioRepositoryTest implements PostgresTestContainerSupport {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Test
    void findByGoogleSub_retornaVazioQuandoNaoExiste() {
        assertThat(usuarioRepository.findByGoogleSub("sub-inexistente")).isEmpty();
    }

    @Test
    void findSessaoById_retornaVazioQuandoNaoExiste() {
        assertThat(usuarioRepository.findSessaoById(UUID.randomUUID())).isEmpty();
    }

    /** SecurityFilter roda antes do open-in-view, então o principal vem desanexado — ler campo LAZY dele (ex.: igrejaNome) estoura LazyInitializationException. */
    @Test
    void findSessaoById_montaODtoInteiroSemDependerDeLazyLoading() {
        Optional<Usuario> algum = usuarioRepository.findAll().stream().findFirst();
        // O banco de testes pode estar vazio; sem dado não há o que afirmar.
        org.junit.jupiter.api.Assumptions.assumeTrue(algum.isPresent(),
                "sem usuário no banco de testes para exercitar a projeção");

        Optional<SessaoDTO> sessao = usuarioRepository.findSessaoById(algum.get().getId());

        assertThat(sessao).isPresent();
        assertThat(sessao.get().id()).isEqualTo(algum.get().getId());
        assertThat(sessao.get().nome()).isNotBlank();
        assertThat(sessao.get().role()).isNotBlank();
        assertThat(sessao.get().igrejaId()).isNotNull();
        // O campo que estourava com LazyInitializationException.
        assertThat(sessao.get().igrejaNome()).isNotBlank();
    }
}
