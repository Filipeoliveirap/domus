package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.SituacaoEvento;
import com.domus.api.modules.evento.DTOs.ImpactoRestricaoResponse;
import com.domus.api.modules.evento.elegibilidade.Elegibilidade;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.evento.elegibilidade.Impedimento;
import com.domus.api.modules.evento.elegibilidade.NaoElegivelException;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.evento.inscricao.DTOs.AcompanhanteRequest;
import com.domus.api.modules.evento.inscricao.DTOs.AcompanhanteResponse;
import com.domus.api.modules.evento.inscricao.DTOs.InscritoResponse;
import com.domus.api.modules.evento.inscricao.DTOs.ListaInscritosResponse;
import com.domus.api.modules.evento.inscricao.DTOs.MinhaInscricaoResponse;
import com.domus.api.modules.evento.inscricao.DTOs.ParticipanteResponse;
import com.domus.api.modules.evento.inscricao.DTOs.RegistranteResumo;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ConflitoNegocioException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.util.TextoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InscricaoService {

    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final AcompanhanteRepository acompanhanteRepository;
    private final PessoaRepository membroRepository;
    private final UsuarioRepository usuarioRepository;
    private final ElegibilidadeService elegibilidadeService;
    private final FamiliaIgrejaService familiaIgrejaService;

    /**
     * Inscreve um membro. {@code inscritoPorOuNull} é NULL na auto-inscrição.
     *
     * <p>Auto-inscrição funciona em QUALQUER evento, independente de {@code requerInscricao}.
     * O evento é buscado COM LOCK para atomizar contagem de vagas e insert.
     */
    @Transactional
    public MinhaInscricaoResponse inscrever(UUID eventoId, UUID pessoaId, UUID inscritoPorOuNull,
                                            UUID minhaPessoaId, String role, boolean confirmado, UUID igrejaId) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Evento evento = eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        Pessoa membro = membroRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));

        validarEventoAberto(evento);
        boolean porExcecao = validarElegibilidade(evento, membro, role, confirmado);

        InscricaoEvento inscricao = inscricaoRepository
                .findByEventoIdAndPessoaId(eventoId, pessoaId)
                .orElse(null);

        if (inscricao != null && inscricao.estaConfirmada()) {
            String mensagem = inscritoPorOuNull == null
                    ? "Você já está inscrito neste evento."
                    : "Este membro já está inscrito no evento.";
            throw new BusinessException("JA_INSCRITO", mensagem);
        }

        validarVaga(evento, 1);

        if (inscricao != null) {
            inscricao.setStatus(StatusInscricao.CONFIRMADA);
            inscricao.setInscritoPorUsuarioId(inscritoPorOuNull);
            inscricao.setInscritoPorExcecao(porExcecao);
        } else {
            inscricao = InscricaoEvento.builder()
                    .igreja(evento.getIgreja())
                    .evento(evento)
                    .pessoa(membro)
                    .inscritoPorUsuarioId(inscritoPorOuNull)
                    .status(StatusInscricao.CONFIRMADA)
                    .inscritoPorExcecao(porExcecao)
                    .build();
        }

        InscricaoEvento salva = inscricaoRepository.save(inscricao);
        log.info("Inscrição confirmada. evento_id={}, pessoa_id={}, inscrito_por={}, igreja_id={}",
                eventoId, pessoaId, inscritoPorOuNull, igrejaId);
        return MinhaInscricaoResponse.from(salva);
    }

    /**
     * Inscreve vários membros de uma vez — tudo ou nada. Checa os já inscritos em uma
     * query só antes do laço, para poder nomear a quantidade na mensagem de erro.
     */
    @Transactional
    public void inscreverPessoas(UUID eventoId, List<UUID> pessoaIds, UUID inscritoPorUsuarioId,
                                 UUID minhaPessoaId, String role, boolean confirmado, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarOrganizaInscricao(evento, "Este evento não organiza inscrição de outras pessoas.");
        validarEventoAberto(evento);

        if (!pessoaIds.isEmpty()) {
            List<UUID> jaInscritos = inscricaoRepository.listarPessoaIdsJaInscritos(eventoId, pessoaIds);
            if (!jaInscritos.isEmpty()) {
                String mensagem = jaInscritos.size() == 1
                        ? "Este membro já está inscrito no evento."
                        : jaInscritos.size() + " membros já estão inscritos no evento.";
                throw new BusinessException("JA_INSCRITO", mensagem);
            }
        }

        for (UUID pessoaId : pessoaIds) {
            inscrever(eventoId, pessoaId, inscritoPorUsuarioId, minhaPessoaId, role, confirmado, igrejaId);
        }
    }

    @Transactional(readOnly = true)
    public MinhaInscricaoResponse minhaInscricao(UUID eventoId, UUID pessoaId) {
        return inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)
                .filter(InscricaoEvento::estaConfirmada)
                .map(MinhaInscricaoResponse::from)
                .orElseGet(MinhaInscricaoResponse::naoInscrito);
    }

    /**
     * Checa se o evento organiza inscrição de TERCEIROS (vagas, convidados, inscrever outra
     * pessoa) — {@code requerInscricao}. NÃO se aplica à auto-inscrição (ver {@link #inscrever}),
     * que funciona em qualquer evento. Mensagem varia por chamador para dizer exatamente o que
     * está indisponível, em vez do genérico "não abre inscrição" (que ficou errado desde que a
     * auto-inscrição passou a valer sempre).
     */
    private void validarOrganizaInscricao(Evento evento, String mensagem) {
        if (!evento.isRequerInscricao()) {
            throw new BusinessException("INSCRICAO_NAO_HABILITADA", mensagem);
        }
    }

    /** Evento EM_ANDAMENTO ou ENCERRADO não aceita inscrição/convidado. */
    private void validarEventoAberto(Evento evento) {
        SituacaoEvento situacao = evento.getSituacao();
        if (situacao == SituacaoEvento.EM_ANDAMENTO) {
            throw new BusinessException("EVENTO_EM_ANDAMENTO",
                    "Este evento já começou. Não é mais possível se inscrever.");
        }
        if (situacao == SituacaoEvento.ENCERRADO) {
            throw new BusinessException("EVENTO_ENCERRADO",
                    "Este evento já aconteceu. Não é mais possível se inscrever.");
        }
    }

    /**
     * Bloqueia o mesmo convidado duas vezes no mesmo evento. Compara por telefone
     * normalizado, com fallback para nome normalizado se não houver telefone.
     */
    private void validarConvidadoNaoDuplicado(UUID eventoId, AcompanhanteRequest data) {
        String telefoneNovo = TextoUtil.somenteDigitos(data.telefone());
        String nomeNovo = TextoUtil.normalizarParaComparacao(data.nome());

        for (AcompanhanteInscricao existente : acompanhanteRepository.listarPorEvento(eventoId)) {
            String telefoneExistente = TextoUtil.somenteDigitos(existente.getTelefone());
            boolean duplicado = telefoneNovo != null && telefoneExistente != null
                    ? telefoneNovo.equals(telefoneExistente)
                    : nomeNovo != null && nomeNovo.equals(TextoUtil.normalizarParaComparacao(existente.getNome()));

            if (duplicado) {
                throw new BusinessException("CONVIDADO_DUPLICADO",
                        "Este convidado já está inscrito neste evento.");
            }
        }
    }

    /**
     * Aplica regras de {@link ElegibilidadeService} e decide se o impedimento pode ser
     * contornado. Auto-inscrição NUNCA contorna, nem para admin. Quem não gerencia também
     * não contorna e não vê detalhes de terceiro no 422. Vaga não entra aqui — é barrada
     * por {@link #validarVaga}, sempre.
     *
     * @return {@code true} se a inscrição contornou um impedimento deliberadamente
     *         ({@link InscricaoEvento#isInscritoPorExcecao}).
     */
    private boolean validarElegibilidade(Evento evento, Pessoa membro, String role, boolean confirmado) {
        Elegibilidade elegibilidade = elegibilidadeService.avaliar(evento, membro);
        if (elegibilidade.apto()) return false;

        // Quem GERENCIA inscrições (admin/líder) pode contornar uma restrição contornável,
        // inclusive na PRÓPRIA inscrição — decisão do autor: o gestor organiza os eventos e
        // pode participar de um recorte fora do seu (equipe do retiro de jovens, café dos
        // homens que ele coordena). Exige `confirmado` explícito por clique, então não é
        // burla casual. Quem NÃO gerencia nunca contorna (podeGerenciar == false barra),
        // então a restrição continua real para o membro comum — que era a proteção que
        // importava. VAGAS_ESGOTADAS não é contornável (não entra em totalmenteContornavel).
        boolean podeGerenciar = Permissoes.podeGerenciarInscricoes(role);
        boolean podeContornar = podeGerenciar
                && confirmado
                && elegibilidade.totalmenteContornavel();

        if (!podeContornar) {
            throw NaoElegivelException.para(elegibilidade.impedimentos(), podeGerenciar);
        }
        return true;
    }

    /**
     * Quantas pessoas (inscritos confirmados + acompanhantes) ocupam vaga hoje neste evento.
     * Exposto para o {@link com.domus.api.modules.evento.EventoService} usar na A9 (recusar
     * reduzir {@code vagas} abaixo de quem já está confirmado) sem duplicar a query.
     */
    @Transactional(readOnly = true)
    public long contarPessoasConfirmadas(UUID eventoId) {
        return inscricaoRepository.contarPessoasConfirmadas(eventoId);
    }

    /** {@code vagas == null} significa sem limite. */
    void validarVaga(Evento evento, int pessoasAAdicionar) {
        if (evento.getVagas() == null) return;

        long ocupadas = inscricaoRepository.contarPessoasConfirmadas(evento.getId());
        if (ocupadas + pessoasAAdicionar > evento.getVagas()) {
            throw new BusinessException("VAGAS_ESGOTADAS",
                    "As vagas deste evento estão esgotadas.");
        }
    }

    /** Convidado de fora, pendurado na inscrição de quem o trouxe. Ocupa vaga. */
    @Transactional
    public AcompanhanteResponse adicionarAcompanhante(UUID inscricaoId, AcompanhanteRequest data,
                                                       UUID usuarioId, UUID igrejaId) {
        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);

        validarOrganizaInscricao(inscricao.getEvento(), "Este evento não permite convidados.");

        if (inscricao.getEvento().isExclusivoMembros()) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros — não é possível levar convidados.");
        }
        validarEventoAberto(inscricao.getEvento());
        validarConvidadoNaoDuplicado(inscricao.getEvento().getId(), data);

        // Trava o evento antes de contar: mesma corrida da inscrição.
        eventoRepository.buscarComLock(inscricao.getEvento().getId(), igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarVaga(inscricao.getEvento(), 1);

        AcompanhanteInscricao a = AcompanhanteInscricao.builder()
                .inscricao(inscricao)
                .nome(TextoUtil.capitalizar(data.nome()))
                .telefone(data.telefone())
                .build();

        AcompanhanteInscricao salvo = acompanhanteRepository.save(a);
        log.info("Acompanhante adicionado. inscricao_id={}, por_usuario={}, igreja_id={}",
                inscricaoId, usuarioId, igrejaId);
        return AcompanhanteResponse.from(salvo);
    }

    @Transactional
    public void removerAcompanhante(UUID acompanhanteId, UUID meuMembroId, String role, UUID igrejaId) {
        AcompanhanteInscricao a = acompanhanteRepository.findById(acompanhanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Convidado não encontrado."));

        InscricaoEvento inscricao = a.getInscricao();
        if (!familiaIgrejaService.idsDaFamiliaCompleta(igrejaId).contains(inscricao.getIgreja().getId())) {
            throw new ResourceNotFoundException("Convidado não encontrado.");
        }

        // A2: mesma trava de cancelar() — remover convidado de evento EM_ANDAMENTO/ENCERRADO
        // reescreveria quem esteve presente. Vale para TODO MUNDO, admin incluso (ver Javadoc
        // de cancelar()).
        validarEventoAberto(inscricao.getEvento());

        // A permissão vem de SER DONO DA INSCRIÇÃO, não de ter sido quem inscreveu.
        // Comparar com inscritoPorUsuarioId seria furo: ele é NULL em toda auto-inscrição
        // (o caso mais comum), e qualquer NULL-check liberaria geral.
        boolean ehGestor = Permissoes.podeGerenciarInscricoes(role);
        boolean souODono = inscricao.getPessoa().getId().equals(meuMembroId);

        if (!ehGestor && !souODono) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você só pode remover convidados da sua própria inscrição.");
        }
        acompanhanteRepository.delete(a);
        log.info("Acompanhante removido. acompanhante_id={}, por_membro={}, igreja_id={}",
                acompanhanteId, meuMembroId, igrejaId);
    }

    /**
     * Cancela uma inscrição. Cada pessoa controla a sua; gestores controlam qualquer uma.
     * Evento EM_ANDAMENTO/ENCERRADO não permite cancelamento — presença é histórico.
     */
    @Transactional
    public void cancelar(UUID inscricaoId, UUID usuarioId, UUID meuMembroId,
                         String role, UUID igrejaId) {
        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);
        validarEventoAberto(inscricao.getEvento());

        boolean ehGestor = Permissoes.podeGerenciarInscricoes(role);
        boolean souEu = inscricao.getPessoa().getId().equals(meuMembroId);

        if (!ehGestor && !souEu) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você não pode cancelar a inscrição de outra pessoa. "
                    + "Peça a ela ou a um líder da igreja.");
        }

        int convidados = inscricao.getAcompanhantes().size();
        cancelarInterno(inscricao);
        log.info("Inscrição cancelada. id={}, convidados_removidos={}, por_usuario={}, igreja_id={}",
                inscricaoId, convidados, usuarioId, igrejaId);
    }

    /**
     * O cancelamento em si, reusado pelo cancelamento manual e pela remoção por restrição.
     * Convidados vão junto com a inscrição e não voltam numa reinscrição.
     */
    private void cancelarInterno(InscricaoEvento inscricao) {
        inscricao.getAcompanhantes().clear();   // orphanRemoval = true apaga as linhas
        inscricao.setStatus(StatusInscricao.CANCELADA);
        inscricaoRepository.save(inscricao);
    }

    /**
     * Cancela inscrições de quem não é mais elegível para as regras atuais do evento.
     * Só roda com escolha EXPLÍCITA do admin (campo {@code cancelarNaoElegiveis} no PUT).
     * Pula quem foi inscrito por exceção deliberada ({@link InscricaoEvento#isInscritoPorExcecao}).
     */
    @Transactional
    public int removerInscritosNaoElegiveis(UUID eventoId) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        int removidos = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            if (inscricao.isInscritoPorExcecao()) continue;

            Elegibilidade elegibilidade = elegibilidadeService.avaliar(
                    inscricao.getEvento(), inscricao.getPessoa());
            if (!elegibilidade.apto()) {
                cancelarInterno(inscricao);
                removidos++;
            }
        }

        if (removidos > 0) {
            log.info("Inscrições removidas por restrição de evento (escolha explícita). "
                    + "evento_id={}, removidos={}", eventoId, removidos);
        }
        return removidos;
    }

    /**
     * Prévia PURA (nada é gravado) de quem, dentre os CONFIRMADOS de hoje, ficaria de fora sob
     * {@code regrasHipoteticas} — alimenta {@code POST /eventos/{id}/impacto-restricao}
     * (ver {@link EventoService#calcularImpacto}).
     *
     * <p>Pula quem já tem {@link InscricaoEvento#isInscritoPorExcecao()}: essas exceções são
     * permanentes por decisão do admin, então nunca aparecem como "afetadas" nem sob regra mais
     * apertada — o admin já escolheu, uma vez, mantê-las.
     */
    @Transactional(readOnly = true)
    public List<ImpactoRestricaoResponse.InscritoImpactado> calcularImpacto(
            UUID eventoId, Evento regrasHipoteticas) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        List<ImpactoRestricaoResponse.InscritoImpactado> afetados = new ArrayList<>();

        for (InscricaoEvento inscricao : inscricoes) {
            if (inscricao.isInscritoPorExcecao()) continue;

            Elegibilidade elegibilidade = elegibilidadeService.avaliar(
                    regrasHipoteticas, inscricao.getPessoa());
            if (!elegibilidade.apto()) {
                List<String> motivos = elegibilidade.impedimentos().stream()
                        .map(Impedimento::mensagem)
                        .toList();
                afetados.add(new ImpactoRestricaoResponse.InscritoImpactado(
                        inscricao.getPessoa().getId(), inscricao.getPessoa().getNome(), motivos));
            }
        }
        return afetados;
    }

    /**
     * Cancela as inscrições CONFIRMADAS de uma pessoa em eventos {@code exclusivoMembros},
     * chamada quando o vínculo dela deixa de ser MEMBRO (ver {@link
     * com.domus.api.modules.pessoa.PessoaService#atualizarMembro}).
     *
     * <p>Sem isto, alguém que perde o vínculo MEMBRO continuaria confirmado e ocupando vaga
     * num evento exclusivo para membros — a mesma inscrição que {@link #validarElegibilidade}
     * recusaria se fosse tentada de novo. Reusa {@link #cancelarInterno}, então os
     * acompanhantes são removidos junto, igual a qualquer outro cancelamento.
     *
     * @return quantas inscrições foram canceladas.
     */
    @Transactional
    public int cancelarInscricoesEmEventosExclusivos(UUID pessoaId) {
        List<InscricaoEvento> inscricoes = inscricaoRepository
                .findByPessoaIdAndStatusAndEventoExclusivoMembrosTrue(pessoaId, StatusInscricao.CONFIRMADA);

        for (InscricaoEvento inscricao : inscricoes) {
            cancelarInterno(inscricao);
        }

        if (!inscricoes.isEmpty()) {
            log.info("Inscrições canceladas por perda de vínculo MEMBRO. pessoa_id={}, canceladas={}",
                    pessoaId, inscricoes.size());
        }
        return inscricoes.size();
    }

    /**
     * Lista PAGINADA de inscritos confirmados + contagem de vagas restantes. {@code busca}
     * (nome do inscrito, opcional) e a paginação afetam só {@code inscritos} — total de
     * pessoas/vagas restantes sempre contam TODAS as confirmadas do evento.
     */
    @Transactional(readOnly = true)
    public ListaInscritosResponse listarInscritos(UUID eventoId, UUID igrejaId, String busca, Pageable pageable) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        // Paginar direto numa query com JOIN FETCH de coleção (acompanhantes) faz o Hibernate
        // paginar EM MEMÓRIA — por isso os ids vêm paginados primeiro, e os detalhes completos
        // depois, por IN (mesma ordem, createdAt ASC nas duas queries).
        Page<UUID> idsPagina = inscricaoRepository.listarIdsPaginadoPorEvento(eventoId, busca, pageable);
        List<InscricaoEvento> inscricoes = inscricaoRepository.listarComDetalhesPorIds(idsPagina.getContent());

        // Resolve "quem inscreveu" em UMA query para a página inteira (evita N+1): coleta os
        // ids distintos e não-nulos e busca nome+foto em lote. Ids ausentes no mapa de volta
        // (conta ou membro arquivados depois da inscrição) viram null no DTO — tratados
        // explicitamente, não escondidos atrás de um texto genérico incorreto.
        Map<UUID, RegistranteResumo> registrantes = buscarRegistrantesEmLote(inscricoes);

        List<InscritoResponse> inscritosDaPagina = inscricoes.stream()
                .map(i -> InscritoResponse.from(i, registrantes.get(i.getInscritoPorUsuarioId())))
                .toList();
        PagedResponse<InscritoResponse> paginaInscritos = PagedResponse.from(
                new PageImpl<>(inscritosDaPagina, pageable, idsPagina.getTotalElements()));

        long total = inscricaoRepository.contarPessoasConfirmadas(eventoId);
        Integer restantes = evento.getVagas() == null
                ? null
                : Math.max(0, evento.getVagas() - (int) total);

        return new ListaInscritosResponse(total, evento.getVagas(), restantes, paginaInscritos);
    }

    /**
     * Lista de participantes visível a QUALQUER MEMBRO — versão reduzida de
     * {@link #listarInscritos}, sem telefone de convidado, sem "quem inscreveu quem" e sem
     * data da inscrição (ver Javadoc de {@link ParticipanteResponse}).
     */
    @Transactional(readOnly = true)
    public List<ParticipanteResponse> listarParticipantes(UUID eventoId, UUID igrejaId) {
        eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        return inscricaoRepository.listarPorEvento(eventoId)
                .stream().map(ParticipanteResponse::from).toList();
    }

    /**
     * Busca nome+foto de quem inscreveu para toda a lista, numa única query (ou nenhuma,
     * se ninguém foi inscrito por terceiro). {@code Map.of()} do {@code Collectors.toMap}
     * já garante ids únicos porque a origem é uma coluna de PK.
     */
    private Map<UUID, RegistranteResumo> buscarRegistrantesEmLote(List<InscricaoEvento> inscricoes) {
        List<UUID> ids = inscricoes.stream()
                .map(InscricaoEvento::getInscritoPorUsuarioId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        // HashMap (não Map.of()): .get(null) é uma consulta legítima e frequente aqui —
        // toda auto-inscrição tem inscritoPorUsuarioId nulo, e Map.of() lança NPE em
        // chave nula.
        Map<UUID, RegistranteResumo> mapa = new HashMap<>();
        if (ids.isEmpty()) {
            return mapa;
        }

        for (RegistranteResumo r : usuarioRepository.buscarRegistrantes(ids)) {
            mapa.put(r.usuarioId(), r);
        }
        return mapa;
    }

    private InscricaoEvento buscarInscricao(UUID id, UUID minhaIgrejaId) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(minhaIgrejaId);
        return inscricaoRepository.buscarVisivelParaFamilia(id, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
    }

    /**
     * Marca presente TODO inscrito CONFIRMADO do evento e TODOS os seus acompanhantes —
     * o fluxo real é "quase todo mundo veio", e cada linha ganha um checkbox individual
     * para corrigir a exceção depois (ver {@link #marcarPresencaInscricao}/
     * {@link #marcarPresencaAcompanhante}).
     *
     * @return quantas PESSOAS FÍSICAS (inscritos + acompanhantes) foram marcadas.
     */
    @Transactional
    public int marcarTodosPresentes(UUID eventoId, UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para marcar presença.");
        }

        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarControlaPresenca(evento);

        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        int marcados = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            inscricao.setCompareceu(true);
            marcados++;
            for (AcompanhanteInscricao acompanhante : inscricao.getAcompanhantes()) {
                acompanhante.setCompareceu(true);
                marcados++;
            }
            inscricaoRepository.save(inscricao);
        }

        log.info("Presença marcada em lote. evento_id={}, pessoas_marcadas={}, igreja_id={}",
                eventoId, marcados, igrejaId);
        return marcados;
    }

    /** Corrige a exceção de um inscrito específico após o "marcar todos" (ou o contrário). */
    @Transactional
    public void marcarPresencaInscricao(UUID eventoId, UUID inscricaoId, boolean compareceu,
                                        UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para marcar presença.");
        }

        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);
        validarControlaPresenca(inscricao.getEvento());
        validarInscricaoConfirmada(inscricao);

        inscricao.setCompareceu(compareceu);
        inscricaoRepository.save(inscricao);
        log.info("Presença individual marcada. inscricao_id={}, compareceu={}, igreja_id={}",
                inscricaoId, compareceu, igrejaId);
    }

    /** Corrige a exceção de UM convidado específico (o inscrito veio, o convidado não, ou vice-versa). */
    @Transactional
    public void marcarPresencaAcompanhante(UUID eventoId, UUID acompanhanteId, boolean compareceu,
                                           UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para marcar presença.");
        }

        AcompanhanteInscricao acompanhante = acompanhanteRepository.findById(acompanhanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Convidado não encontrado."));

        // Mesmo isolamento multi-tenant de removerAcompanhante(): id de outra igreja é
        // tratado como inexistente, nunca vaza que existe fora da própria igreja.
        if (!acompanhante.getInscricao().getIgreja().getId().equals(igrejaId)) {
            throw new ResourceNotFoundException("Convidado não encontrado.");
        }

        validarControlaPresenca(acompanhante.getInscricao().getEvento());
        validarInscricaoConfirmada(acompanhante.getInscricao());

        acompanhante.setCompareceu(compareceu);
        acompanhanteRepository.save(acompanhante);
        log.info("Presença de convidado marcada. acompanhante_id={}, compareceu={}, igreja_id={}",
                acompanhanteId, compareceu, igrejaId);
    }

    /** Espelha o CHECK do banco (V6): sem controlaPresenca não existe presença para marcar. */
    private void validarControlaPresenca(Evento evento) {
        if (!evento.isControlaPresenca()) {
            throw new ConflitoNegocioException("PRESENCA_NAO_HABILITADA",
                    "Este evento não controla presença.");
        }
    }

    /**
     * Só é editável se a inscrição estiver CONFIRMADA (spec do relatório de eventos,
     * 2026-07-23): uma inscrição CANCELADA não deveria ter presença marcada/desmarcada.
     */
    private void validarInscricaoConfirmada(InscricaoEvento inscricao) {
        if (inscricao.getStatus() != StatusInscricao.CONFIRMADA) {
            throw new ConflitoNegocioException("INSCRICAO_NAO_CONFIRMADA",
                    "Esta inscrição está cancelada e não pode ter presença marcada.");
        }
    }
}
