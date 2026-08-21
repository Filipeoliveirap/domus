package com.domus.api.modules.evento;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.evento.DTOs.EventoArquivadoResponse;
import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.DTOs.ImpactoRestricaoResponse;
import com.domus.api.modules.evento.elegibilidade.DTOs.ElegibilidadeResponse;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.evento.local.LocalEventoRepository;
import com.domus.api.modules.foto.Foto;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
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

    /** Tipos sugeridos por padrão quando a igreja ainda não usou nenhum. */
    private static final List<String> SEMENTES =
            List.of("Culto", "Conferência", "Retiro", "Ensaio", "Reunião");

    private final EventoRepository eventoRepository;
    private final IgrejaRepository igrejaRepository;
    private final CacheEvictor cacheEvictor;
    private final OutboxRegistrador outboxRegistrador;
    private final InscricaoService inscricaoService;
    private final InscricaoRepository inscricaoRepository;
    private final FotoService fotoService;
    private final ElegibilidadeService elegibilidadeService;
    private final PessoaRepository pessoaRepository;
    private final LocalEventoRepository localEventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final FamiliaIgrejaService familiaIgrejaService;
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
    private final com.domus.api.modules.evento.serie.EventoSerieRepository eventoSerieRepository;

    @Cacheable(
            value = "eventos",
            key = "T(com.domus.api.config.redis.CacheKeys).eventos(#igrejaId, #q, #tipo, #recorteEtario, #role, #pageable)"
    )
    @Transactional(readOnly = true)
    public PagedResponse<EventoResponse> listarEventos(
            UUID igrejaId, String q, String tipo, String recorteEtario, String role, Pageable pageable) {
        Set<UUID> idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Page<EventoResponse> pagina = eventoRepository
                .buscarPorFamilia(igrejaId, idsFamilia.toArray(new UUID[0]), q, tipo, recorteEtario,
                        java.time.LocalDateTime.now(), pageable)
                .map(evento -> EventoResponse.from(evento, igrejaId,
                        Permissoes.podeGerenciarEventos(role) && evento.getIgreja().getId().equals(igrejaId)));
        return PagedResponse.from(pagina);
    }

    @Transactional(readOnly = true)
    public EventoResponse buscarPorId(UUID id, UUID igrejaId, String role) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        // Arquivado não aparece em buscarVisivelParaFamilia (@SQLRestriction) — precisa
        // enxergar arquivado também, pra abrir o detalhe a partir da tela de Arquivados
        // (igual célula/ministério). Fallback restrito à própria igreja, não à família
        // inteira: a tela de Arquivados também só lista da própria igreja.
        Evento evento = eventoRepository.buscarVisivelParaFamilia(id, igrejaId, idsFamilia)
                .or(() -> eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(id, igrejaId))
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        boolean podeGerenciar = Permissoes.podeGerenciarEventos(role)
                && evento.getIgreja().getId().equals(igrejaId);
        return EventoResponse.from(evento, igrejaId, podeGerenciar);
    }

    @Transactional
    public EventoResponse cadastrarEvento(EventoRequest data, UUID igrejaId, UUID usuarioId) {
        log.info("Cadastrando evento. titulo={}, igreja_id={}", data.titulo(), igrejaId);
        validarDatas(data);
        validarIdades(data);
        validarControlaPresenca(data);
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
                .controlaPresenca(Boolean.TRUE.equals(data.controlaPresenca()))
                .restritoPropriaIgreja(Boolean.TRUE.equals(data.restritoPropriaIgreja()))
                .build();

        Evento salvo = eventoRepository.save(evento);

        if (data.recorrencia() != null) {
            var serie = criarSerie(data.recorrencia(), igreja, usuario);
            salvo.setSerie(serie);
            salvo = eventoRepository.save(salvo);
        }

        outboxRegistrador.registrar(
                TipoEntidadeOutbox.EVENTO,
                TipoEventoOutbox.CRIADO,
                salvo.getId(),
                igrejaId
        );
        notificarNovoResponsavel(salvo, igrejaId, usuarioId);
        notificarNovoEvento(salvo, igrejaId, usuarioId);
        log.info("Evento cadastrado. id={}, igreja_id={}", salvo.getId(), igrejaId);
        evictarCacheDeEventosDaFamilia(igrejaId);
        return EventoResponse.from(salvo, igrejaId, true);
    }

    @Transactional
    public EventoResponse atualizarEvento(UUID id, EventoRequest data, UUID igrejaId, UUID usuarioId,
                                          com.domus.api.modules.evento.serie.EscopoEdicaoEvento escopo) {
        return atualizarEvento(id, data, igrejaId, usuarioId, false, escopo);
    }

    /** @param cancelarNaoElegiveis padrão {@code false} nunca cancela ninguém sozinho. */
    @Transactional
    public EventoResponse atualizarEvento(UUID id, EventoRequest data, UUID igrejaId, UUID usuarioId,
                                          boolean cancelarNaoElegiveis,
                                          com.domus.api.modules.evento.serie.EscopoEdicaoEvento escopo) {
        log.info("Atualizando evento. id={}, igreja_id={}", id, igrejaId);
        validarDatas(data);
        validarIdades(data);
        validarControlaPresenca(data);
        LocalEvento local = resolverLocal(data, igrejaId);
        Pessoa responsavel = resolverResponsavel(data.responsavelPessoaId(), igrejaId);

        Evento evento = eventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        // Editar evento que não está AGENDADO reescreveria o passado com gente já confirmada.
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

        java.time.LocalDateTime inicioAntigo = evento.getInicioEm();
        UUID localIdAntigo = evento.getLocal() != null ? evento.getLocal().getId() : null;
        String localTextoAntigo = evento.getLocalTexto();
        UUID responsavelIdAntigo = evento.getResponsavel() != null ? evento.getResponsavel().getId() : null;

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

        // Vagas contam inscritos confirmados + acompanhantes.
        // Reduzir abaixo do total de confirmados é proibido; null = sem limite.
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
        evento.setControlaPresenca(Boolean.TRUE.equals(data.controlaPresenca()));
        evento.setRestritoPropriaIgreja(Boolean.TRUE.equals(data.restritoPropriaIgreja()));

        // Resolve a nova foto antes de trocar; só remove a antiga depois.
        Foto fotoAntiga = evento.getFoto();
        Foto fotoNova = fotoService.buscarParaVincular(data.fotoId(), igrejaId);
        evento.setFoto(fotoNova);

        Evento salvo = eventoRepository.save(evento);

        if (evento.getSerie() != null) {
            switch (escopo) {
                case ESTA -> {
                    evento.setDivergeDaSerie(true);
                    salvo = eventoRepository.save(evento);
                }
                case SERIE -> salvo = propagarParaSerie(salvo, igrejaId);
                case ESTA_E_SEGUINTES -> salvo = dividirSerie(salvo, igrejaId);
            }
        }

        boolean dataOuLocalMudou = !java.util.Objects.equals(inicioAntigo, salvo.getInicioEm())
                || !java.util.Objects.equals(localIdAntigo, salvo.getLocal() != null ? salvo.getLocal().getId() : null)
                || !java.util.Objects.equals(localTextoAntigo, salvo.getLocalTexto());
        if (dataOuLocalMudou) {
            notificarInscritos(salvo, igrejaId, usuarioId,
                    "O evento \"" + salvo.getTitulo() + "\" mudou de data ou local.",
                    "/eventos?detalhe=" + salvo.getId());
        }

        UUID responsavelIdNovo = responsavel != null ? responsavel.getId() : null;
        if (!java.util.Objects.equals(responsavelIdAntigo, responsavelIdNovo)) {
            notificarNovoResponsavel(salvo, igrejaId, usuarioId);
        }

        // Remove a foto antiga só depois que o evento já aponta para a nova.
        boolean fotoMudou = !java.util.Objects.equals(
                fotoAntiga == null ? null : fotoAntiga.getId(),
                fotoNova == null ? null : fotoNova.getId());
        if (fotoMudou && fotoAntiga != null) {
            fotoService.remover(fotoAntiga.getId());
        }

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
        evictarCacheDeEventosDaFamilia(igrejaId);
        return EventoResponse.from(salvo, inscricoesRemovidas, igrejaId, true);
    }

    @Transactional
    public void arquivarEvento(UUID id, UUID igrejaId, UUID usuarioId,
                               com.domus.api.modules.evento.serie.EscopoEdicaoEvento escopo) {
        log.info("Arquivando evento. id={}, igreja_id={}", id, igrejaId);
        Evento evento = eventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        // Bloqueia só em andamento: arquivar evento encerrado é faxina, mas arquivar
        // evento rolando tira ele de quem ainda vai chegar.
        if (evento.getSituacao() == SituacaoEvento.EM_ANDAMENTO) {
            throw new BusinessException("EVENTO_EM_ANDAMENTO",
                    "Não é possível arquivar um evento em andamento.");
        }

        List<Evento> paraArquivar = List.of(evento);
        if (evento.getSerie() != null
                && escopo != com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA) {
            paraArquivar = eventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(
                    evento.getSerie().getId(), evento.getInicioEm());
            evento.getSerie().setAtiva(false);
            eventoSerieRepository.save(evento.getSerie());
        }

        for (Evento ocorrencia : paraArquivar) {
            if (ocorrencia.getSituacao() == SituacaoEvento.EM_ANDAMENTO) continue;
            notificarInscritos(ocorrencia, igrejaId, usuarioId,
                    "O evento \"" + ocorrencia.getTitulo() + "\" foi cancelado.", "/eventos");
            eventoRepository.delete(ocorrencia);
            outboxRegistrador.registrar(TipoEntidadeOutbox.EVENTO, TipoEventoOutbox.REMOVIDO,
                    ocorrencia.getId(), igrejaId);
        }
        log.info("Evento(s) arquivado(s). id={}, igreja_id={}, total={}", id, igrejaId, paraArquivar.size());
        evictarCacheDeEventosDaFamilia(igrejaId);
    }

    /** {@code usuarioIdAtor} nunca recebe a própria notificação — quem fez a mudança já sabe dela. */
    private void notificarInscritos(Evento evento, UUID igrejaId, UUID usuarioIdAtor, String texto, String link) {
        List<UUID> pessoaIds = inscricaoRepository.findPessoaIdsByEventoIdAndStatus(
                evento.getId(), com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA);
        for (UUID pessoaId : pessoaIds) {
            usuarioRepository.findByPessoaId(pessoaId)
                    .filter(usuario -> !usuario.getId().equals(usuarioIdAtor))
                    .ifPresent(usuario ->
                            notificacaoService.criar(
                                    com.domus.api.modules.notificacao.TipoNotificacao.EVENTO_ALTERADO,
                                    igrejaId, usuario.getId(), texto, link));
        }
    }

    /** Copia os campos editáveis pra toda ocorrência AGENDADO da mesma série — limpa
     *  divergeDaSerie de todas (edição de série sempre vence uma divergência antiga). */
    private Evento propagarParaSerie(Evento editado, UUID igrejaId) {
        List<Evento> futuras = eventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(
                editado.getSerie().getId(), editado.getInicioEm());
        for (Evento ocorrencia : futuras) {
            if (ocorrencia.getId().equals(editado.getId())) continue;
            if (ocorrencia.getSituacao() != SituacaoEvento.AGENDADO) continue;
            ocorrencia.setTitulo(editado.getTitulo());
            ocorrencia.setDescricao(editado.getDescricao());
            ocorrencia.setLocal(editado.getLocal());
            ocorrencia.setLocalTexto(editado.getLocalTexto());
            ocorrencia.setTipo(editado.getTipo());
            ocorrencia.setResponsavel(editado.getResponsavel());
            ocorrencia.setRecorteEtario(editado.getRecorteEtario());
            ocorrencia.setIdadeMin(editado.getIdadeMin());
            ocorrencia.setIdadeMax(editado.getIdadeMax());
            ocorrencia.setRestricaoEstadoCivil(editado.getRestricaoEstadoCivil());
            ocorrencia.setRestricaoSexo(editado.getRestricaoSexo());
            ocorrencia.setVagas(editado.getVagas());
            ocorrencia.setPreco(editado.getPreco());
            ocorrencia.setExclusivoMembros(editado.isExclusivoMembros());
            ocorrencia.setRequerInscricao(editado.isRequerInscricao());
            ocorrencia.setControlaPresenca(editado.isControlaPresenca());
            ocorrencia.setRestritoPropriaIgreja(editado.isRestritoPropriaIgreja());
            ocorrencia.setDivergeDaSerie(false);
            eventoRepository.save(ocorrencia);
        }
        editado.setDivergeDaSerie(false);
        return eventoRepository.save(editado);
    }

    /** "Esta e as seguintes": encerra a série atual na véspera desta ocorrência, cria uma
     *  série nova (clone da regra) e reponta essa ocorrência + as futuras agendadas pra ela. */
    private Evento dividirSerie(Evento editado, UUID igrejaId) {
        var antiga = editado.getSerie();
        antiga.setDataFim(editado.getInicioEm().toLocalDate().minusDays(1));
        antiga.setNumeroOcorrencias(null); // CHECK de exclusão mútua no banco
        eventoSerieRepository.save(antiga);

        var nova = com.domus.api.modules.evento.serie.EventoSerie.builder()
                .igreja(antiga.getIgreja())
                .frequencia(antiga.getFrequencia())
                .intervalo(antiga.getIntervalo())
                .diasSemana(antiga.getDiasSemana())
                .tipoRecorrenciaMensal(antiga.getTipoRecorrenciaMensal())
                .criadoPor(antiga.getCriadoPor())
                .build();
        nova = eventoSerieRepository.save(nova);

        List<Evento> futuras = eventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(
                antiga.getId(), editado.getInicioEm());
        for (Evento ocorrencia : futuras) {
            if (ocorrencia.getSituacao() != SituacaoEvento.AGENDADO
                    && !ocorrencia.getId().equals(editado.getId())) continue;
            ocorrencia.setSerie(nova);
            ocorrencia.setDivergeDaSerie(false);
            if (!ocorrencia.getId().equals(editado.getId())) {
                ocorrencia.setTitulo(editado.getTitulo());
                ocorrencia.setDescricao(editado.getDescricao());
                ocorrencia.setLocal(editado.getLocal());
                ocorrencia.setLocalTexto(editado.getLocalTexto());
            }
            eventoRepository.save(ocorrencia);
        }
        editado.setSerie(nova);
        editado.setDivergeDaSerie(false);
        return eventoRepository.save(editado);
    }

    /** {@code usuarioIdAtor} nunca recebe a própria notificação — quem se colocou como responsável já sabe. */
    private void notificarNovoResponsavel(Evento evento, UUID igrejaId, UUID usuarioIdAtor) {
        if (evento.getResponsavel() == null) return;
        usuarioRepository.findByPessoaId(evento.getResponsavel().getId())
                .filter(usuario -> !usuario.getId().equals(usuarioIdAtor))
                .ifPresent(usuario ->
                        notificacaoService.criar(
                                com.domus.api.modules.notificacao.TipoNotificacao.RESPONSAVEL_EVENTO,
                                igrejaId, usuario.getId(),
                                "Você foi definido como responsável pelo evento \"" + evento.getTitulo() + "\".",
                                "/eventos?detalhe=" + evento.getId()));
    }

    /** Convite pra todo mundo da igreja dar uma olhada no evento novo — exceto quem cadastrou. */
    private void notificarNovoEvento(Evento evento, UUID igrejaId, UUID usuarioIdAtor) {
        List<UUID> usuarioIds = usuarioRepository.findIdsAtivosPorIgreja(igrejaId);
        String texto = evento.getSerie() != null
                ? textoLembreteDeSerie(evento)
                : "Novo evento: \"" + evento.getTitulo() + "\". Dá uma olhada!";
        for (UUID usuarioId : usuarioIds) {
            if (usuarioId.equals(usuarioIdAtor)) continue;
            notificacaoService.criar(
                    com.domus.api.modules.notificacao.TipoNotificacao.NOVO_EVENTO,
                    igrejaId, usuarioId, texto, "/eventos?detalhe=" + evento.getId());
        }
    }

    private static final java.time.format.DateTimeFormatter FORMATADOR_LEMBRETE =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", new java.util.Locale("pt", "BR"));

    private String textoLembreteDeSerie(Evento evento) {
        String diaDaSemana = evento.getInicioEm().getDayOfWeek()
                .getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"));
        return evento.getTitulo() + " é " + diaDaSemana + ", "
                + evento.getInicioEm().format(FORMATADOR_LEMBRETE) + ". Vem participar!";
    }

    @Transactional(readOnly = true)
    public List<EventoArquivadoResponse> listarArquivados(UUID igrejaId) {
        return eventoRepository.findArquivadosPorIgreja(igrejaId).stream()
                .map(e -> EventoArquivadoResponse.de(e, inscricaoRepository.countByEventoId(e.getId())))
                .toList();
    }

    @Transactional
    public void restaurar(UUID id, UUID igrejaId) {
        int linhas = eventoRepository.restaurarPorId(id, igrejaId);
        if (linhas == 0) {
            throw new ResourceNotFoundException("Evento não encontrado.");
        }
        outboxRegistrador.registrar(TipoEntidadeOutbox.EVENTO, TipoEventoOutbox.ATUALIZADO, id, igrejaId);
        evictarCacheDeEventosDaFamilia(igrejaId);
    }

    /** Desvincula (apaga inscrições) em vez de bloquear, como Célula/Ministério — não Categoria. */
    @Transactional
    public void excluirDefinitivo(UUID id, UUID igrejaId) {
        eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        var inscricoes = inscricaoRepository.findByEventoId(id);
        inscricaoRepository.deleteAll(inscricoes);
        inscricaoRepository.flush();

        eventoRepository.hardDeleteById(id);
        outboxRegistrador.registrar(TipoEntidadeOutbox.EVENTO, TipoEventoOutbox.REMOVIDO, id, igrejaId);
        evictarCacheDeEventosDaFamilia(igrejaId);
    }

    /** Prévia de UX — o {@code InscricaoService} reavalia durante o POST como defesa real. */
    @Transactional(readOnly = true)
    public ElegibilidadeResponse elegibilidade(UUID eventoId, UUID pessoaId, UUID igrejaId) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Evento evento = eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        Pessoa pessoa = pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));
        return ElegibilidadeResponse.from(elegibilidadeService.avaliar(evento, pessoa));
    }

    /** Prévia (não grava nada) de quem ficaria de fora sob as restrições de {@code data}. */
    @Transactional(readOnly = true)
    public ImpactoRestricaoResponse calcularImpacto(UUID eventoId, EventoRequest data, UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarEventos(role)) {
            throw new AccessDeniedException(
                    "Você não tem permissão para ver o impacto desta restrição.");
        }

        eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarIdades(data);

        // Evento nunca persistido — só carrega as regras que ElegibilidadeService avalia.
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

    /** Controlar presença sem inscrição não faz sentido — não há lista de quem chamar. */
    private void validarControlaPresenca(EventoRequest data) {
        boolean controlaPresenca = Boolean.TRUE.equals(data.controlaPresenca());
        boolean requerInscricao = Boolean.TRUE.equals(data.requerInscricao());
        if (controlaPresenca && !requerInscricao) {
            throw new BusinessException("CONTROLA_PRESENCA_SEM_INSCRICAO",
                    "Só é possível controlar presença em eventos que também exigem inscrição.");
        }
    }

    private void validarIdades(EventoRequest data) {
        if (data.idadeMin() != null && data.idadeMax() != null && data.idadeMin() > data.idadeMax()) {
            throw new BusinessException("FAIXA_INVALIDA",
                    "A idade mínima não pode ser maior que a máxima.");
        }
    }

    /** Valida que localId e localTexto não vêm juntos (a constraint do banco é rede de segurança). */
    private LocalEvento resolverLocal(EventoRequest data, UUID igrejaId) {
        boolean temTexto = data.localTexto() != null && !data.localTexto().isBlank();
        if (data.localId() != null && temTexto) {
            throw new BusinessException("LOCAL_AMBIGUO",
                    "Escolha um local cadastrado ou digite um local, não os dois.");
        }
        if (data.localId() == null) {
            return null;
        }
        // localId de outra igreja é tratado como inexistente.
        return localEventoRepository.findByIdAndIgrejaId(data.localId(), igrejaId)
                .orElseThrow(() -> new BusinessException("LOCAL_NAO_ENCONTRADO",
                        "Local não encontrado."));
    }

    private Pessoa resolverResponsavel(UUID responsavelPessoaId, UUID igrejaId) {
        if (responsavelPessoaId == null) return null;
        return pessoaRepository.findByIdAndIgrejaId(responsavelPessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa responsável não encontrada."));
    }

    private com.domus.api.modules.evento.serie.EventoSerie criarSerie(
            com.domus.api.modules.evento.serie.DTOs.RecorrenciaRequest data, Igreja igreja, Usuario usuario) {
        String dias = data.diasSemana() == null || data.diasSemana().isEmpty()
                ? null
                : data.diasSemana().stream().map(Enum::name)
                        .collect(java.util.stream.Collectors.joining(","));
        var serie = com.domus.api.modules.evento.serie.EventoSerie.builder()
                .igreja(igreja)
                .frequencia(data.frequencia())
                .intervalo(data.intervalo() == null ? 1 : data.intervalo())
                .diasSemana(dias)
                .tipoRecorrenciaMensal(data.tipoRecorrenciaMensal())
                .dataFim(data.dataFim())
                .numeroOcorrencias(data.numeroOcorrencias())
                .criadoPor(usuario)
                .build();
        return eventoSerieRepository.save(serie);
    }

    /** Reusa grafia já existente da igreja se a forma normalizada bater — evita "Vigília"/"vigilia" duplicados. */
    private String resolverTipo(String tipoInformado, UUID igrejaId) {
        String capitalizado = TextoUtil.capitalizar(tipoInformado);
        if (capitalizado == null) return null;

        String normalizado = TextoUtil.normalizarParaComparacao(capitalizado);
        return eventoRepository.tiposUsadosPorFrequencia(igrejaId).stream()
                .filter(existente -> TextoUtil.normalizarParaComparacao(existente).equals(normalizado))
                .findFirst()
                .orElse(capitalizado);
    }

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

    private void evictarCacheDeEventosDaFamilia(UUID igrejaId) {
        familiaIgrejaService.idsDaFamiliaCompleta(igrejaId)
                .forEach(id -> cacheEvictor.evictPorIgreja("eventos", id));
    }

    /** EventoDocument.local vem do nome do LocalEvento; renomear/arquivar o local muda a busca de todo evento vinculado. */
    @Transactional
    public void reindexarPorLocal(UUID localId, UUID igrejaId) {
        List<Evento> eventos = eventoRepository.findByLocalIdAndIgrejaId(localId, igrejaId);
        if (eventos.isEmpty()) return;
        log.info("Reindexando {} eventos por alteração no local. local_id={}, igreja_id={}",
                eventos.size(), localId, igrejaId);
        eventos.forEach(evento -> outboxRegistrador.registrar(
                TipoEntidadeOutbox.EVENTO,
                TipoEventoOutbox.ATUALIZADO,
                evento.getId(),
                igrejaId
        ));
    }
}
