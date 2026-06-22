package com.domus.api.modules.usuario;


import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.usuario.DTO.PagedResponse;
import com.domus.api.modules.usuario.DTO.UsuarioRequestDTO;
import com.domus.api.modules.usuario.DTO.UsuarioResponseDTO;
import com.domus.api.modules.usuario.DTO.UsuarioUpdateRequestDTO;
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

    @Transactional
    public UsuarioResponseDTO registrarUsuario(UsuarioRequestDTO data, UUID igrejaId) {
        log.info("Iniciando o cadastro de um usuario. nome={}, emailUsuario={}", data.nomeUsuario(), data.emailUsuario());

        if (usuarioRepository.existsByEmail(data.emailUsuario())) {
            log.warn("E-mail já cadastrado. emailUsuario={}", data.emailUsuario());
            throw new BusinessException("EMAIL_DUPLICADO", "E-mail já cadastrado no sistema.");
        }

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new BusinessException("Igreja não encontrada."));
        Role role = roleRepository.findByNome(data.role())
                .orElseThrow(() -> new BusinessException("Perfil inválido"));

        Usuario usuario = Usuario.builder()
                .igreja(igreja)
                .nome(data.nomeUsuario())
                .email(data.emailUsuario())
                .senhaHash(passwordEncoder.encode(data.senhaUsuario()))
                .ativo(true)
                .role(role)
                .build();
        Usuario salvo = usuarioRepository.save(usuario);
        log.info("Usuário cadastrado: id={}", salvo.getId());
        evictCacheUsuarios(igrejaId);

        return  UsuarioResponseDTO.from(salvo);
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
        String pattern = "usuarios::" + igrejaId + ":*";
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            List<String> keys = new ArrayList<>();
            cursor.forEachRemaining(keys::add);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
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
    public UsuarioResponseDTO usuarioUpdate(UUID id, UsuarioUpdateRequestDTO data, UUID igrejaId) {
        log.info("Atualizando usuário. id={}, igreja_id={}", id, igrejaId);
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado."));

        if(!usuario.getEmail().equals(data.email()) && usuarioRepository.existsByEmail(data.email())) {
            throw new BusinessException("EMAIL_DUPLICADO", "E-mail já cadastrado no sistema.");
        }

        Role role = roleRepository.findByNome(data.role())
                .orElseThrow(() -> new BusinessException("Perfil inválido"));
        if (!ROLE_ADMIN.equals(role.getNome())) {
            garantirNaoEhUltimoAdmin(usuario, igrejaId);
        }

        usuario.setNome(data.nome());
        usuario.setEmail(data.email());
        usuario.setRole(role);

        Usuario salvo = usuarioRepository.save(usuario);
        evictCacheUsuarios(igrejaId);

        log.info("Usuário atualizado. id={}", salvo.getId());
        return UsuarioResponseDTO.from(salvo);
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
    public void deletarUsuario(UUID id, UUID igrejaId) {
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new BusinessException("Usuario não encontrado."));
        garantirNaoEhUltimoAdmin(usuario, igrejaId);

        usuarioRepository.delete(usuario);
        evictCacheUsuarios(igrejaId);
    }


}
