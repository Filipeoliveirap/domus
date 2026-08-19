package com.domus.api.modules.celula;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
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
    @Autowired CelulaMembroRepository celulaMembroRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
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

        int linhas = celulaRepository.restaurarPorId(id, igreja.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(linhas).isEqualTo(1);
        assertThat(celulaRepository.findById(id)).isPresent();
    }

    @Test
    void restaurarPorIdNaoAfetaCelulaDeOutraIgreja() {
        Igreja igrejaDona = igrejaRepository.save(
                Igreja.builder().nome("Igreja Dona").emailContato("dona-" + UUID.randomUUID() + "@teste.com").build());
        Igreja igrejaInvasora = igrejaRepository.save(
                Igreja.builder().nome("Igreja Invasora").emailContato("invasora-" + UUID.randomUUID() + "@teste.com").build());
        Celula celula = celulaRepository.save(
                Celula.builder().igreja(igrejaDona).nome("Não é sua " + UUID.randomUUID()).build());
        UUID id = celula.getId();
        celulaRepository.delete(celula);
        entityManager.flush();
        entityManager.clear();

        int linhas = celulaRepository.restaurarPorId(id, igrejaInvasora.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(linhas).isEqualTo(0);
        assertThat(celulaRepository.findById(id)).isEmpty(); // continua arquivada
    }

    @Test
    void hardDeleteByIdFuncionaAposApagarMembrosNaMesmaTransacao() {
        // Reproduz o bug real: hardDeleteById é SQL nativo e não enxerga o delete de
        // celula_membro feito via JPA na mesma transação sem um flush explícito no meio.
        Igreja igreja = igrejaRepository.save(
                Igreja.builder().nome("Igreja Teste").emailContato("hd-" + UUID.randomUUID() + "@teste.com").build());
        Celula celula = celulaRepository.save(
                Celula.builder().igreja(igreja).nome("Com membro " + UUID.randomUUID()).build());
        Pessoa pessoa = pessoaRepository.save(
                Pessoa.builder().igreja(igreja).nome("Fulano").vinculo(Vinculo.MEMBRO).build());
        celulaMembroRepository.save(
                CelulaMembro.builder().igreja(igreja).celula(celula).pessoa(pessoa).build());
        entityManager.flush();
        entityManager.clear();

        // Igual ao CelulaService: busca fresca (não a mesma referência que salvou) e
        // deleteAll — sem clear() no meio, exatamente a sequência real do serviço.
        List<CelulaMembro> membros = celulaMembroRepository.findByCelulaIdOrderByPapelAsc(celula.getId());
        celulaMembroRepository.deleteAll(membros);
        // SEM FLUSH (teste de sanidade)
        celulaRepository.hardDeleteById(celula.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(celulaRepository.findById(celula.getId())).isEmpty();
    }

    @Test
    void findByCelulaIdOrderByPapelAscEnxergaMembroDeCelulaArquivada() {
        // O bug relatado: entrar numa célula arquivada mostra "sem membros" mesmo tendo.
        // Suspeita: @SQLRestriction("deleted_at IS NULL") da Celula vazando pro JOIN
        // implícito de findByCelulaIdOrderByPapelAsc (celula_membro -> celula).
        Igreja igreja = igrejaRepository.save(
                Igreja.builder().nome("Igreja Teste").emailContato("membro-" + UUID.randomUUID() + "@teste.com").build());
        Celula celula = celulaRepository.save(
                Celula.builder().igreja(igreja).nome("Vai arquivar " + UUID.randomUUID()).build());
        Pessoa pessoa = pessoaRepository.save(
                Pessoa.builder().igreja(igreja).nome("Fulano").vinculo(Vinculo.MEMBRO).build());
        celulaMembroRepository.save(CelulaMembro.builder().igreja(igreja).celula(celula).pessoa(pessoa).build());
        entityManager.flush();
        entityManager.clear();

        celulaRepository.delete(celula); // arquiva (soft delete)
        entityManager.flush();
        entityManager.clear();

        List<CelulaMembro> membros = celulaMembroRepository.findByCelulaIdOrderByPapelAsc(celula.getId());

        assertThat(membros).hasSize(1);
    }
}
