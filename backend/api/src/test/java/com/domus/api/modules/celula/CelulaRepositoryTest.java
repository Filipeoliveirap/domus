package com.domus.api.modules.celula;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// replace = NONE: usa o datasource configurado (Neon de testes), não um H2 em memória.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CelulaRepositoryTest {

    @Autowired CelulaRepository celulaRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired EntityManager entityManager;

    @Test
    void findArquivadasPorIgrejaTrazSoAsArquivadas() {
        Igreja igreja = igrejaRepository.save(Igreja.builder().nome("Igreja Teste").emailContato("teste-" + java.util.UUID.randomUUID() + "@teste.com").build());

        Celula ativa = celulaRepository.save(
                Celula.builder().igreja(igreja).nome("Ativa " + UUID.randomUUID()).build());
        Celula arquivada = celulaRepository.save(
                Celula.builder().igreja(igreja).nome("Arquivada " + UUID.randomUUID()).build());
        celulaRepository.delete(arquivada); // soft delete via @SQLDelete
        entityManager.flush();
        entityManager.clear();

        List<Celula> arquivadas = celulaRepository.findArquivadasPorIgreja(igreja.getId());

        assertThat(arquivadas).extracting(Celula::getId).containsExactly(arquivada.getId());
        assertThat(arquivadas).extracting(Celula::getId).doesNotContain(ativa.getId());
    }

    @Test
    void restaurarPorIdTiraDoArquivo() {
        Igreja igreja = igrejaRepository.save(Igreja.builder().nome("Igreja Teste").emailContato("teste-" + java.util.UUID.randomUUID() + "@teste.com").build());
        Celula celula = celulaRepository.save(
                Celula.builder().igreja(igreja).nome("Vai e volta " + UUID.randomUUID()).build());
        UUID id = celula.getId();
        celulaRepository.delete(celula);
        entityManager.flush();
        entityManager.clear();

        celulaRepository.restaurarPorId(id);
        entityManager.flush();
        entityManager.clear();

        assertThat(celulaRepository.findById(id)).isPresent();
    }
}
