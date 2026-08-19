package com.domus.api.modules.pessoa;

import com.domus.api.modules.foto.Foto;
import com.domus.api.modules.igreja.Igreja;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pessoa tem @SQLRestriction("deleted_at IS NULL"); só banco real prova que o UPDATE nativo bate na pessoa arquivada sem ser filtrado. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PessoaRepositoryDesvincularFotoTest {

    @Autowired
    PessoaRepository pessoaRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    void desvinculaFotoDePessoaArquivada() {
        Igreja igreja = Igreja.builder()
                .nome("Igreja de Teste").emailContato("teste@teste.com").build();
        entityManager.persist(igreja);

        Foto foto = Foto.builder()
                .igreja(igreja).chave("fotos/teste/" + UUID.randomUUID())
                .tipo("image/jpeg").bytes(100L).build();
        entityManager.persist(foto);

        Pessoa pessoa = Pessoa.builder()
                .igreja(igreja).nome("Fulano de Teste").vinculo(Vinculo.CONGREGANTE)
                .foto(foto).build();
        pessoaRepository.save(pessoa);
        entityManager.flush();

        // Arquiva a pessoa (soft delete via @SQLDelete) — ela some de qualquer JPQL a
        // partir daqui, mas continua com foto_id apontando pra foto.
        pessoaRepository.delete(pessoa);
        entityManager.flush();
        entityManager.clear();

        pessoaRepository.desvincularFoto(foto.getId());
        entityManager.flush();
        entityManager.clear();

        Object fotoIdDepois = entityManager
                .createNativeQuery("SELECT foto_id FROM pessoa WHERE id = :id")
                .setParameter("id", pessoa.getId())
                .getSingleResult();
        assertThat(fotoIdDepois).isNull();
    }
}
