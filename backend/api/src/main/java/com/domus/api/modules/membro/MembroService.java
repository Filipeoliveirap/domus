package com.domus.api.modules.membro;

import com.domus.api.config.redis.CacheEvictor;
<<<<<<< HEAD
=======
import com.domus.api.modules.financeiro.movimentacao.busca.ReindexacaoMovimentacaoService;
>>>>>>> develop
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.membro.DTO.MembroRequestDTO;
import com.domus.api.modules.membro.DTO.MembroResponse;
<<<<<<< HEAD
import com.domus.api.modules.usuario.*;
import com.domus.api.shared.PagedResponse;
=======
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import com.domus.api.modules.usuario.*;
import com.domus.api.shared.DTO.PagedResponse;
>>>>>>> develop
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
<<<<<<< HEAD
=======
    private final OutboxRegistrador outboxRegistrador;
    private final ReindexacaoMovimentacaoService  reindexacaoMovimentacaoService;
>>>>>>> develop

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

<<<<<<< HEAD
        if (email != null && membroRepository.existsByEmail(email)) {
            log.warn("E-mail de membro já cadastrado. email={}", email);
            throw new BusinessException("EMAIL_DUPLICADO", "E-mail já cadastrado no sistema.");
=======
        if (email != null) {
            if (membroRepository.existsByEmail(email)) {
                throw new BusinessException("EMAIL_DUPLICADO", "E-mail já cadastrado no sistema.");
            }
            if (membroRepository.existsByEmailIncluindoArquivados(email)) {
                throw new BusinessException("EMAIL_ARQUIVADO",
                        "Este e-mail pertence a um cadastro arquivado. Use outro e-mail ou restaure o cadastro.");
            }
>>>>>>> develop
        }

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        Membro membro = Membro.builder()
                .igreja(igreja)
                .nome(data.nome())
                .email(email)
                .telefone(data.telefone())
                .dataNascimento(data.dataNascimento())
                .endereco(data.endereco())
                .status(data.status())
                .estadoCivil(data.estadoCivil())
                .ministerio(data.ministerio())
                .observacoes(data.observacoes())
                .build();

        Membro salvo = membroRepository.save(membro);
<<<<<<< HEAD
=======
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MEMBRO,
                TipoEventoOutbox.CRIADO,
                salvo.getId(),
                igrejaId
        );
>>>>>>> develop
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
<<<<<<< HEAD

        Membro membro = membroRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));

        String emailNovo = normalizarEmail(data.email());

        if (emailNovo != null && !emailNovo.equals(membro.getEmail())
                && membroRepository.existsByEmail(emailNovo)) {
            throw new BusinessException("EMAIL_DUPLICADO", "E-mail já cadastrado no sistema.");
=======
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
>>>>>>> develop
        }

        membro.setNome(data.nome());
        membro.setEmail(emailNovo);
        membro.setTelefone(data.telefone());
        membro.setDataNascimento(data.dataNascimento());
        membro.setEndereco(data.endereco());
        membro.setStatus(data.status());
        membro.setEstadoCivil(data.estadoCivil());
        membro.setMinisterio(data.ministerio());
        membro.setObservacoes(data.observacoes());

        Membro salvo = membroRepository.save(membro);
<<<<<<< HEAD
        cacheEvictor.evictPorIgreja("membros", igrejaId);

=======

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
>>>>>>> develop
        log.info("Membro atualizado. id={}, IgrejaId={}", salvo.getId(), igrejaId);
        return MembroResponse.from(salvo);
    }

    @Transactional
    public void arquivarMembro(UUID id, UUID igrejaId) {
        Membro membro = membroRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));

        usuarioService.arquivarPorMembro(membro.getId(), igrejaId);
        membroRepository.delete(membro);
<<<<<<< HEAD
=======
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MEMBRO,
                TipoEventoOutbox.REMOVIDO,
                membro.getId(),
                igrejaId
        );
>>>>>>> develop
        cacheEvictor.evictPorIgreja("membros", igrejaId);
    }

    @Transactional(readOnly = true)
    public MembroResponse buscarPorId(UUID id, UUID igrejaId) {
        Membro membro = membroRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));
        return MembroResponse.from(membro);
    }


}