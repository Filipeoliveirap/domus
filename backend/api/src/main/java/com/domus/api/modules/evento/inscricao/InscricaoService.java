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

    /** Auto-inscrição funciona em QUALQUER evento, independente de {@code requerInscricao}; evento buscado com lock. */
    @Transactional
    public MinhaInscricaoResponse inscrever(UUID eventoId, UUID pessoaId, UUID inscritoPorOuNull,
                                            UUID minhaPessoaId, String role, boolean confirmado, UUID igrejaId) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Evento evento = eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        Pessoa membro = membroRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));

        validarEventoAberto(evento);
        boolean porExcecao = validarElegibilidade(evento, membro, role, confirmado, igrejaId);

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

    /** Tudo ou nada — checa os já inscritos em uma query só para nomear a quantidade no erro. */
    @Transactional
    public void inscreverPessoas(UUID eventoId, List<UUID> pessoaIds, UUID inscritoPorUsuarioId,
                                 UUID minhaPessoaId, String role, boolean confirmado, UUID igrejaId) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Evento evento = eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
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

    /** {@code requerInscricao} não se aplica à auto-inscrição (ver {@link #inscrever}), que funciona em qualquer evento. */
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

    /** Compara por telefone normalizado, com fallback para nome se não houver telefone. */
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
     * Auto-inscrição NUNCA contorna, nem para admin. Vaga não entra aqui — é barrada por {@link #validarVaga}, sempre.
     * @return {@code true} se contornou um impedimento deliberadamente ({@link InscricaoEvento#isInscritoPorExcecao}).
     */
    private boolean validarElegibilidade(Evento evento, Pessoa membro, String role, boolean confirmado,
                                          UUID igrejaId) {
        Elegibilidade elegibilidade = elegibilidadeService.avaliar(evento, membro);
        if (elegibilidade.apto()) return false;

        // Quem gerencia pode contornar restrição contornável mesmo na própria inscrição; exige `confirmado` explícito.
        boolean podeGerenciar = Permissoes.podeGerenciarInscricoes(role)
                && evento.getIgreja().getId().equals(igrejaId);
        boolean podeContornar = podeGerenciar
                && confirmado
                && elegibilidade.totalmenteContornavel();

        if (!podeContornar) {
            throw NaoElegivelException.para(elegibilidade.impedimentos(), podeGerenciar);
        }
        return true;
    }

    /** Exposto para {@link com.domus.api.modules.evento.EventoService} recusar reduzir vagas abaixo do confirmado. */
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
                                                       UUID usuarioId, UUID meuMembroId, String role,
                                                       UUID igrejaId) {
        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);

        boolean ehGestor = Permissoes.podeGerenciarInscricoes(role);
        boolean gestorDaMesmaIgreja = ehGestor && inscricao.getIgreja().getId().equals(igrejaId);
        boolean souODono = inscricao.getPessoa() != null && inscricao.getPessoa().getId().equals(meuMembroId);
        if (!gestorDaMesmaIgreja && !souODono) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você só pode adicionar convidados à sua própria inscrição.");
        }

        validarOrganizaInscricao(inscricao.getEvento(), "Este evento não permite convidados.");

        if (inscricao.getEvento().isExclusivoMembros()) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros — não é possível levar convidados.");
        }
        validarEventoAberto(inscricao.getEvento());
        validarConvidadoNaoDuplicado(inscricao.getEvento().getId(), data);

        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        eventoRepository.buscarComLockVisivelParaFamilia(inscricao.getEvento().getId(), igrejaId, idsFamilia)
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

        // Mesma trava de cancelar(): evento EM_ANDAMENTO/ENCERRADO reescreveria quem esteve presente.
        validarEventoAberto(inscricao.getEvento());

        // Permissão vem de ser dono da inscrição, não de quem inscreveu — inscritoPorUsuarioId é NULL na maioria dos casos.
        boolean ehGestor = Permissoes.podeGerenciarInscricoes(role);
        boolean gestorDaMesmaIgreja = ehGestor && inscricao.getIgreja().getId().equals(igrejaId);
        boolean souODono = inscricao.getPessoa() != null && inscricao.getPessoa().getId().equals(meuMembroId);

        if (!gestorDaMesmaIgreja && !souODono) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você só pode remover convidados da sua própria inscrição.");
        }
        acompanhanteRepository.delete(a);
        log.info("Acompanhante removido. acompanhante_id={}, por_membro={}, igreja_id={}",
                acompanhanteId, meuMembroId, igrejaId);
    }

    /** Evento EM_ANDAMENTO/ENCERRADO não permite cancelamento — presença é histórico. */
    @Transactional
    public void cancelar(UUID inscricaoId, UUID usuarioId, UUID meuMembroId,
                         String role, UUID igrejaId) {
        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);
        validarEventoAberto(inscricao.getEvento());

        boolean ehGestor = Permissoes.podeGerenciarInscricoes(role);
        boolean gestorDaMesmaIgreja = ehGestor && inscricao.getIgreja().getId().equals(igrejaId);
        boolean souEu = inscricao.getPessoa() != null && inscricao.getPessoa().getId().equals(meuMembroId);

        if (!gestorDaMesmaIgreja && !souEu) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você não pode cancelar a inscrição de outra pessoa. "
                    + "Peça a ela ou a um líder da igreja.");
        }

        int convidados = inscricao.getAcompanhantes().size();
        cancelarInterno(inscricao);
        log.info("Inscrição cancelada. id={}, convidados_removidos={}, por_usuario={}, igreja_id={}",
                inscricaoId, convidados, usuarioId, igrejaId);
    }

    /** Reusado pelo cancelamento manual e pela remoção por restrição — convidados não voltam numa reinscrição. */
    private void cancelarInterno(InscricaoEvento inscricao) {
        inscricao.getAcompanhantes().clear();   // orphanRemoval = true apaga as linhas
        inscricao.setStatus(StatusInscricao.CANCELADA);
        inscricaoRepository.save(inscricao);
    }

    /**
     * Chamado ao arquivar a pessoa (antes do soft delete, pessoa ainda ativa aqui). Evento que
     * ainda vai acontecer perde a vaga dela — cancela, como qualquer cancelamento normal. Evento
     * já em andamento ou encerrado não mexe: ela participou, isso é histórico e continua exibindo
     * os dados normalmente (ela só está arquivada, não excluída).
     */
    @Transactional
    public void cancelarInscricoesEmEventosAbertosPorPessoa(UUID pessoaId) {
        List<InscricaoEvento> confirmadas = inscricaoRepository.findByPessoaIdAndStatus(pessoaId, StatusInscricao.CONFIRMADA);
        for (InscricaoEvento inscricao : confirmadas) {
            if (inscricao.getEvento().getSituacao() == SituacaoEvento.AGENDADO) {
                cancelarInterno(inscricao);
            }
        }
    }

    /** Só roda com escolha explícita do admin ({@code cancelarNaoElegiveis}); pula exceções deliberadas. */
    @Transactional
    public int removerInscritosNaoElegiveis(UUID eventoId) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        Map<UUID, Pessoa> pessoas = resolverPessoasEmLote(inscricoes);
        int removidos = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            Pessoa pessoa = resolverPessoa(inscricao, pessoas);
            // Pessoa excluída (LGPD): não dá pra reavaliar elegibilidade de quem não existe mais.
            if (inscricao.isInscritoPorExcecao() || pessoa == null) continue;

            Elegibilidade elegibilidade = elegibilidadeService.avaliar(inscricao.getEvento(), pessoa);
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

    /** Prévia pura (nada gravado); pula exceção deliberada — o admin já escolheu manter aquela inscrição. */
    @Transactional(readOnly = true)
    public List<ImpactoRestricaoResponse.InscritoImpactado> calcularImpacto(
            UUID eventoId, Evento regrasHipoteticas) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        Map<UUID, Pessoa> pessoas = resolverPessoasEmLote(inscricoes);
        List<ImpactoRestricaoResponse.InscritoImpactado> afetados = new ArrayList<>();

        for (InscricaoEvento inscricao : inscricoes) {
            Pessoa pessoa = resolverPessoa(inscricao, pessoas);
            if (inscricao.isInscritoPorExcecao() || pessoa == null) continue;

            Elegibilidade elegibilidade = elegibilidadeService.avaliar(regrasHipoteticas, pessoa);
            if (!elegibilidade.apto()) {
                List<String> motivos = elegibilidade.impedimentos().stream()
                        .map(Impedimento::mensagem)
                        .toList();
                afetados.add(new ImpactoRestricaoResponse.InscritoImpactado(
                        pessoa.getId(), pessoa.getNome(), motivos));
            }
        }
        return afetados;
    }

    /**
     * Chamada quando o vínculo deixa de ser MEMBRO — sem isto a pessoa continuaria ocupando vaga em evento exclusivo.
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

    /** {@code busca} e a paginação afetam só {@code inscritos} — total/vagas restantes contam TODAS as confirmadas. */
    @Transactional(readOnly = true)
    public ListaInscritosResponse listarInscritos(UUID eventoId, UUID igrejaId, String busca, Pageable pageable) {
        // IncluindoArquivados: a tela de Arquivados também abre a lista de inscritos de um
        // evento arquivado (pra só olhar) — arquivar não desvincula ninguém.
        Evento evento = eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        // JOIN FETCH de coleção paginaria em memória no Hibernate — ids paginados primeiro, detalhes por IN depois.
        Page<UUID> idsPagina = inscricaoRepository.listarIdsPaginadoPorEvento(eventoId, busca, pageable);
        List<InscricaoEvento> inscricoes = inscricaoRepository.listarComDetalhesPorIds(idsPagina.getContent());

        // Resolve "quem inscreveu" em UMA query (evita N+1); id ausente no mapa (conta arquivada) vira null explícito.
        Map<UUID, RegistranteResumo> registrantes = buscarRegistrantesEmLote(inscricoes);
        Map<UUID, Pessoa> pessoas = resolverPessoasEmLote(inscricoes);

        List<InscritoResponse> inscritosDaPagina = inscricoes.stream()
                .map(i -> InscritoResponse.from(i,
                        resolverPessoa(i, pessoas),
                        registrantes.get(i.getInscritoPorUsuarioId())))
                .toList();
        PagedResponse<InscritoResponse> paginaInscritos = PagedResponse.from(
                new PageImpl<>(inscritosDaPagina, pageable, idsPagina.getTotalElements()));

        long total = inscricaoRepository.contarPessoasConfirmadas(eventoId);
        Integer restantes = evento.getVagas() == null
                ? null
                : Math.max(0, evento.getVagas() - (int) total);

        return new ListaInscritosResponse(total, evento.getVagas(), restantes, paginaInscritos);
    }

    /** Visível a QUALQUER MEMBRO — versão reduzida de {@link #listarInscritos} (ver {@link ParticipanteResponse}). */
    @Transactional(readOnly = true)
    public List<ParticipanteResponse> listarParticipantes(UUID eventoId, UUID igrejaId) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        // IncluindoArquivados: mesma razão do buscarPorId — abrir o detalhe de um evento
        // arquivado (tela de Arquivados) também carrega essa lista reduzida.
        eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
                .or(() -> eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId))
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        Map<UUID, Pessoa> pessoas = resolverPessoasEmLote(inscricoes);
        return inscricoes.stream()
                .map(i -> ParticipanteResponse.from(i, resolverPessoa(i, pessoas)))
                .toList();
    }

    /**
     * Em lote, via bypass do @SQLRestriction (nunca {@code i.getPessoa().getNome()} direto) —
     * pessoa arquivada (mas não excluída) continua tendo os dados reais resolvidos aqui; some
     * do mapa só quando foi excluída de vez (pessoa_id já é NULL nesse caso, nem entra na busca).
     */
    private Map<UUID, Pessoa> resolverPessoasEmLote(List<InscricaoEvento> inscricoes) {
        List<UUID> ids = inscricoes.stream()
                .map(InscricaoEvento::getPessoa)
                .filter(p -> p != null)
                .map(Pessoa::getId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return membroRepository.findByIdInIncluindoArquivadas(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Pessoa::getId, p -> p));
    }

    /**
     * Resolve a pessoa de exibição pra uma inscrição. Em produção, {@code i.getPessoa()} é sempre
     * um proxy lazy do Hibernate (repositório nunca mais faz JOIN FETCH em pessoa) — usa o mapa
     * resolvido em lote pra não estourar @SQLRestriction se ela estiver arquivada. Em teste
     * (objeto construído direto via builder), não é proxy — usa direto, sem depender do mock do
     * bypass estar stubado.
     */
    private Pessoa resolverPessoa(InscricaoEvento i, Map<UUID, Pessoa> pessoasResolvidas) {
        Pessoa p = i.getPessoa();
        if (p == null) return null;
        if (!(p instanceof org.hibernate.proxy.HibernateProxy)) return p;
        return pessoasResolvidas.get(p.getId());
    }

    /** Numa única query (ou nenhuma, se ninguém foi inscrito por terceiro). */
    private Map<UUID, RegistranteResumo> buscarRegistrantesEmLote(List<InscricaoEvento> inscricoes) {
        List<UUID> ids = inscricoes.stream()
                .map(InscricaoEvento::getInscritoPorUsuarioId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        // HashMap, não Map.of(): .get(null) é consulta legítima aqui (auto-inscrição) e Map.of() lança NPE em chave nula.
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
     * Fluxo real é "quase todo mundo veio" — exceções se corrigem depois via {@link #marcarPresencaInscricao}.
     * @return quantas pessoas físicas (inscritos + acompanhantes) foram marcadas.
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

    /** Reverte um "marcar todos" feito sem querer, ou reinicia a contagem de presença do zero. */
    @Transactional
    public int desmarcarTodosPresentes(UUID eventoId, UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para marcar presença.");
        }

        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarControlaPresenca(evento);

        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        int desmarcados = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            inscricao.setCompareceu(false);
            desmarcados++;
            for (AcompanhanteInscricao acompanhante : inscricao.getAcompanhantes()) {
                acompanhante.setCompareceu(false);
                desmarcados++;
            }
            inscricaoRepository.save(inscricao);
        }

        log.info("Presença desmarcada em lote. evento_id={}, pessoas_desmarcadas={}, igreja_id={}",
                eventoId, desmarcados, igrejaId);
        return desmarcados;
    }

    /** Corrige a exceção de um inscrito específico após o "marcar todos" (ou o contrário). */
    @Transactional
    public void marcarPresencaInscricao(UUID eventoId, UUID inscricaoId, boolean compareceu,
                                        UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para marcar presença.");
        }

        InscricaoEvento inscricao = inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
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

        // Mesmo isolamento de removerAcompanhante(): id de outra igreja é tratado como inexistente, nunca vaza.
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

    /** Inscrição CANCELADA não deveria ter presença marcada/desmarcada. */
    private void validarInscricaoConfirmada(InscricaoEvento inscricao) {
        if (inscricao.getStatus() != StatusInscricao.CONFIRMADA) {
            throw new ConflitoNegocioException("INSCRICAO_NAO_CONFIRMADA",
                    "Esta inscrição está cancelada e não pode ter presença marcada.");
        }
    }
}
