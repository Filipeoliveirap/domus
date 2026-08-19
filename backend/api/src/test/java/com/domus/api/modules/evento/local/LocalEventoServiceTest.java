package com.domus.api.modules.evento.local;

import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.EventoService;
import com.domus.api.modules.evento.local.DTOs.LocalEventoRequest;
import com.domus.api.modules.evento.local.DTOs.LocalEventoResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.dominio.Endereco;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Postgres real (não mock): checagem de duplicata e endereço herdado dependem de dado já persistido; @Transactional dá rollback ao final. */
@SpringBootTest
@Transactional
class LocalEventoServiceTest {

    @Autowired LocalEventoService service;
    @Autowired LocalEventoRepository repository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired EventoService eventoService;
    @Autowired EntityManager entityManager;

    UUID igrejaId;
    UUID outraIgrejaId;
    Igreja igrejaDoTeste;

    @BeforeEach
    void setup() {
        igrejaDoTeste = igrejaRepository.save(novaIgreja("Igreja do Teste de Local"));
        igrejaId = igrejaDoTeste.getId();
        outraIgrejaId = igrejaRepository.save(novaIgreja("Outra Igreja")).getId();
    }

    private Igreja novaIgreja(String nome) {
        Igreja igreja = new Igreja();
        igreja.setNome(nome);
        igreja.setEmailContato(nome.toLowerCase().replace(" ", ".") + "@teste.com");
        igreja.setEndereco(Endereco.builder()
                .cep("01000-000").logradouro("Rua da Igreja").numero("100")
                .bairro("Centro").cidade("São Paulo").uf("SP")
                .build());
        return igreja;
    }

    @Test
    void nao_permite_dois_locais_com_o_mesmo_nome_ignorando_acento_e_caixa() {
        service.criar(new LocalEventoRequest("Salão Social", 80, null, null), igrejaId);
        assertThatThrownBy(() ->
                service.criar(new LocalEventoRequest("salao social", 50, null, null), igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Já existe um local");
    }

    @Test
    void endereco_nulo_herda_o_da_igreja_e_sinaliza_a_heranca() {
        UUID id = service.criar(new LocalEventoRequest("Santuário", 300, null, null), igrejaId).id();
        LocalEventoResponse r = service.listar(igrejaId).stream()
                .filter(l -> l.id().equals(id)).findFirst().orElseThrow();

        assertThat(r.enderecoHerdado()).isTrue();
        assertThat(r.endereco()).contains("Rua da Igreja").contains("São Paulo");
    }

    @Test
    void endereco_proprio_nao_herda() {
        UUID id = service.criar(
                new LocalEventoRequest("Chácara Betel", 120, "12345-000, Estrada X, 10", "Zona Rural"),
                igrejaId).id();
        LocalEventoResponse r = service.listar(igrejaId).stream()
                .filter(l -> l.id().equals(id)).findFirst().orElseThrow();

        assertThat(r.enderecoHerdado()).isFalse();
        assertThat(r.endereco()).contains("Estrada X");
    }

    @Test
    void local_de_outra_igreja_nao_e_encontrado() {
        UUID id = service.criar(new LocalEventoRequest("Salão", 80, null, null), igrejaId).id();
        assertThat(repository.findByIdAndIgrejaId(id, outraIgrejaId)).isEmpty();
    }

    /**
     * LocalEvento usa soft delete, então o ON DELETE SET NULL da FK evento.local_id nunca dispara — arquivar precisa desvincular manualmente, senão o proxy LAZY estoura EntityNotFoundException ao montar EventoResponse e derruba a listagem inteira.
     * flush()+clear() e chama EventoService.listarEventos (o caminho real) em vez de reler a instância já gerenciada, senão o proxy LAZY não seria resolvido de novo e o teste passaria mesmo com o bug.
     */
    @Test
    void arquivar_local_no_evento_deixaOEventoSemLocalENaoQuebraAListagem() {
        UUID localId = service.criar(new LocalEventoRequest("Salão Social", 80, null, null), igrejaId).id();
        LocalEvento local = repository.findByIdAndIgrejaId(localId, igrejaId).orElseThrow();

        Evento evento = Evento.builder()
                .igreja(igrejaRepository.findById(igrejaId).orElseThrow())
                .titulo("Café dos Homens")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .local(local)
                .exclusivoMembros(false)
                .requerInscricao(false)
                .build();
        evento = eventoRepository.save(evento);
        UUID eventoId = evento.getId();

        service.arquivar(localId, igrejaId);

        // Força releitura do banco: sem isto, a instância de `evento` já gerenciada na
        // sessão continuaria com o `local` antigo em memória e o teste não provaria nada.
        entityManager.flush();
        entityManager.clear();

        // Exercita o caminho REAL que quebrava: EventoService.listarEventos monta
        // EventoResponse, que chama getLocalExibicao() e resolveria o proxy LAZY de local.
        PagedResponse<EventoResponse> pagina =
                eventoService.listarEventos(igrejaId, null, null, null, "ADMIN_IGREJA", PageRequest.of(0, 20));
        EventoResponse resposta = pagina.getContent().stream()
                .filter(r -> r.id().equals(eventoId)).findFirst().orElseThrow();

        // Local arquivado não é endereço válido pra continuar aparecendo — o evento fica
        // sem local (nem o nome sobra como texto), não vira ad-hoc.
        assertThat(resposta.local()).isNull();

        Evento recarregado = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId).orElseThrow();
        assertThat(recarregado.getLocal()).isNull();
        assertThat(recarregado.getLocalTexto()).isNull();
        assertThat(recarregado.getLocalExibicao()).isNull();

        // O local em si foi arquivado (soft delete) — não aparece mais para a igreja.
        assertThat(repository.findByIdAndIgrejaId(localId, igrejaId)).isEmpty();
    }

    /**
     * Eventos arquivados também precisam ser desvinculados ao arquivar o local: @SQLRestriction("deleted_at IS NULL") faz findByLocalIdAndIgrejaId nunca enxergar o evento arquivado, por isso a correção usa SQL nativo (EventoRepository.desvincularLocal).
     * Verificação via SQL direto no EntityManager — repository/service filtram deleted_at IS NULL e não enxergariam a linha arquivada.
     */
    @Test
    void arquivar_local_desvincula_ate_evento_ja_arquivado() {
        UUID localId = service.criar(new LocalEventoRequest("Salão Social", 80, null, null), igrejaId).id();
        LocalEvento local = repository.findByIdAndIgrejaId(localId, igrejaId).orElseThrow();

        Evento evento = Evento.builder()
                .igreja(igrejaRepository.findById(igrejaId).orElseThrow())
                .titulo("Café dos Homens")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .local(local)
                .exclusivoMembros(false)
                .requerInscricao(false)
                .build();
        evento = eventoRepository.save(evento);
        UUID eventoId = evento.getId();

        // Arquiva o EVENTO primeiro — a partir daqui, nenhuma busca via JPQL/derived query
        // o enxerga mais (@SQLRestriction).
        eventoRepository.delete(evento);
        entityManager.flush();
        entityManager.clear();

        // Depois arquiva o LOCAL — é aqui que o desvínculo tem que alcançar o evento
        // arquivado também.
        service.arquivar(localId, igrejaId);
        entityManager.flush();
        entityManager.clear();

        Tuple linha = (Tuple) entityManager.createNativeQuery(
                        "SELECT local_id, local_texto FROM evento WHERE id = :id", Tuple.class)
                .setParameter("id", eventoId)
                .getSingleResult();

        assertThat(linha.get("local_id")).isNull();
        assertThat(linha.get("local_texto")).isNull();
    }

    @Test
    void arquivar_listarArquivados_restaurarEExcluirDefinitivo_semNuncaBloquear() {
        UUID localId = service.criar(new LocalEventoRequest("Salão Nobre", 100, null, null), igrejaId).id();

        service.arquivar(localId, igrejaId);
        entityManager.flush();
        entityManager.clear();

        var arquivados = service.listarArquivados(igrejaId);
        assertThat(arquivados).hasSize(1);
        assertThat(arquivados.get(0).id()).isEqualTo(localId);

        service.restaurar(localId, igrejaId);
        entityManager.flush();
        entityManager.clear();
        assertThat(service.listarArquivados(igrejaId)).isEmpty();
        assertThat(repository.findByIdAndIgrejaId(localId, igrejaId)).isPresent();

        service.arquivar(localId, igrejaId);
        entityManager.flush();
        entityManager.clear();

        service.excluirDefinitivo(localId, igrejaId);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByIdAndIgrejaIdIncluindoArquivados(localId, igrejaId)).isEmpty();
    }

    @Test
    void excluirDefinitivo_falhaQuandoLocalNaoEncontrado() {
        assertThatThrownBy(() -> service.excluirDefinitivo(UUID.randomUUID(), igrejaId))
                .isInstanceOf(com.domus.api.shared.exception.ResourceNotFoundException.class);
    }
}
