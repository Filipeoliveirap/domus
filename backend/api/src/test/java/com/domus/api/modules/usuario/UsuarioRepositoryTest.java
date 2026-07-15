package com.domus.api.modules.usuario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

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
}
