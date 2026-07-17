package com.domus.api.modules.usuario;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.membro.DTO.ConcederAcessoRequestDTO;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.usuario.DTO.UsuarioResponseDTO;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import java.util.UUID;
import com.domus.api.shared.DTO.PagedResponse;

@Service
@Slf4j
@RequiredArgsConstructor
public class UsuarioService {


    private final UsuarioRepository usuarioRepository;
    private final IgrejaRepository igrejaRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private static final String ROLE_ADMIN = "ADMIN_IGREJA";
    private final MembroRepository membroRepository;
    private final CacheEvictor cacheEvictor;
    private final OutboxRegistrador outboxRegistrador;

    @Transactional
    public UsuarioResponseDTO concederAcesso(ConcederAcessoRequestDTO data, UUID igrejaId) {
        log.info("Concedendo acesso a membro. membroId={}, igreja_id={}", data.membroId(), igrejaId);

        Membro membro = membroRepository.findByIdAndIgrejaId(data.membroId(), igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));

        if (membro.getEmail() == null || membro.getEmail().isBlank()) {
            throw new BusinessException("MEMBRO_SEM_EMAIL",
                    "O membro precisa ter um e-mail para receber acesso ao sistema.");
        }

        Role role = roleRepository.findByNome(data.role())
                .orElseThrow(() -> new BusinessException("Perfil inválido"));

        Usuario existente = usuarioRepository
                .findByMembroIdIncluindoArquivados(membro.getId())
                .orElse(null);

        if (existente != null) {
            if (existente.getDeleteAt() == null) {
                throw new BusinessException("MEMBRO_JA_TEM_ACESSO",
                        "Este membro já possui acesso ao sistema.");
            }
            throw new BusinessException("MEMBRO_TEM_USUARIO_ARQUIVADO",
                    "Este membro já teve acesso, que foi arquivado. Deseja reativar?");
        }

        Usuario usuario = Usuario.builder()
                .igreja(membro.getIgreja())
                .membro(membro)
                .senhaHash(passwordEncoder.encode(data.senha()))
                .ativo(true)
                .role(role)
                .build();

        Usuario salvo = usuarioRepository.save(usuario);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.USUARIO,
                TipoEventoOutbox.CRIADO,
                salvo.getId(),
                igrejaId
        );
        log.info("Acesso concedido (novo usuário). usuario_id={}, membro_id={}, igreja_id={}", salvo.getId(), membro.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("usuarios", igrejaId);
        return UsuarioResponseDTO.from(salvo);
    }

    @Transactional
    public UsuarioResponseDTO reativarAcesso(ConcederAcessoRequestDTO data, UUID igrejaId) {
        log.info("Reativando acesso de membro. membroId={}, igreja_id={}", data.membroId(), igrejaId);
        Membro membro = membroRepository.findByIdAndIgrejaId(data.membroId(), igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));

        Usuario arquivado = usuarioRepository.findByMembroIdIncluindoArquivados(membro.getId())
                .filter(u -> u.getDeleteAt() != null)
                .orElseThrow(() -> new BusinessException("Nenhum acesso arquivado encontrado."));

        Role role = roleRepository.findByNome(data.role())
                .orElseThrow(() -> new BusinessException("Perfil inválido"));

        arquivado.setDeleteAt(null);
        arquivado.setAtivo(true);
        arquivado.setSenhaHash(passwordEncoder.encode(data.senha()));
        arquivado.setRole(role);

        Usuario salvo = usuarioRepository.save(arquivado);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.USUARIO,
                TipoEventoOutbox.ATUALIZADO,
                salvo.getId(),
                igrejaId
        );
        log.info("Acesso reativado. usuario_id={}, membro_id={}, igrejaId={}", salvo.getId(), membro.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("usuarios", igrejaId);
        return UsuarioResponseDTO.from(salvo);
    }

    @Cacheable(
            value = "usuarios",
            key = "T(com.domus.api.config.redis.CacheKeys).usuarios(#igrejaId, #q, #pageable)"
    )

    @Transactional(readOnly = true)
    public PagedResponse<UsuarioResponseDTO> listar(UUID igrejaId, String q, Pageable pageable) {
        Page<UsuarioResponseDTO> pagina = usuarioRepository.buscarPorIgreja(igrejaId, q, pageable)
                .map(UsuarioResponseDTO::from);
        return PagedResponse.from(pagina);
    }


    private void garantirNaoEhUltimoAdmin(Usuario usuario, UUID igrejaId) {
        boolean eAdminAtivo = ROLE_ADMIN.equals(usuario.getRole().getNome()) && usuario.isAtivo();
        if (!eAdminAtivo) return;

        igrejaRepository.buscarComLock(igrejaId);

        long adminsAtivos = usuarioRepository.countByIgrejaIdAndRole_NomeAndAtivoTrue(igrejaId, ROLE_ADMIN);
        if (adminsAtivos <= 1) {
            log.warn("Bloqueada remoção do último admin. usuario_id={}, igreja_id={}", usuario.getId(), igrejaId);
            throw new BusinessException("ULTIMO_ADMIN",
                    "A igreja precisa ter pelo menos um administrador ativo.");
        }
    }


    @Transactional
    public UsuarioResponseDTO updateStatus(UUID id, boolean ativo, UUID igrejaId) {
        log.info("Alterando status de usuário. id={}, ativo={}, igreja_id={}", id, ativo, igrejaId);
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario não encontrado."));
        if (!ativo) {
            garantirNaoEhUltimoAdmin(usuario, igrejaId);
        }

        usuario.setAtivo(ativo);
        Usuario salvo = usuarioRepository.save(usuario);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.USUARIO,
                TipoEventoOutbox.ATUALIZADO,
                salvo.getId(),
                igrejaId
        );
        log.info("status de usuário alterado. id={}, ativo={}, igreja_id={}", id, ativo, igrejaId);
        cacheEvictor.evictPorIgreja("usuarios", igrejaId);
        return UsuarioResponseDTO.from(salvo);
    }

    @Transactional
    public UsuarioResponseDTO updateRole(UUID id, String roleName, UUID igrejaId) {
        log.info("Alterando role de usuário. id={}, role={}, igreja_id={}", id, roleName, igrejaId);
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario não encontrado."));

        Role role = roleRepository.findByNome(roleName)
                .orElseThrow(() -> new BusinessException("Perfil inválido"));

        if (!ROLE_ADMIN.equals(role.getNome())) {
            garantirNaoEhUltimoAdmin(usuario, igrejaId);
        }

        usuario.setRole(role);
        Usuario salvo = usuarioRepository.save(usuario);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.USUARIO,
                TipoEventoOutbox.ATUALIZADO,
                salvo.getId(),
                igrejaId
        );
        log.info("role de usuário alterado. id={}, role={}, igreja_id={}", id, roleName, igrejaId);
        cacheEvictor.evictPorIgreja("usuarios", igrejaId);
        return UsuarioResponseDTO.from(salvo);
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscarPorId(UUID id, UUID igrejaId) {
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario não encontrado."));
        return UsuarioResponseDTO.from(usuario);
    }

    @Transactional
    public void arquivarUsuario(UUID id, UUID igrejaId) {
        log.info("Arquivando usuário. id={}, igreja_id={}", id, igrejaId);
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario não encontrado."));
        garantirNaoEhUltimoAdmin(usuario, igrejaId);

        usuarioRepository.delete(usuario);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.USUARIO,
                TipoEventoOutbox.REMOVIDO,
                usuario.getId(),
                igrejaId
        );
        log.info("Usuário arquivado. id={}, igreja_id={}", id, igrejaId);
        cacheEvictor.evictPorIgreja("usuarios", igrejaId);
    }

    @Transactional
    public void arquivarPorMembro(UUID membroId, UUID igrejaId) {
        usuarioRepository.findByMembroId(membroId).ifPresent(usuario -> {
            log.info("Arquivando usuário em cascata (membro arquivado). usuario_id={}, membro_id={}, igrejaId={}", usuario.getId(), membroId, igrejaId);
            usuarioRepository.delete(usuario);
            outboxRegistrador.registrar(
                    TipoEntidadeOutbox.USUARIO,
                    TipoEventoOutbox.REMOVIDO,
                    usuario.getId(),
                    igrejaId
            );
            cacheEvictor.evictPorIgreja("usuarios", igrejaId);
        });
    }

    @Transactional
    public void reindexarPorMembro(UUID membroId, UUID igrejaId) {
        usuarioRepository.findByMembroId(membroId).ifPresent(usuario -> {
            log.debug("Reindexando usuário por alteração no membro. usuario_id={}, membro_id={}", usuario.getId(), membroId);
            outboxRegistrador.registrar(
                    TipoEntidadeOutbox.USUARIO,
                    TipoEventoOutbox.ATUALIZADO,
                    usuario.getId(),
                    igrejaId
            );
        });
    }


}
