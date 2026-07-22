package com.domus.api.modules.evento;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.DTOs.ImpactoRestricaoResponse;
import com.domus.api.modules.evento.elegibilidade.DTOs.ElegibilidadeResponse;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.evento.local.LocalEventoRepository;
import com.domus.api.modules.foto.Foto;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.util.TextoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventoService {

    /**
     * Sementes de tipo sugeridas antes de a igreja ter usado algo — completa a lista de
     * sugestões enquanto o campo ainda não "aprendeu" nada da igreja (ver {@link #tiposSugeridos}).
     */
    private static final List<String> SEMENTES =
            List.of("Culto", "Conferência", "Retiro", "Ensaio", "Reunião");

    private final EventoRepository eventoRepository;
    private final IgrejaRepository igrejaRepository;
    private final CacheEvictor cacheEvictor;
    private final OutboxRegistrador outboxRegistrador;
    private final InscricaoService inscricaoService;
    private final FotoService fotoService;
    private final ElegibilidadeService elegibilidadeService;
    private final PessoaRepository pessoaRepository;
    private final LocalEventoRepository localEventoRepository;
    private final UsuarioRepository usuarioRepository;

    @Cacheable(
            value = "eventos",
            key = "T(com.domus.api.config.redis.CacheKeys).eventos(#igrejaId, #q, #pageable)"
    )
    @Transactional(readOnly = true)
    public PagedResponse<EventoResponse> listarEventos(UUID igrejaId, String q, Pageable pageable) {
        Page<EventoResponse> pagina = eventoRepository
                .buscarPorIgreja(igrejaId, q, java.time.LocalDateTime.now(), pageable)
                .map(EventoResponse::from);
        return PagedResponse.from(pagina);
    }

    @Transactional(readOnly = true)
    public EventoResponse buscarPorId(UUID id, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        return EventoResponse.from(evento);
    }

    @Transactional
    public EventoResponse cadastrarEvento(EventoRequest data, UUID igrejaId, UUID usuarioId) {
        log.info("Cadastrando evento. titulo={}, igreja_id={}", data.titulo(), igrejaId);
        validarDatas(data);
        validarIdades(data);
        LocalEvento local = resolverLocal(data, igrejaId);
        Pessoa responsavel = resolverResponsavel(data.responsavelPessoaId(), igrejaId);

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        Foto foto = fotoService.buscarParaVincular(data.fotoId(), igrejaId);
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        Evento evento = Evento.builder()
                .igreja(igreja)
                .titulo(TextoUtil.capitalizar(data.titulo()))
                .descricao(data.descricao())
                .inicioEm(data.inicioEm())
                .fimEm(data.fimEm())
                .local(local)
                .localTexto(local == null ? TextoUtil.capitalizar(data.localTexto()) : null)
                .tipo(resolverTipo(data.tipo(), igrejaId))
                .responsavel(responsavel)
                .recorteEtario(data.recorteEtario())
                .idadeMin(data.idadeMin())
                .idadeMax(data.idadeMax())
                .restricaoEstadoCivil(data.restricaoEstadoCivil())
                .restricaoSexo(data.restricaoSexo())
                .criadoPor(usuario)
                .foto(foto)
                .vagas(data.vagas())
                .preco(data.preco())
                .exclusivoMembros(Boolean.TRUE.equals(data.exclusivoMembros()))
                .requerInscricao(Boolean.TRUE.equals(data.requerInscricao()))
                .build();

        Evento salvo = eventoRepository.save(evento);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.EVENTO,
                TipoEventoOutbox.CRIADO,
                salvo.getId(),
                igrejaId
        );
        log.info("Evento cadastrado. id={}, igreja_id={}", salvo.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("eventos", igrejaId);
        return EventoResponse.from(salvo);
    }

    @Transactional
    public EventoResponse atualizarEvento(UUID id, EventoRequest data, UUID igrejaId, UUID usuarioId) {
        return atualizarEvento(id, data, igrejaId, usuarioId, false);
    }

    /**
     * @param cancelarNaoElegiveis Task 6: escolha EXPLÍCITA do admin ({@code PUT
     *                             /eventos/{id}?cancelarNaoElegiveis=true}) para cancelar quem
     *                             não é mais elegível sob a configuração nova. {@code false}
     *                             (o padrão) NUNCA cancela ninguém sozinho — apertar uma faixa
     *                             etária ou ligar {@code exclusivoMembros} não apaga mais em
     *                             silêncio as exceções que o admin abriu com "inscrever mesmo
     *                             assim" (ver Javadoc de
     *                             {@link InscricaoService#removerInscritosNaoElegiveis}).
     */
    @Transactional
    public EventoResponse atualizarEvento(UUID id, EventoRequest data, UUID igrejaId, UUID usuarioId,
                                          boolean cancelarNaoElegiveis) {
        log.info("Atualizando evento. id={}, igreja_id={}", id, igrejaId);
        validarDatas(data);
        validarIdades(data);
        LocalEvento local = resolverLocal(data, igrejaId);
        Pessoa responsavel = resolverResponsavel(data.responsavelPessoaId(), igrejaId);

        Evento evento = eventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        // Editar um evento que já começou reescreveria o passado (data, local, vagas) debaixo
        // de gente que já confirmou presença ou já foi. AGENDADO é a única situação em que
        // editar ainda faz sentido.
        SituacaoEvento situacaoAtual = evento.getSituacao();
        if (situacaoAtual == SituacaoEvento.EM_ANDAMENTO) {
            throw new BusinessException("EVENTO_EM_ANDAMENTO",
                    "Não é possível editar um evento em andamento.");
        }
        if (situacaoAtual == SituacaoEvento.ENCERRADO) {
            throw new BusinessException("EVENTO_ENCERRADO",
                    "Não é possível editar um evento encerrado.");
        }

        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        evento.setTitulo(TextoUtil.capitalizar(data.titulo()));
        evento.setDescricao(data.descricao());
        evento.setInicioEm(data.inicioEm());
        evento.setFimEm(data.fimEm());
        evento.setLocal(local);
        evento.setLocalTexto(local == null ? TextoUtil.capitalizar(data.localTexto()) : null);
        evento.setTipo(resolverTipo(data.tipo(), igrejaId));
        evento.setResponsavel(responsavel);
        evento.setRecorteEtario(data.recorteEtario());
        evento.setIdadeMin(data.idadeMin());
        evento.setIdadeMax(data.idadeMax());
        evento.setRestricaoEstadoCivil(data.restricaoEstadoCivil());
        evento.setRestricaoSexo(data.restricaoSexo());
        evento.setAtualizadoPor(usuario);

        // A9: vagas contam PESSOAS (inscritos confirmados + acompanhantes) — reduzir abaixo
        // de quem já está confirmado deixaria o evento com mais gente que vaga declarada, e
        // "vagas restantes" (evento.getVagas() - total) ficaria negativo. null (sem limite)
        // sempre é permitido, não há o que estourar.
        if (data.vagas() != null) {
            long pessoasConfirmadas = inscricaoService.contarPessoasConfirmadas(evento.getId());
            if (data.vagas() < pessoasConfirmadas) {
                throw new BusinessException("VAGAS_MENOR_QUE_INSCRITOS",
                        "Não é possível reduzir as vagas para " + data.vagas() + ": "
                        + pessoasConfirmadas + " pessoas já estão confirmadas neste evento. "
                        + "Cancele inscrições antes de reduzir o limite.");
            }
        }
        evento.setVagas(data.vagas());
        evento.setPreco(data.preco());
        boolean exclusivoMembros = Boolean.TRUE.equals(data.exclusivoMembros());
        evento.setExclusivoMembros(exclusivoMembros);
        evento.setRequerInscricao(Boolean.TRUE.equals(data.requerInscricao()));

        // Foto: resolve a NOVA antes de tocar no evento (valida que é da mesma igreja) e só
        // grava a ANTIGA como candidata a remoção — nunca apaga antes de o evento apontar
        // para a nova (mesma ordem usada em PessoaService).
        Foto fotoAntiga = evento.getFoto();
        Foto fotoNova = fotoService.buscarParaVincular(data.fotoId(), igrejaId);
        evento.setFoto(fotoNova);

        Evento salvo = eventoRepository.save(evento);

        // Remove a foto antiga só DEPOIS que o evento já aponta para a nova — antes, o
        // ON DELETE RESTRICT recusaria (a FK ainda apontaria para ela).
        boolean fotoMudou = !java.util.Objects.equals(
                fotoAntiga == null ? null : fotoAntiga.getId(),
                fotoNova == null ? null : fotoNova.getId());
        if (fotoMudou && fotoAntiga != null) {
            fotoService.remover(fotoAntiga.getId());
        }

        // Task 6: NUNCA cancela sozinho mais. Só quando o admin escolhe explicitamente
        // cancelarNaoElegiveis=true (depois de ver a prévia de POST .../impacto-restricao) —
        // ver Javadoc de removerInscritosNaoElegiveis para o porquê da mudança.
        int inscricoesRemovidas = cancelarNaoElegiveis
                ? inscricaoService.removerInscritosNaoElegiveis(salvo.getId())
                : 0;

        outboxRegistrador.registrar(
                TipoEntidadeOutbox.EVENTO,
                TipoEventoOutbox.ATUALIZADO,
                salvo.getId(),
                igrejaId
        );
        log.info("Evento atualizado. id={}, igreja_id={}", id, igrejaId);
        cacheEvictor.evictPorIgreja("eventos", igrejaId);
        return EventoResponse.from(salvo, inscricoesRemovidas);
    }

    @Transactional
    public void arquivarEvento(UUID id, UUID igrejaId) {
        log.info("Arquivando evento. id={}, igreja_id={}", id, igrejaId);
        Evento evento = eventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        // Só bloqueia em andamento: arquivar um evento ENCERRADO é faxina normal (o piloto vai
        // acumular evento passado toda semana), e travar isso empurraria o usuário pra pedir
        // exceção sem necessidade. Em andamento é diferente — arquivar um evento que está
        // rolando AGORA tira ele da lista de quem ainda vai chegar.
        if (evento.getSituacao() == SituacaoEvento.EM_ANDAMENTO) {
            throw new BusinessException("EVENTO_EM_ANDAMENTO",
                    "Não é possível arquivar um evento em andamento.");
        }

        eventoRepository.delete(evento);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.EVENTO,
                TipoEventoOutbox.REMOVIDO,
                evento.getId(),
                igrejaId
        );
        log.info("Evento arquivado. id={}, igreja_id={}", id, igrejaId);
        cacheEvictor.evictPorIgreja("eventos", igrejaId);
    }

    /**
     * Prévia de elegibilidade PARA A PRÓPRIA PESSOA logada — é o que alimenta o
     * {@code GET /eventos/{id}/elegibilidade}, conveniência de UX para a tela decidir o que
     * mostrar ANTES do POST de inscrição. NUNCA é defesa: o {@code InscricaoService} roda a
     * MESMA {@link ElegibilidadeService#avaliar} de novo dentro da transação de inscrever,
     * então quem chamar o POST direto (Insomnia, curl) esbarra na regra igual.
     */
    @Transactional(readOnly = true)
    public ElegibilidadeResponse elegibilidade(UUID eventoId, UUID pessoaId, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        Pessoa pessoa = pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));
        return ElegibilidadeResponse.from(elegibilidadeService.avaliar(evento, pessoa));
    }

    /**
     * Prévia PURA (nada é gravado) de quem, dentre os inscritos confirmados de HOJE, ficaria de
     * fora se {@code data} fosse salvo — alimenta {@code POST /eventos/{id}/impacto-restricao}
     * (Task 6). É o que substitui o cancelamento automático que existia antes: em vez de
     * cancelar em silêncio ao apertar/ligar uma restrição, o admin vê a lista e decide, via
     * {@code PUT /eventos/{id}?cancelarNaoElegiveis=true}, se quer mesmo cancelar.
     *
     * <p><b>Privacidade:</b> a resposta traz nome e motivo (ex.: "34 anos") de terceiros — só
     * quem {@link Permissoes#podeGerenciarEventos(String)} pode chamar (mesmo vazamento da
     * revisão da Task 4, fechado aqui na origem).
     */
    @Transactional(readOnly = true)
    public ImpactoRestricaoResponse calcularImpacto(UUID eventoId, EventoRequest data, UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarEventos(role)) {
            throw new AccessDeniedException(
                    "Você não tem permissão para ver o impacto desta restrição.");
        }

        eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarIdades(data);

        // Evento "de mentira", nunca persistido: só carrega as regras hipotéticas que
        // ElegibilidadeService lê (idade, estado civil, sexo, exclusivoMembros — ver Javadoc
        // de ElegibilidadeService sobre RegraVagas ficar de fora dessa lista).
        Evento regrasHipoteticas = Evento.builder()
                .idadeMin(data.idadeMin())
                .idadeMax(data.idadeMax())
                .restricaoEstadoCivil(data.restricaoEstadoCivil())
                .restricaoSexo(data.restricaoSexo())
                .exclusivoMembros(Boolean.TRUE.equals(data.exclusivoMembros()))
                .build();

        return new ImpactoRestricaoResponse(
                inscricaoService.calcularImpacto(eventoId, regrasHipoteticas));
    }

    private void validarDatas(EventoRequest data) {
        if (data.fimEm() != null && data.fimEm().isBefore(data.inicioEm())) {
            log.warn("Data de término anterior ao início. inicio={}, fim={}", data.inicioEm(), data.fimEm());
            throw new BusinessException("DATA_INVALIDA",
                    "A data de término não pode ser anterior à data de início.");
        }
    }

    private void validarIdades(EventoRequest data) {
        if (data.idadeMin() != null && data.idadeMax() != null && data.idadeMin() > data.idadeMax()) {
            throw new BusinessException("FAIXA_INVALIDA",
                    "A idade mínima não pode ser maior que a máxima.");
        }
    }

    /**
     * Resolve o local do evento a partir de {@code localId}/{@code localTexto}. O CHECK do
     * banco (mutuamente exclusivos) é a rede de segurança — aqui é onde o usuário recebe uma
     * mensagem decente em vez de um 500 genérico vindo da constraint.
     */
    private LocalEvento resolverLocal(EventoRequest data, UUID igrejaId) {
        boolean temTexto = data.localTexto() != null && !data.localTexto().isBlank();
        if (data.localId() != null && temTexto) {
            throw new BusinessException("LOCAL_AMBIGUO",
                    "Escolha um local cadastrado ou digite um local, não os dois.");
        }
        if (data.localId() == null) {
            return null;
        }
        // Isolamento multi-tenant: localId de OUTRA igreja é tratado como inexistente — nunca
        // vaza que existe fora da própria igreja.
        return localEventoRepository.findByIdAndIgrejaId(data.localId(), igrejaId)
                .orElseThrow(() -> new BusinessException("LOCAL_NAO_ENCONTRADO",
                        "Local não encontrado."));
    }

    private Pessoa resolverResponsavel(UUID responsavelPessoaId, UUID igrejaId) {
        if (responsavelPessoaId == null) return null;
        return pessoaRepository.findByIdAndIgrejaId(responsavelPessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa responsável não encontrada."));
    }

    /**
     * Grava o tipo capitalizado, mas ANTES procura um tipo já usado pela igreja cujo
     * {@code normalizarParaComparacao} bata com o novo — se houver, reusa a grafia existente.
     * Sem isso, "Vigília" e "vigilia" virariam dois tipos diferentes no filtro (o mesmo evento,
     * duas grafias). Tipos genuinamente diferentes ("Culto" vs. "Cultinho") não normalizam
     * igual, então continuam distintos.
     */
    private String resolverTipo(String tipoInformado, UUID igrejaId) {
        String capitalizado = TextoUtil.capitalizar(tipoInformado);
        if (capitalizado == null) return null;

        String normalizado = TextoUtil.normalizarParaComparacao(capitalizado);
        return eventoRepository.tiposUsadosPorFrequencia(igrejaId).stream()
                .filter(existente -> TextoUtil.normalizarParaComparacao(existente).equals(normalizado))
                .findFirst()
                .orElse(capitalizado);
    }

    /**
     * Sugestões de tipo: primeiro os que a IGREJA já usou (por frequência — o que ela mais
     * digita sobe), depois as sementes que ainda não foram usadas. É essa ordem que faz o
     * campo parecer que aprende: o que a igreja usou sobe e passa na frente do que o sistema
     * chutou e ninguém usou.
     */
    @Transactional(readOnly = true)
    public List<String> tiposSugeridos(UUID igrejaId) {
        List<String> usados = eventoRepository.tiposUsadosPorFrequencia(igrejaId);
        Set<String> jaPresentes = new LinkedHashSet<>();
        for (String tipo : usados) {
            jaPresentes.add(TextoUtil.normalizarParaComparacao(tipo));
        }

        List<String> sugestoes = new ArrayList<>(usados);
        for (String semente : SEMENTES) {
            if (jaPresentes.add(TextoUtil.normalizarParaComparacao(semente))) {
                sugestoes.add(semente);
            }
        }
        return sugestoes;
    }
}