package com.domus.api.modules.membro;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.financeiro.movimentacao.busca.ReindexacaoMovimentacaoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.membro.DTO.EnderecoDTO;
import com.domus.api.modules.membro.DTO.MembroRequestDTO;
import com.domus.api.modules.membro.DTO.MembroResponse;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import com.domus.api.modules.usuario.*;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MembroService {

    private final MembroRepository membroRepository;
    private final IgrejaRepository igrejaRepository;
    private final UsuarioService  usuarioService;
    private final CacheEvictor cacheEvictor;
    private final OutboxRegistrador outboxRegistrador;
    private final ReindexacaoMovimentacaoService  reindexacaoMovimentacaoService;

    @Transactional(readOnly = true)
    public java.util.List<String> listarBairros(UUID igrejaId) {
        return membroRepository.bairrosDistintos(igrejaId);
    }

    @Cacheable(
            value = "membros",
            key = "T(com.domus.api.config.redis.CacheKeys).membros(#igrejaId, #q, #pageable)"
    )
    @Transactional(readOnly = true)
    public PagedResponse<MembroResponse> listarMembros(UUID igrejaId, String q, Pageable pageable) {
        Page<MembroResponse> pagina = membroRepository.buscarPorIgreja(igrejaId, q, pageable)
                .map(MembroResponse::from);
        return PagedResponse.from(pagina);
    }


    @Transactional
    public MembroResponse cadastrarMembro(MembroRequestDTO data, UUID igrejaId) {
        log.info("Iniciando cadastro de membro. nome={}, igreja_id={}", data.nome(), igrejaId);

        String email = normalizarEmail(data.email());

        if (email != null) {
            if (membroRepository.existsByEmail(email)) {
                throw new BusinessException("EMAIL_DUPLICADO", "E-mail já cadastrado no sistema.");
            }
            if (membroRepository.existsByEmailIncluindoArquivados(email)) {
                throw new BusinessException("EMAIL_ARQUIVADO",
                        "Este e-mail pertence a um cadastro arquivado. Use outro e-mail ou restaure o cadastro.");
            }
        }

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        Membro membro = Membro.builder()
                .igreja(igreja)
                .nome(data.nome())
                .email(email)
                .telefone(data.telefone())
                .dataNascimento(data.dataNascimento())
                .endereco(paraEndereco(data.endereco()))
                .status(data.status())
                .estadoCivil(data.estadoCivil())
                .ministerio(data.ministerio())
                .observacoes(data.observacoes())
                .build();

        Membro salvo = membroRepository.save(membro);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MEMBRO,
                TipoEventoOutbox.CRIADO,
                salvo.getId(),
                igrejaId
        );
        log.info("Membro cadastrado. id={}, Igreja_id={}", salvo.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("membros", igrejaId);

        return MembroResponse.from(salvo);
    }

    private String normalizarEmail(String email) {
        return (email == null || email.isBlank()) ? null : email.trim().toLowerCase();
    }

    @Transactional
    public MembroResponse atualizarMembro(UUID id, MembroRequestDTO data, UUID igrejaId) {
        log.info("Atualizando membro. id={}, igreja_id={}", id, igrejaId);
        Membro membro = membroRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));

        String nomeAntigo = membro.getNome();

        String emailNovo = normalizarEmail(data.email());
        if (emailNovo != null && !emailNovo.equals(membro.getEmail())) {

            if (membroRepository.existsByEmail(emailNovo)) {
                throw new BusinessException("EMAIL_DUPLICADO", "E-mail já cadastrado no sistema.");
            }
            if (membroRepository.existsByEmailIncluindoArquivados(emailNovo)) {
                throw new BusinessException("EMAIL_ARQUIVADO",
                        "Este e-mail pertence a um cadastro arquivado. Use outro e-mail.");
            }
        }

        membro.setNome(data.nome());
        membro.setEmail(emailNovo);
        membro.setTelefone(data.telefone());
        membro.setDataNascimento(data.dataNascimento());
        membro.setEndereco(paraEndereco(data.endereco()));
        membro.setStatus(data.status());
        membro.setEstadoCivil(data.estadoCivil());
        membro.setMinisterio(data.ministerio());
        membro.setObservacoes(data.observacoes());

        Membro salvo = membroRepository.save(membro);

        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MEMBRO,
                TipoEventoOutbox.ATUALIZADO,
                membro.getId(),
                igrejaId
        );

        boolean nomeMudou = !java.util.Objects.equals(nomeAntigo, membro.getNome());
        if (nomeMudou) {
            usuarioService.reindexarPorMembro(membro.getId(), igrejaId);
            reindexacaoMovimentacaoService.reindexarPorMembro(membro.getId(), igrejaId);
        }

        cacheEvictor.evictPorIgreja("membros", igrejaId);
        log.info("Membro atualizado. id={}, IgrejaId={}", salvo.getId(), igrejaId);
        return MembroResponse.from(salvo);
    }

    @Transactional
    public void arquivarMembro(UUID id, UUID igrejaId) {
        Membro membro = membroRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));

        usuarioService.arquivarPorMembro(membro.getId(), igrejaId);
        membroRepository.delete(membro);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MEMBRO,
                TipoEventoOutbox.REMOVIDO,
                membro.getId(),
                igrejaId
        );
        cacheEvictor.evictPorIgreja("membros", igrejaId);
    }

    @Transactional(readOnly = true)
    public MembroResponse buscarPorId(UUID id, UUID igrejaId) {
        Membro membro = membroRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));
        return MembroResponse.from(membro);
    }

    private Endereco paraEndereco(EnderecoDTO dto) {
        if (dto == null) return null;
        return Endereco.builder()
                .cep(dto.cep()).logradouro(dto.logradouro()).numero(dto.numero())
                .complemento(dto.complemento())
                .bairro(normalizar(dto.bairro()))
                .cidade(normalizar(dto.cidade()))
                .uf(dto.uf())
                .build();
    }

    static String normalizar(String v) {
        if (v == null) return null;
        String limpo = v.trim().replaceAll("\\s+", " ");
        if (limpo.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(limpo.length());
        for (String palavra : limpo.split(" ")) {
            sb.append(Character.toUpperCase(palavra.charAt(0)))
              .append(palavra.substring(1).toLowerCase())
              .append(' ');
        }
        return sb.toString().trim();
    }
}