package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoRequest;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoResponse;
import com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaResponse;
import com.domus.api.modules.evento.campopersonalizado.OrigemResposta;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampoPersonalizadoService {

    private final CampoPersonalizadoEventoRepository campoRepository;
    private final RespostaCampoPersonalizadoRepository respostaRepository;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;
    private final PessoaRepository pessoaRepository;

    public List<CampoPersonalizadoResponse> listar(UUID eventoId, UUID igrejaId) {
        return campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId).stream()
                .map(CampoPersonalizadoResponse::from)
                .toList();
    }

    /** Só os campos que ainda precisam de resposta — pula os mapeados que a Pessoa já tem.
     *  {@code pessoaOuNull} nulo (convidado sem cadastro) nunca pula nenhum. */
    public List<CampoPersonalizadoResponse> listarParaResponder(UUID eventoId, UUID igrejaId, com.domus.api.modules.pessoa.Pessoa pessoaOuNull) {
        return campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId).stream()
                .filter(c -> valorJaConhecido(c.getMapeamento(), pessoaOuNull).isEmpty())
                .map(CampoPersonalizadoResponse::from)
                .toList();
    }

    /** Mesma coisa que {@link #listarParaResponder}, mas carrega a Pessoa a partir do id —
     *  usado pelo endpoint autenticado "meus campos pendentes" (auto-inscrição normal e
     *  convite público já logado). Nunca receber a Pessoa do principal autenticado direto
     *  (é um objeto desanexado — ler campo LAZY dele estoura); sempre buscar de novo aqui. */
    public List<CampoPersonalizadoResponse> listarParaResponderComoTitular(UUID eventoId, UUID igrejaId, UUID pessoaId) {
        var pessoa = pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId).orElse(null);
        return listarParaResponder(eventoId, igrejaId, pessoa);
    }

    private java.util.Optional<String> valorJaConhecido(MapeamentoCampoPersonalizado mapeamento,
                                                          com.domus.api.modules.pessoa.Pessoa pessoa) {
        if (pessoa == null || mapeamento == null) return java.util.Optional.empty();
        return switch (mapeamento) {
            case IDADE -> java.util.Optional.ofNullable(pessoa.getDataNascimento())
                    .map(d -> String.valueOf(java.time.Period.between(d, java.time.LocalDate.now()).getYears()));
            case ESTADO_CIVIL -> java.util.Optional.ofNullable(pessoa.getEstadoCivil()).map(Enum::name);
            case SEXO -> java.util.Optional.ofNullable(pessoa.getSexo()).map(Enum::name);
            case ENDERECO -> temAlgumDadoDeEndereco(pessoa.getEndereco())
                    ? java.util.Optional.of(formatarEndereco(pessoa.getEndereco())) : java.util.Optional.empty();
        };
    }

    private boolean temAlgumDadoDeEndereco(com.domus.api.shared.dominio.Endereco e) {
        if (e == null) return false;
        return e.getCep() != null || e.getLogradouro() != null || e.getNumero() != null
                || e.getComplemento() != null || e.getBairro() != null || e.getCidade() != null || e.getUf() != null;
    }

    private String formatarEndereco(com.domus.api.shared.dominio.Endereco e) {
        StringBuilder sb = new StringBuilder();
        if (e.getLogradouro() != null) sb.append(e.getLogradouro());
        if (e.getNumero() != null) sb.append(", ").append(e.getNumero());
        if (e.getBairro() != null) sb.append(" - ").append(e.getBairro());
        if (e.getCidade() != null) sb.append(", ").append(e.getCidade());
        if (e.getUf() != null) sb.append("/").append(e.getUf());
        return sb.toString();
    }

    /** Substitui a lista inteira: cria o que não tem id, atualiza o que tem, arquiva
     *  (soft delete) o que já existia e sumiu da lista enviada. Editar campo (inclusive
     *  opções) é sempre livre, mesmo com resposta já dada — resposta guarda snapshot. */
    @Transactional
    public List<CampoPersonalizadoResponse> salvar(UUID eventoId, UUID igrejaId, List<CampoPersonalizadoRequest> dados,
                                                    UUID usuarioIdAtor) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        List<CampoPersonalizadoEvento> existentes =
                campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId);
        Map<UUID, CampoPersonalizadoEvento> porId = new HashMap<>();
        for (CampoPersonalizadoEvento c : existentes) porId.put(c.getId(), c);

        java.util.Set<UUID> idsEnviados = new java.util.HashSet<>();
        List<CampoPersonalizadoEvento> resultado = new java.util.ArrayList<>();
        boolean surgiuCampoObrigatorioNovo = false;

        for (CampoPersonalizadoRequest r : dados) {
            CampoPersonalizadoEvento campo = r.id() != null ? porId.get(r.id()) : null;
            boolean eraObrigatorioAntes = campo != null && campo.isObrigatorio();
            if (campo == null) {
                campo = CampoPersonalizadoEvento.builder().igreja(evento.getIgreja()).evento(evento).build();
            } else {
                idsEnviados.add(campo.getId());
            }
            // Campo novo obrigatório, ou campo existente que virou obrigatório agora —
            // quem já estava confirmado no evento precisa saber que tem pergunta nova.
            if (r.obrigatorio() && !eraObrigatorioAntes) {
                surgiuCampoObrigatorioNovo = true;
            }
            // Mapeamento não depende de tipo/opções: valorJaConhecido() lê sempre o dado bruto
            // da Pessoa (pessoa.getEstadoCivil(), pessoa.getSexo()…), nunca o texto das opções
            // do campo — então mudar o tipo ou reescrever as opções não invalida o "pula
            // pergunta pra quem já tem esse dado". Editar/remover o mapeamento é escolha
            // explícita do admin no seletor, refletida direto em r.mapeamento().
            campo.setLabel(r.label());
            campo.setPlaceholder(r.placeholder());
            campo.setTipo(r.tipo());
            campo.setOpcoesComoLista(r.opcoes());
            campo.setObrigatorio(r.obrigatorio());
            campo.setVisivelAoPublico(r.visivelAoPublico());
            campo.setOrdem(r.ordem());
            campo.setMapeamento(r.mapeamento());
            resultado.add(campoRepository.save(campo));
        }

        for (CampoPersonalizadoEvento existente : existentes) {
            if (!idsEnviados.contains(existente.getId())) {
                campoRepository.delete(existente);
            }
        }

        if (surgiuCampoObrigatorioNovo) {
            notificarInscritosSobrePendencia(evento, igrejaId, usuarioIdAtor);
        }

        return resultado.stream().map(CampoPersonalizadoResponse::from).toList();
    }

    /** {@code usuarioIdAtor} nunca recebe a própria notificação — quem editou já sabe. */
    private void notificarInscritosSobrePendencia(Evento evento, UUID igrejaId, UUID usuarioIdAtor) {
        List<UUID> pessoaIds = inscricaoRepository.findPessoaIdsByEventoIdAndStatus(
                evento.getId(), StatusInscricao.CONFIRMADA);
        String texto = "\"" + evento.getTitulo() + "\" tem uma pergunta nova pra você responder.";
        String link = "/eventos?detalhe=" + evento.getId();
        for (UUID pessoaId : pessoaIds) {
            usuarioRepository.findByPessoaId(pessoaId)
                    .filter(usuario -> !usuario.getId().equals(usuarioIdAtor))
                    .ifPresent(usuario ->
                            notificacaoService.criar(
                                    TipoNotificacao.CAMPO_PERSONALIZADO_PENDENTE,
                                    igrejaId, usuario.getId(), texto, link));
        }
    }

    /** Cada convidado tem sua própria inscrição agora (modelo unificado convidado/titular) —
     *  responde sempre pela {@code inscricaoId} dele mesmo, nunca mais por acompanhanteId.
     *  Valida obrigatoriedade aqui — nunca em inscrever(). */
    @Transactional
    public void responder(UUID inscricaoId,
                          List<com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest> respostas,
                          UUID igrejaId, UUID pessoaLogadaId, String role) {
        var inscricao = inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));

        boolean ehDono = (inscricao.getPessoa() != null
                        && java.util.Objects.equals(inscricao.getPessoa().getId(), pessoaLogadaId))
                || (inscricao.getConvidadoPor() != null
                        && java.util.Objects.equals(inscricao.getConvidadoPor().getId(), pessoaLogadaId));
        if (!ehDono && !com.domus.api.shared.security.Permissoes.podeGerenciarEventos(role)) {
            throw new com.domus.api.shared.exception.BusinessException(
                    "SEM_PERMISSAO", "Você não pode responder por essa inscrição.");
        }

        validarEResponder(inscricao, respostas, igrejaId);
    }

    /** Variante sem autor logado, usada só pelo fluxo de convite público (entrar sem conta): a
     *  posse do token — já validado antes de chegar aqui — É a autorização. Responde sempre
     *  como titular da inscrição recém-criada. */
    @Transactional
    public void responderComoConvidado(UUID inscricaoId,
                                        List<com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest> respostas,
                                        UUID igrejaId) {
        var inscricao = inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
        validarEResponder(inscricao, respostas, igrejaId);
    }

    private void validarEResponder(com.domus.api.modules.evento.inscricao.InscricaoEvento inscricao,
                                    List<com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest> respostas,
                                    UUID igrejaId) {
        List<CampoPersonalizadoEvento> campos = campoRepository
                .findByEventoIdAndIgrejaIdOrderByOrdemAsc(inscricao.getEvento().getId(), igrejaId);

        Map<UUID, String> valoresEnviados = new HashMap<>();
        for (var r : respostas) valoresEnviados.put(r.campoId(), r.valor());

        if (inscricao.getPessoa() != null) {
            for (CampoPersonalizadoEvento campo : campos) {
                if (campo.getMapeamento() == null || valoresEnviados.containsKey(campo.getId())) continue;
                valorJaConhecido(campo.getMapeamento(), inscricao.getPessoa())
                        .ifPresent(valor -> valoresEnviados.put(campo.getId(), valor));
            }
        }

        for (CampoPersonalizadoEvento campo : campos) {
            if (!campo.isObrigatorio()) continue;
            String valor = valoresEnviados.get(campo.getId());
            boolean respondidoAgora = valor != null && !valor.isBlank();
            boolean jaRespondidoAntes = !respondidoAgora && respostaRepository
                    .findByCampoIdAndInscricaoId(campo.getId(), inscricao.getId())
                    .map(r -> r.getValor() != null && !r.getValor().isBlank())
                    .orElse(false);
            if (!respondidoAgora && !jaRespondidoAntes) {
                throw new com.domus.api.shared.exception.BusinessException(
                        "CAMPO_OBRIGATORIO_PENDENTE", "\"" + campo.getLabel() + "\" é obrigatório.");
            }
        }

        for (var entry : valoresEnviados.entrySet()) {
            CampoPersonalizadoEvento campo = campos.stream()
                    .filter(c -> c.getId().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Campo não encontrado."));

            var existente = respostaRepository
                    .findByCampoIdAndInscricaoId(entry.getKey(), inscricao.getId());

            RespostaCampoPersonalizado resposta = existente.orElseGet(() ->
                    RespostaCampoPersonalizado.builder().campo(campo).inscricao(inscricao).build());
            resposta.setValor(entry.getValue());
            respostaRepository.save(resposta);
        }
    }

    /** Modal read-only do gestor. Devolve TODOS os campos ativos do evento — mesmo os que a
     *  pessoa nunca "respondeu" porque são mapeados (idade/estado civil/sexo/endereço) e o
     *  dado já estava no cadastro dela ({@code origem = CADASTRO}). Campos já arquivados pelo
     *  admin só aparecem se houver resposta persistida (snapshot). {@code @Transactional} pra
     *  ler {@code inscricao.getPessoa()}/{@code getEvento()} lazy dentro da sessão. */
    @Transactional(readOnly = true)
    public List<RespostaResponse> respostasPorInscricao(
            UUID inscricaoId, UUID igrejaId, UUID pessoaId, String role) {
        var inscricao = inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));

        boolean ehGestor = Permissoes.podeGerenciarInscricoes(role);
        boolean souEu = inscricao.getPessoa() != null
                && inscricao.getPessoa().getId().equals(pessoaId);
        if (!ehGestor && !souEu) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você não pode ver as respostas da inscrição de outra pessoa.");
        }

        Map<UUID, RespostaCampoPersonalizado> respostasPorCampo = new LinkedHashMap<>();
        for (var r : respostaRepository.findByInscricaoId(inscricaoId)) {
            respostasPorCampo.put(r.getCampo().getId(), r);
        }

        var pessoa = inscricao.getPessoa(); // lazy ok dentro do @Transactional
        var campos = campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(
                inscricao.getEvento().getId(), igrejaId);

        List<RespostaResponse> resultado = new ArrayList<>();
        Set<UUID> jaIncluidos = new HashSet<>();

        for (var campo : campos) {
            jaIncluidos.add(campo.getId());
            var resp = respostasPorCampo.get(campo.getId());
            if (resp != null && resp.getValor() != null && !resp.getValor().isBlank()) {
                resultado.add(new RespostaResponse(
                        campo.getId(), campo.getLabel(), campo.getTipo(), resp.getValor(),
                        OrigemResposta.RESPONDIDO));
                continue;
            }
            var doCadastro = valorJaConhecido(campo.getMapeamento(), pessoa);
            if (doCadastro.isPresent()) {
                resultado.add(new RespostaResponse(
                        campo.getId(), campo.getLabel(), campo.getTipo(), doCadastro.get(),
                        OrigemResposta.CADASTRO));
            } else {
                resultado.add(new RespostaResponse(
                        campo.getId(), campo.getLabel(), campo.getTipo(), null,
                        OrigemResposta.SEM_RESPOSTA));
            }
        }

        // A resposta guarda um snapshot da pergunta — o campo pode já ter sido arquivado/removido
        // pelo admin depois que a pessoa respondeu. Resolver via findByIdAndIgrejaIdIncluindoArquivados
        // (nativa, bypassa @SQLRestriction): acessar um campo arquivado pela associação lazy normal
        // estoura EntityNotFoundException.
        for (var r : respostasPorCampo.values()) {
            if (jaIncluidos.contains(r.getCampo().getId())) continue;
            campoRepository.findByIdAndIgrejaIdIncluindoArquivados(r.getCampo().getId(), igrejaId)
                    .ifPresent(campo -> resultado.add(
                            new RespostaResponse(
                                    campo.getId(), campo.getLabel(), campo.getTipo(), r.getValor(),
                                    OrigemResposta.RESPONDIDO)));
        }
        return resultado;
    }
}
