package com.domus.api.modules.evento;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B4: prova que a ordenação por situação (EM_ANDAMENTO -> hoje -> futuro -> ENCERRADO) é feita
 * no BANCO (dentro do próprio {@code ORDER BY} da paginação), não em memória depois de buscar a
 * página. Roda contra o Postgres de testes de verdade (replace = NONE), dentro da transação que
 * o {@code @DataJpaTest} sempre desfaz ao fim do teste — não deixa linha nenhuma no banco.
 *
 * <p>O teste cria 5 eventos, um de cada "posição" (incluindo dois futuros para testar a ordem
 * relativa dentro do próprio bucket) e pede a listagem em páginas de 2. Se a ordenação fosse
 * feita em memória sobre a página já carregada (o erro que este teste existe para pegar), cada
 * página manteria a ordem antiga (por {@code inicio_em} bruto) e o EM_ANDAMENTO nasceria na
 * página errada sempre que não coincidisse com a ordem cronológica pura.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoRepositoryOrdenacaoTest {

    @Autowired
    EventoRepository eventoRepository;

    @Autowired
    IgrejaRepository igrejaRepository;

    @Test
    void ordenaPorSituacaoAtravesDeVariasPaginas() {
        Igreja igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Ordenacao B4 " + UUID.randomUUID())
                .emailContato("ordenacao-b4-" + UUID.randomUUID() + "@teste.com")
                .build());
        UUID igrejaId = igreja.getId();
        LocalDateTime agora = LocalDateTime.now();

        // Cronologicamente (por inicio_em) a ordem seria: encerrado, emAndamento, hoje,
        // futuroProximo, futuroDistante. A ordem DESEJADA por situação é outra:
        // emAndamento, hoje, futuroProximo, futuroDistante, encerrado.
        Evento encerrado = salvar(igreja, "Encerrado", agora.minusDays(5), agora.minusDays(5).plusHours(2));
        Evento emAndamento = salvar(igreja, "Em andamento", agora.minusHours(1), agora.plusHours(1));
        Evento hoje = salvar(igreja, "Hoje mais tarde", agora.plusHours(3), null);
        Evento futuroProximo = salvar(igreja, "Futuro proximo", agora.plusDays(2), null);
        Evento futuroDistante = salvar(igreja, "Futuro distante", agora.plusDays(10), null);

        List<UUID> ordemEsperada = List.of(
                emAndamento.getId(), hoje.getId(), futuroProximo.getId(),
                futuroDistante.getId(), encerrado.getId());

        // Página 0 (tamanho 2): deve trazer os DOIS primeiros da ordem por situação.
        Page<Evento> pagina0 = eventoRepository.buscarPorIgreja(igrejaId, null, agora, PageRequest.of(0, 2));
        assertThat(pagina0.getTotalElements()).isEqualTo(5);
        assertThat(pagina0.getContent()).extracting(Evento::getId)
                .containsExactly(ordemEsperada.get(0), ordemEsperada.get(1));

        // Página 1: os dois seguintes — prova que o corte de página respeita a ordem GLOBAL,
        // não reordena só o que caiu na página.
        Page<Evento> pagina1 = eventoRepository.buscarPorIgreja(igrejaId, null, agora, PageRequest.of(1, 2));
        assertThat(pagina1.getContent()).extracting(Evento::getId)
                .containsExactly(ordemEsperada.get(2), ordemEsperada.get(3));

        // Página 2: o último — o ENCERRADO, que é cronologicamente o MAIS ANTIGO dos 5, mas
        // tem que aparecer por último por causa da situação.
        Page<Evento> pagina2 = eventoRepository.buscarPorIgreja(igrejaId, null, agora, PageRequest.of(2, 2));
        assertThat(pagina2.getContent()).extracting(Evento::getId)
                .containsExactly(ordemEsperada.get(4));
    }

    private Evento salvar(Igreja igreja, String titulo, LocalDateTime inicio, LocalDateTime fim) {
        return eventoRepository.save(Evento.builder()
                .igreja(igreja)
                .titulo(titulo)
                .inicioEm(inicio)
                .fimEm(fim)
                .build());
    }
}
