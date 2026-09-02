package com.domus.api.modules.ministerio;

import com.domus.api.modules.foto.Foto;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.igreja.GeneroGramatical;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest;
import com.domus.api.modules.ministerio.DTOs.AtualizarPapelRequest;
import com.domus.api.modules.ministerio.DTOs.MembroResponse;
import com.domus.api.modules.ministerio.DTOs.MinisterioDetalheResponse;
import com.domus.api.modules.ministerio.DTOs.MinisterioRequest;
import com.domus.api.modules.ministerio.DTOs.MinisterioResponse;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.util.TextoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinisterioService {

    /** Rótulo padrão pro texto de notificação — usado quando a igreja não customizou
     *  "Ministério" nas configurações (item 8 do backlog, self-service por igreja). Domínio,
     *  rotas e nomes de classe continuam "ministerio"; só a cópia visível troca. */
    private static final String ROTULO_MINISTERIO_PADRAO = "Rede";
    private static final GeneroGramatical GENERO_MINISTERIO_PADRAO = GeneroGramatical.FEMININO;

    private static String rotuloMinisterio(Igreja igreja) {
        String custom = igreja.getMinisterioNomeSingular();
        return custom != null ? custom : ROTULO_MINISTERIO_PADRAO;
    }

    private static GeneroGramatical generoMinisterio(Igreja igreja) {
        GeneroGramatical custom = igreja.getMinisterioGenero();
        return custom != null ? custom : GENERO_MINISTERIO_PADRAO;
    }

    /** {@code feminina}/{@code masculina} escolhidos conforme o gênero customizado do
     *  rótulo de Ministério na igreja (ex.: preposição "à"/"ao", "da"/"do", "na"/"no"). */
    private static String prep(Igreja igreja, String feminina, String masculina) {
        return generoMinisterio(igreja) == GeneroGramatical.FEMININO ? feminina : masculina;
    }

    /** Rótulo + nome, sem duplicar quando quem cadastrou já incluiu o rótulo no próprio nome
     *  (ex.: nome = "Rede de Louvor" viraria "Rede Rede de Louvor" sem esta checagem). */
    private static String comRotulo(Ministerio ministerio) {
        return TextoUtil.prefixarSemDuplicar(rotuloMinisterio(ministerio.getIgreja()), ministerio.getNome());
    }

    private final MinisterioRepository ministerioRepository;
    private final MinisterioMembroRepository membroRepository;
    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final FotoService fotoService;
    private final OutboxRegistrador outboxRegistrador;
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;

    @Transactional(readOnly = true)
    public List<MinisterioResponse> listar(UUID igrejaId, UUID pessoaLogadaId) {
        // N+1 deliberado: uma igreja tem dezenas de ministérios, não milhares — uma query de
        // membros por ministério na tela de listagem é aceitável (YAGNI evita otimizar cedo
        // demais). Se a lista crescer muito, trocar por uma query agregada única.
        return ministerioRepository.findByIgrejaIdOrderByNomeAsc(igrejaId).stream()
                .map(m -> MinisterioResponse.comResumo(m, membrosAtivosDe(m.getId()), pessoaLogadaId))
                .toList();
    }

    private List<MinisterioMembro> membrosAtivosDe(UUID ministerioId) {
        return membroRepository.findByMinisterioIdOrderByPapelAsc(ministerioId).stream()
                .filter(m -> m.getStatus() == StatusMembro.ATIVO)
                .toList();
    }

    @Transactional
    public MinisterioResponse criar(MinisterioRequest data, UUID igrejaId, UUID usuarioId) {
        String nome = TextoUtil.capitalizar(data.nome());
        validarNaoDuplicado(nome, igrejaId, null);

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));
        Usuario usuario = usuarioId != null ? usuarioRepository.findById(usuarioId).orElse(null) : null;

        Foto foto = fotoService.buscarParaVincular(data.fotoId(), igrejaId);

        Ministerio ministerio = Ministerio.builder()
                .igreja(igreja)
                .nome(nome)
                .foto(foto)
                .criadoPor(usuario)
                .atualizadoPor(usuario)
                .build();

        Ministerio salvo = ministerioRepository.save(ministerio);
        outboxRegistrador.registrar(TipoEntidadeOutbox.MINISTERIO, TipoEventoOutbox.CRIADO, salvo.getId(), igrejaId);
        return MinisterioResponse.from(salvo);
    }

    @Transactional
    public MinisterioResponse atualizar(UUID id, MinisterioRequest data, UUID igrejaId, UUID usuarioId,
                                        UUID atorPessoaId, boolean isAdmin) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(id, igrejaId);
        exigirAdminOuLider(id, atorPessoaId, isAdmin);

        String nome = TextoUtil.capitalizar(data.nome());
        validarNaoDuplicado(nome, igrejaId, id);

        ministerio.setNome(nome);
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(ministerio::setAtualizadoPor);
        }

        Foto fotoAntiga = ministerio.getFoto();
        Foto fotoNova = fotoService.buscarParaVincular(data.fotoId(), igrejaId);
        ministerio.setFoto(fotoNova);
        MinisterioResponse response = MinisterioResponse.from(ministerioRepository.save(ministerio));
        outboxRegistrador.registrar(TipoEntidadeOutbox.MINISTERIO, TipoEventoOutbox.ATUALIZADO, ministerio.getId(), igrejaId);
        if (!Objects.equals(fotoAntiga != null ? fotoAntiga.getId() : null, fotoNova != null ? fotoNova.getId() : null) && fotoAntiga != null) {
            fotoService.remover(fotoAntiga.getId());
        }
        return response;
    }

    /** Só a foto — salva assim que o recorte é confirmado, sem esperar o resto do "Salvar". */
    @Transactional
    public void atualizarFoto(UUID id, UUID igrejaId, UUID usuarioId, UUID atorPessoaId, boolean isAdmin, UUID fotoId) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(id, igrejaId);
        exigirAdminOuLider(id, atorPessoaId, isAdmin);

        Foto fotoAntiga = ministerio.getFoto();
        Foto fotoNova = fotoService.buscarParaVincular(fotoId, igrejaId);
        ministerio.setFoto(fotoNova);
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(ministerio::setAtualizadoPor);
        }
        ministerioRepository.save(ministerio);

        if (!Objects.equals(fotoAntiga != null ? fotoAntiga.getId() : null, fotoNova != null ? fotoNova.getId() : null)
                && fotoAntiga != null) {
            fotoService.remover(fotoAntiga.getId());
        }
    }

    @Transactional
    public void arquivar(UUID id, UUID igrejaId) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(id, igrejaId);
        ministerioRepository.delete(ministerio);
        outboxRegistrador.registrar(TipoEntidadeOutbox.MINISTERIO, TipoEventoOutbox.REMOVIDO, id, igrejaId);
    }

    @Transactional(readOnly = true)
    public List<MinisterioResponse> listarArquivadas(UUID igrejaId) {
        return ministerioRepository.findArquivadasPorIgreja(igrejaId).stream()
                .map(m -> MinisterioResponse.comResumo(m, membrosAtivosDe(m.getId()), null))
                .toList();
    }

    @Transactional
    public void restaurar(UUID id, UUID igrejaId) {
        int linhas = ministerioRepository.restaurarPorId(id, igrejaId);
        if (linhas == 0) {
            throw new ResourceNotFoundException("Ministério não encontrado.");
        }
        outboxRegistrador.registrar(TipoEntidadeOutbox.MINISTERIO, TipoEventoOutbox.ATUALIZADO, id, igrejaId);
    }

    @Transactional
    public void excluirDefinitivo(UUID id, UUID igrejaId) {
        // Inclui arquivados: chamado a partir da tela de Arquivados.
        ministerioRepository.findByIdAndIgrejaIdIncluindoArquivadas(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Ministério não encontrado."));

        // Vínculo é só "pessoa está no grupo", não histórico — ter membro não bloqueia a exclusão.
        List<MinisterioMembro> membros = membroRepository.findByMinisterioIdOrderByPapelAsc(id);
        membroRepository.deleteAll(membros);
        // hardDeleteById é SQL nativo — precisa do delete acima já refletido no banco
        // antes de rodar (mesmo motivo do fix em CelulaService.excluirDefinitivo).
        membroRepository.flush();

        ministerioRepository.hardDeleteById(id);
        outboxRegistrador.registrar(TipoEntidadeOutbox.MINISTERIO, TipoEventoOutbox.REMOVIDO, id, igrejaId);
    }

    Ministerio buscarDaIgrejaOuFalhar(UUID id, UUID igrejaId) {
        return ministerioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Ministério não encontrado."));
    }

    private void validarNaoDuplicado(String nome, UUID igrejaId, UUID ignorarId) {
        String normalizado = TextoUtil.normalizarParaComparacao(nome);
        boolean duplicado = ministerioRepository.findByIgrejaIdOrderByNomeAsc(igrejaId).stream()
                .filter(m -> ignorarId == null || !m.getId().equals(ignorarId))
                .anyMatch(m -> TextoUtil.normalizarParaComparacao(m.getNome()).equals(normalizado));
        if (duplicado) {
            throw new BusinessException("MINISTERIO_DUPLICADO", "Já existe um ministério com esse nome.");
        }
    }

    @Transactional(readOnly = true)
    public MinisterioDetalheResponse detalhe(UUID ministerioId, UUID igrejaId, UUID pessoaLogadaId, boolean isAdmin) {
        // Precisa enxergar arquivado também — dá pra abrir o detalhe de um ministério
        // arquivado a partir da tela de Arquivados, igual um ministério ativo.
        Ministerio ministerio = ministerioRepository.findByIdAndIgrejaIdIncluindoArquivadas(ministerioId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Ministério não encontrado."));
        boolean souLider = isAdmin || (pessoaLogadaId != null && ehLiderDoMinisterio(ministerioId, pessoaLogadaId));

        List<MinisterioMembro> todos = membroRepository.findByMinisterioIdOrderByPapelAsc(ministerioId);
        List<MembroResponse> membros = todos.stream()
                .filter(m -> m.getStatus() == StatusMembro.ATIVO)
                .map(MembroResponse::from)
                .toList();
        List<MembroResponse> pedidosPendentes = souLider
                ? todos.stream()
                        .filter(m -> m.getStatus() == StatusMembro.PENDENTE)
                        .map(MembroResponse::from)
                        .toList()
                : List.of();

        // O front não guarda pessoaId no authStore (só usuarioId/role) — calcula aqui pra
        // decidir "pedir para entrar" vs "pedido enviado" sem o front precisar saber quem é.
        boolean souMembroAtivo = pessoaLogadaId != null && todos.stream()
                .anyMatch(m -> m.getPessoa().getId().equals(pessoaLogadaId) && m.getStatus() == StatusMembro.ATIVO);
        boolean tenhoPedidoPendente = pessoaLogadaId != null && todos.stream()
                .anyMatch(m -> m.getPessoa().getId().equals(pessoaLogadaId) && m.getStatus() == StatusMembro.PENDENTE);

        return MinisterioDetalheResponse.from(
                ministerio, membros, pedidosPendentes, souLider, souMembroAtivo, tenhoPedidoPendente);
    }

    public boolean ehLiderDoMinisterio(UUID ministerioId, UUID pessoaId) {
        return membroRepository.existsByMinisterioIdAndPessoaIdAndPapelAndStatus(
                ministerioId, pessoaId, Papel.LIDER.name(), StatusMembro.ATIVO.name());
    }

    private void exigirAdminOuLider(UUID ministerioId, UUID atorPessoaId, boolean isAdmin) {
        if (isAdmin) return;
        if (atorPessoaId == null || !ehLiderDoMinisterio(ministerioId, atorPessoaId)) {
            throw new AccessDeniedException("Só o líder deste ministério ou um administrador pode fazer isso.");
        }
    }

    @Transactional
    public void adicionarMembro(UUID ministerioId, AdicionarMembroRequest data, UUID igrejaId,
                                 UUID atorPessoaId, boolean isAdmin, UUID usuarioId) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
        exigirAdminOuLider(ministerioId, atorPessoaId, isAdmin);

        if (membroRepository.findByMinisterioIdAndPessoaId(ministerioId, data.pessoaId()).isPresent()) {
            throw new BusinessException("MEMBRO_JA_VINCULADO", "Essa pessoa já está vinculada a este ministério.");
        }

        Pessoa pessoa = pessoaRepository.findByIdAndIgrejaId(data.pessoaId(), igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));
        Usuario usuario = usuarioId != null ? usuarioRepository.findById(usuarioId).orElse(null) : null;

        membroRepository.save(MinisterioMembro.builder()
                .igreja(ministerio.getIgreja())
                .ministerio(ministerio)
                .pessoa(pessoa)
                .papel(Papel.MEMBRO)
                .status(StatusMembro.ATIVO)
                .criadoPor(usuario)
                .atualizadoPor(usuario)
                .build());

        notificarMembroDoMinisterio(ministerio, igrejaId, data.pessoaId(), atorPessoaId,
                com.domus.api.modules.notificacao.TipoNotificacao.ADICIONADO_MINISTERIO,
                "Você foi adicionado " + prep(ministerio.getIgreja(), "à", "ao") + " " + comRotulo(ministerio) + ".");
    }

    @Transactional
    public void removerMembro(UUID ministerioId, UUID pessoaId, UUID igrejaId, UUID atorPessoaId, boolean isAdmin) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
        exigirAdminOuLider(ministerioId, atorPessoaId, isAdmin);

        MinisterioMembro membro = membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Vínculo não encontrado."));
        membroRepository.delete(membro);

        notificarMembroDoMinisterio(ministerio, igrejaId, pessoaId, atorPessoaId,
                com.domus.api.modules.notificacao.TipoNotificacao.REMOVIDO_MINISTERIO,
                "Você foi removido " + prep(ministerio.getIgreja(), "da", "do") + " " + comRotulo(ministerio) + ".");
    }

    /** Notifica a própria pessoa afetada (adicionada/removida/aceita) — nunca quando ela mesma agiu. */
    private void notificarMembroDoMinisterio(Ministerio ministerio, UUID igrejaId, UUID pessoaId, UUID pessoaIdAtor,
                                              com.domus.api.modules.notificacao.TipoNotificacao tipo, String texto) {
        if (pessoaId.equals(pessoaIdAtor)) return;
        usuarioRepository.findByPessoaId(pessoaId)
                .ifPresent(usuario -> notificacaoService.criar(
                        tipo, igrejaId, usuario.getId(), texto, "/ministerios/" + ministerio.getId()));
    }

    @Transactional
    public void atualizarPapel(UUID ministerioId, UUID pessoaId, AtualizarPapelRequest data, UUID igrejaId,
                                UUID atorPessoaId, boolean isAdmin) {
        buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
        exigirAdminOuLider(ministerioId, atorPessoaId, isAdmin);

        MinisterioMembro membro = membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Vínculo não encontrado."));
        if (membro.getStatus() != StatusMembro.ATIVO) {
            throw new BusinessException("MEMBRO_NAO_ATIVO", "A pessoa precisa ser membro ativo antes de virar líder.");
        }
        membro.setPapel(data.papel());
        membroRepository.save(membro);
    }

    @Transactional
    public void pedirEntrada(UUID ministerioId, UUID pessoaId, UUID igrejaId) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(ministerioId, igrejaId);

        if (membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaId).isPresent()) {
            throw new BusinessException("PEDIDO_JA_EXISTE", "Você já está vinculado ou já tem um pedido pendente neste ministério.");
        }

        Pessoa pessoa = pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));

        membroRepository.save(MinisterioMembro.builder()
                .igreja(ministerio.getIgreja())
                .ministerio(ministerio)
                .pessoa(pessoa)
                .papel(Papel.MEMBRO)
                .status(StatusMembro.PENDENTE)
                .build());

        List<MinisterioMembro> lideres = membroRepository.findByMinisterioIdAndPapelAndStatus(
                ministerioId, Papel.LIDER.name(), StatusMembro.ATIVO.name());
        for (MinisterioMembro lider : lideres) {
            usuarioRepository.findByPessoaId(lider.getPessoa().getId()).ifPresent(usuario ->
                    notificacaoService.criar(
                            com.domus.api.modules.notificacao.TipoNotificacao.PEDIDO_MINISTERIO,
                            igrejaId, usuario.getId(),
                            pessoa.getNome() + " pediu pra entrar " + prep(ministerio.getIgreja(), "na", "no") + " " + comRotulo(ministerio) + ".",
                            "/ministerios/" + ministerioId));
        }
    }

    @Transactional
    public void aceitarPedido(UUID ministerioId, UUID pessoaId, UUID igrejaId,
                               UUID atorPessoaId, boolean isAdmin, UUID usuarioId) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
        exigirAdminOuLider(ministerioId, atorPessoaId, isAdmin);

        MinisterioMembro membro = membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        membro.setStatus(StatusMembro.ATIVO);
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(membro::setAtualizadoPor);
        }
        membroRepository.save(membro);

        notificarMembroDoMinisterio(ministerio, igrejaId, pessoaId, atorPessoaId,
                com.domus.api.modules.notificacao.TipoNotificacao.ADICIONADO_MINISTERIO,
                "Seu pedido para entrar " + prep(ministerio.getIgreja(), "na", "no") + " " + comRotulo(ministerio) + " foi aceito.");
    }

    @Transactional
    public void recusarPedido(UUID ministerioId, UUID pessoaId, UUID igrejaId, UUID atorPessoaId, boolean isAdmin) {
        buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
        exigirAdminOuLider(ministerioId, atorPessoaId, isAdmin);

        MinisterioMembro membro = membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        membroRepository.delete(membro);
    }

    @Transactional(readOnly = true)
    public List<MinisterioResponse> listarMinisteriosDaPessoa(UUID pessoaId, UUID igrejaId) {
        return membroRepository.findByPessoaIdAndIgrejaIdAndStatus(pessoaId, igrejaId, StatusMembro.ATIVO).stream()
                .map(m -> MinisterioResponse.from(m.getMinisterio()))
                .toList();
    }
}
