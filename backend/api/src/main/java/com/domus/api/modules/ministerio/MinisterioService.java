package com.domus.api.modules.ministerio;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinisterioService {

    private final MinisterioRepository ministerioRepository;
    private final MinisterioMembroRepository membroRepository;
    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;

    @Transactional(readOnly = true)
    public List<MinisterioResponse> listar(UUID igrejaId) {
        // N+1 deliberado: uma igreja tem dezenas de ministérios, não milhares — uma query de
        // membros por ministério na tela de listagem é aceitável (YAGNI evita otimizar cedo
        // demais). Se a lista crescer muito, trocar por uma query agregada única.
        return ministerioRepository.findByIgrejaIdOrderByNomeAsc(igrejaId).stream()
                .map(m -> MinisterioResponse.comResumo(m, membrosAtivosDe(m.getId())))
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

        Ministerio ministerio = Ministerio.builder()
                .igreja(igreja)
                .nome(nome)
                .criadoPor(usuario)
                .atualizadoPor(usuario)
                .build();

        return MinisterioResponse.from(ministerioRepository.save(ministerio));
    }

    @Transactional
    public MinisterioResponse atualizar(UUID id, MinisterioRequest data, UUID igrejaId, UUID usuarioId) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(id, igrejaId);

        String nome = TextoUtil.capitalizar(data.nome());
        validarNaoDuplicado(nome, igrejaId, id);

        ministerio.setNome(nome);
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(ministerio::setAtualizadoPor);
        }

        return MinisterioResponse.from(ministerioRepository.save(ministerio));
    }

    @Transactional
    public void arquivar(UUID id, UUID igrejaId) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(id, igrejaId);
        ministerioRepository.delete(ministerio);
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
        Ministerio ministerio = buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
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
                ministerioId, pessoaId, Papel.LIDER, StatusMembro.ATIVO);
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
    }

    @Transactional
    public void removerMembro(UUID ministerioId, UUID pessoaId, UUID igrejaId, UUID atorPessoaId, boolean isAdmin) {
        buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
        exigirAdminOuLider(ministerioId, atorPessoaId, isAdmin);

        MinisterioMembro membro = membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Vínculo não encontrado."));
        membroRepository.delete(membro);
    }

    @Transactional
    public void atualizarPapel(UUID ministerioId, UUID pessoaId, AtualizarPapelRequest data, UUID igrejaId) {
        buscarDaIgrejaOuFalhar(ministerioId, igrejaId);

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
    }

    @Transactional
    public void aceitarPedido(UUID ministerioId, UUID pessoaId, UUID igrejaId,
                               UUID atorPessoaId, boolean isAdmin, UUID usuarioId) {
        buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
        exigirAdminOuLider(ministerioId, atorPessoaId, isAdmin);

        MinisterioMembro membro = membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        membro.setStatus(StatusMembro.ATIVO);
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(membro::setAtualizadoPor);
        }
        membroRepository.save(membro);
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
