package com.domus.api.modules.ministerio;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.ministerio.DTOs.MinisterioRequest;
import com.domus.api.modules.ministerio.DTOs.MinisterioResponse;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.util.TextoUtil;
import lombok.RequiredArgsConstructor;
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
}
