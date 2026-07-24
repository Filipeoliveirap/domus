package com.domus.api.modules.evento;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.evento.local.LocalEventoRepository;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre a normalização de {@code tipo} (Step 2/3 da Task 5) e a validação de local/idade —
 * o ponto sutil é que dois textos diferentes que normalizam igual ("Vigília"/"vigilia") têm
 * que reusar a MESMA grafia, senão o filtro de tipos fica com duas entradas para o mesmo tipo.
 */
class EventoTipoENormalizacaoTest {

    EventoRepository eventoRepository;
    IgrejaRepository igrejaRepository;
    CacheEvictor cacheEvictor;
    OutboxRegistrador outboxRegistrador;
    InscricaoService inscricaoService;
    FotoService fotoService;
    ElegibilidadeService elegibilidadeService;
    PessoaRepository pessoaRepository;
    LocalEventoRepository localEventoRepository;
    UsuarioRepository usuarioRepository;
    EventoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();
    UUID outraIgrejaId = UUID.randomUUID();
    UUID pessoaId = UUID.randomUUID();

    // Simula a coluna tipo persistida: cada criar() grava aqui, e tiposUsadosPorFrequencia
    // reflete a igreja de verdade sem precisar de um banco.
    List<Evento> eventosSalvos;

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        cacheEvictor = mock(CacheEvictor.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        inscricaoService = mock(InscricaoService.class);
        fotoService = mock(FotoService.class);
        elegibilidadeService = mock(ElegibilidadeService.class);
        pessoaRepository = mock(PessoaRepository.class);
        localEventoRepository = mock(LocalEventoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        service = new EventoService(eventoRepository, igrejaRepository, cacheEvictor,
                outboxRegistrador, inscricaoService, fotoService, elegibilidadeService, pessoaRepository,
                localEventoRepository, usuarioRepository);

        eventosSalvos = new ArrayList<>();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja(igrejaId)));
        when(fotoService.buscarParaVincular(any(), any())).thenReturn(null);
        when(inscricaoService.removerInscritosNaoElegiveis(any())).thenReturn(0);

        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setPessoa(pessoaDoUsuario(usuarioId));
        when(usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)).thenReturn(Optional.of(usuario));

        // save() empilha o evento na lista "persistida" e devolve o mesmo objeto com id.
        when(eventoRepository.save(any())).thenAnswer(inv -> {
            Evento e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            eventosSalvos.removeIf(existing -> existing.getId().equals(e.getId()));
            eventosSalvos.add(e);
            return e;
        });
        when(eventoRepository.findByIdAndIgrejaId(any(), any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            UUID igreja = inv.getArgument(1);
            return eventosSalvos.stream()
                    .filter(e -> e.getId().equals(id) && e.getIgreja().getId().equals(igreja))
                    .findFirst();
        });
        // Frequência real: conta quantos eventos salvos usam cada tipo, mais usado primeiro.
        when(eventoRepository.tiposUsadosPorFrequencia(any())).thenAnswer(inv -> {
            UUID igreja = inv.getArgument(0);
            java.util.Map<String, Long> contagem = new java.util.LinkedHashMap<>();
            for (Evento e : eventosSalvos) {
                if (e.getIgreja().getId().equals(igreja) && e.getTipo() != null) {
                    contagem.merge(e.getTipo(), 1L, Long::sum);
                }
            }
            return contagem.entrySet().stream()
                    .sorted((a, b) -> {
                        int cmp = b.getValue().compareTo(a.getValue());
                        return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
                    })
                    .map(java.util.Map.Entry::getKey)
                    .toList();
        });
    }

    private Igreja igreja(UUID id) {
        Igreja i = new Igreja();
        i.setId(id);
        return i;
    }

    // Usuario.pessoa é @OneToOne EAGER "nullable = false" — nunca é null no banco de verdade.
    private Pessoa pessoaDoUsuario(UUID usuarioId) {
        Pessoa p = new Pessoa();
        p.setId(UUID.randomUUID());
        p.setNome("Usuário " + usuarioId);
        return p;
    }

    // titulo, descricao, inicioEm, fimEm, localId, localTexto, tipo, responsavelPessoaId,
    // recorteEtario, idadeMin, idadeMax, restricaoEstadoCivil, restricaoSexo,
    // vagas, preco, exclusivoMembros, requerInscricao, fotoId
    private EventoRequest requestComTipo(String tipo) {
        return new EventoRequest("Evento", "desc", LocalDateTime.now().plusDays(5), null,
                null, null, tipo, null,
                null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private EventoRequest requestComLocal(UUID localId) {
        return new EventoRequest("Evento", "desc", LocalDateTime.now().plusDays(5), null,
                localId, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private EventoRequest requestComAmbos() {
        return new EventoRequest("Evento", "desc", LocalDateTime.now().plusDays(5), null,
                UUID.randomUUID(), "Chácara", null, null,
                null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private EventoRequest requestComResponsavel(UUID pessoaId) {
        return new EventoRequest("Evento", "desc", LocalDateTime.now().plusDays(5), null,
                null, null, null, pessoaId,
                null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private EventoRequest requestComIdades(Integer min, Integer max) {
        return new EventoRequest("Evento", "desc", LocalDateTime.now().plusDays(5), null,
                null, null, null, null,
                null, min, max, null, null,
                null, null, null, null, null, null);
    }

    @Test
    void tipo_e_gravado_capitalizado() {
        UUID id = service.cadastrarEvento(requestComTipo("  culto   de   jovens "), igrejaId, usuarioId).id();
        assertThat(service.buscarPorId(id, igrejaId).tipo()).isEqualTo("Culto de Jovens");
    }

    @Test
    void tipo_equivalente_reusa_a_grafia_ja_existente() {
        service.cadastrarEvento(requestComTipo("Vigília"), igrejaId, usuarioId);
        UUID id = service.cadastrarEvento(requestComTipo("vigilia"), igrejaId, usuarioId).id();
        // "vigilia" (sem acento) não pode criar um segundo tipo — o filtro ficaria com dois.
        assertThat(service.buscarPorId(id, igrejaId).tipo()).isEqualTo("Vigília");
    }

    @Test
    void tipos_distintos_NAO_sao_colapsados() {
        service.cadastrarEvento(requestComTipo("Culto"), igrejaId, usuarioId);
        UUID id = service.cadastrarEvento(requestComTipo("Cultinho"), igrejaId, usuarioId).id();
        assertThat(service.buscarPorId(id, igrejaId).tipo()).isEqualTo("Cultinho");
    }

    @Test
    void sugestoes_trazem_os_da_igreja_por_frequencia_antes_das_sementes() {
        service.cadastrarEvento(requestComTipo("Vigília"), igrejaId, usuarioId);
        service.cadastrarEvento(requestComTipo("Vigília"), igrejaId, usuarioId);
        service.cadastrarEvento(requestComTipo("Retiro"), igrejaId, usuarioId);

        assertThat(service.tiposSugeridos(igrejaId)).startsWith("Vigília", "Retiro");
    }

    @Test
    void sugestoes_nao_duplicam_semente_ja_usada_pela_igreja() {
        service.cadastrarEvento(requestComTipo("Culto"), igrejaId, usuarioId);

        List<String> sugestoes = service.tiposSugeridos(igrejaId);
        assertThat(sugestoes).filteredOn(t -> t.equals("Culto")).hasSize(1);
    }

    @Test
    void local_de_outra_igreja_e_recusado() {
        UUID localDeOutraIgreja = UUID.randomUUID();
        when(localEventoRepository.findByIdAndIgrejaId(localDeOutraIgreja, igrejaId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cadastrarEvento(
                requestComLocal(localDeOutraIgreja), igrejaId, usuarioId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void local_id_e_local_texto_juntos_sao_recusados_com_mensagem_clara() {
        assertThatThrownBy(() -> service.cadastrarEvento(requestComAmbos(), igrejaId, usuarioId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "LOCAL_AMBIGUO");
    }

    @Test
    void local_cadastrado_e_aceito_e_vinculado() {
        UUID localId = UUID.randomUUID();
        LocalEvento local = LocalEvento.builder().id(localId).igreja(igreja(igrejaId)).nome("Templo").build();
        when(localEventoRepository.findByIdAndIgrejaId(localId, igrejaId)).thenReturn(Optional.of(local));

        UUID id = service.cadastrarEvento(requestComLocal(localId), igrejaId, usuarioId).id();

        assertThat(service.buscarPorId(id, igrejaId).local().id()).isEqualTo(localId);
    }

    @Test
    void faixa_de_idade_invertida_e_recusada() {
        assertThatThrownBy(() -> service.cadastrarEvento(requestComIdades(30, 18), igrejaId, usuarioId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "FAIXA_INVALIDA");
    }

    @Test
    void responsavel_e_gravado_e_exibido_na_resposta() {
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);
        pessoa.setNome("Ana");
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(pessoa));

        UUID id = service.cadastrarEvento(requestComResponsavel(pessoaId), igrejaId, usuarioId).id();

        EventoResponse resposta = service.buscarPorId(id, igrejaId);
        assertThat(resposta.responsavel().id()).isEqualTo(pessoaId);
        assertThat(resposta.responsavel().nome()).isEqualTo("Ana");
    }

    @Test
    void auditoria_grava_criado_por_no_insert_e_atualizado_por_no_update() {
        UUID id = service.cadastrarEvento(requestComTipo("Culto"), igrejaId, usuarioId).id();
        assertThat(service.buscarPorId(id, igrejaId).criadoPor().id()).isEqualTo(usuarioId);
        assertThat(service.buscarPorId(id, igrejaId).atualizadoPor()).isNull();

        UUID outroUsuarioId = UUID.randomUUID();
        Usuario outroUsuario = new Usuario();
        outroUsuario.setId(outroUsuarioId);
        outroUsuario.setPessoa(pessoaDoUsuario(outroUsuarioId));
        when(usuarioRepository.findByIdAndIgrejaId(outroUsuarioId, igrejaId)).thenReturn(Optional.of(outroUsuario));

        service.atualizarEvento(id, requestComTipo("Culto"), igrejaId, outroUsuarioId);

        assertThat(service.buscarPorId(id, igrejaId).atualizadoPor().id()).isEqualTo(outroUsuarioId);
    }
}
