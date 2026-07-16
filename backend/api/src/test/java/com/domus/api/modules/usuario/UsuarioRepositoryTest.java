package com.domus.api.modules.usuario;

import com.domus.api.modules.auth.DTO.SessaoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// replace = NONE: usa o datasource configurado (Neon de testes), não um H2 em memória.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsuarioRepositoryTest {

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

    /**
     * Este teste existe por causa de um bug real (2026-07-16): o /auth/me lia
     * {@code usuario.getIgreja().getNome()} do principal do Spring Security — uma entidade
     * DESANEXADA, porque o SecurityFilter é um servlet filter e roda antes do open-in-view.
     * Resultado: LazyInitializationException a cada reload da página.
     *
     * <p>Nenhum teste com Mockito pegaria isso (lá a entidade é montada na mão e não há
     * lazy loading). Só uma consulta contra o banco de verdade prova que a projeção monta o
     * DTO inteiro — inclusive {@code igrejaNome}, o campo que estourava.
     */
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
