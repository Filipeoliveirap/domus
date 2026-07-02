package com.domus.api.modules.usuario;

import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.membro.DTO.ConcederAcessoRequestDTO;
import com.domus.api.modules.usuario.DTO.UsuarioResponseDTO;
import com.domus.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.domus.api.shared.PagedResponse;

@Service
@Slf4j
@RequiredArgsConstructor
public class UsuarioService {


    private final UsuarioRepository usuarioRepository;
    private final IgrejaRepository igrejaRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate  redisTemplate;
    private static final String ROLE_ADMIN = "ADMIN_IGREJA";
    private final MembroRepository membroRepository;

    @Transactional
    public UsuarioResponseDTO concederAcesso(ConcederAcessoRequestDTO data, UUID igrejaId) {
        log.info("Concedendo acesso a membro. membroId={}, igreja_id={}", data.membroId(), igrejaId);

        Membro membro = membroRepository.findByIdAndIgrejaId(data.membroId(), igrejaId)
                .orElseThrow(() -> new BusinessException("Membro não encontrado."));

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
        log.info("Acesso concedido (novo usuário). usuario_id={}, membro_id={}", salvo.getId(), membro.getId());
        evictCacheUsuarios(igrejaId);
        return UsuarioResponseDTO.from(salvo);
    }

    @Transactional
    public UsuarioResponseDTO reativarAcesso(ConcederAcessoRequestDTO data, UUID igrejaId) {
        Membro membro = membroRepository.findByIdAndIgrejaId(data.membroId(), igrejaId)
                .orElseThrow(() -> new BusinessException("Membro não encontrado."));

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
        evictCacheUsuarios(igrejaId);
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

    private void evictCacheUsuarios(UUID igrejaId) {
        try {
            String pattern = "usuarios::" + igrejaId + ":*";   // ← usuarios, não membros
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                List<String> keys = new ArrayList<>();
                cursor.forEachRemaining(keys::add);
                if (!keys.isEmpty()) redisTemplate.delete(keys);
            }
        } catch (RuntimeException ex) {
            log.warn("Falha ao invalidar cache de usuários. igreja_id={}", igrejaId, ex);
        }
    }

    private void garantirNaoEhUltimoAdmin(Usuario usuario, UUID igrejaId) {
        boolean eAdminAtivo = ROLE_ADMIN.equals(usuario.getRole().getNome()) && usuario.isAtivo();
        if (!eAdminAtivo) return;

        igrejaRepository.buscarComLock(igrejaId);

        long adminsAtivos = usuarioRepository.countByIgrejaIdAndRole_NomeAndAtivoTrue(igrejaId, ROLE_ADMIN);
        if (adminsAtivos <= 1) {
            throw new BusinessException("ULTIMO_ADMIN",
                    "A igreja precisa ter pelo menos um administrador ativo.");
        }
    }


    @Transactional
    public UsuarioResponseDTO updateStatus(UUID id, boolean ativo, UUID igrejaId) {
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado."));
        if (!ativo) {
            garantirNaoEhUltimoAdmin(usuario, igrejaId);
        }

        usuario.setAtivo(ativo);
        Usuario salvo = usuarioRepository.save(usuario);
        evictCacheUsuarios(igrejaId);
        return UsuarioResponseDTO.from(salvo);
    }

    @Transactional
    public UsuarioResponseDTO updateRole(UUID id, String roleName, UUID igrejaId) {
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado."));

        Role role = roleRepository.findByNome(roleName)
                .orElseThrow(() -> new BusinessException("Perfil inválido"));

        if (!ROLE_ADMIN.equals(role.getNome())) {
            garantirNaoEhUltimoAdmin(usuario, igrejaId);
        }

        usuario.setRole(role);
        Usuario salvo = usuarioRepository.save(usuario);
        evictCacheUsuarios(igrejaId);
        return UsuarioResponseDTO.from(salvo);
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscarPorId(UUID id, UUID igrejaId) {
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado."));
        return UsuarioResponseDTO.from(usuario);
    }

    @Transactional
    public void arquivarUsuario(UUID id, UUID igrejaId) {
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new BusinessException("Usuario não encontrado."));
        garantirNaoEhUltimoAdmin(usuario, igrejaId);

        usuarioRepository.delete(usuario);
        evictCacheUsuarios(igrejaId);
    }

    @Transactional
    public void arquivarPorMembro(UUID membroId, UUID igrejaId) {
        usuarioRepository.findByMembroId(membroId).ifPresent(usuario -> {
            usuarioRepository.delete(usuario);
            evictCacheUsuarios(igrejaId);
        });
    }


}
