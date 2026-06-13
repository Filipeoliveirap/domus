package com.domus.api.modules.usuario;


import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.usuario.DTO.UsuarioRequestDTO;
import com.domus.api.modules.usuario.DTO.UsuarioResponseDTO;
import com.domus.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class UsuarioService {


    private final UsuarioRepository usuarioRepository;
    private final IgrejaRepository igrejaRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioResponseDTO registrarUsuario(UsuarioRequestDTO data, UUID igrejaId) {
        log.info("Iniciando o cadastro de um usuario. nome={}, emailUsuario={}", data.nomeUsuario(), data.emailUsuario());

        if (usuarioRepository.existsByEmail(data.emailUsuario())) {
            log.warn("E-mail já cadastrado nesta igreja. emailUsuario={}", data.emailUsuario());
            throw new BusinessException("E-mail já  cadastrado nesta igreja.");
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
                .roles(Set.of(role))
                .build();
        Usuario salvo = usuarioRepository.save(usuario);
        log.info("Usuário cadastrado: id={}", salvo.getId());

        return  new UsuarioResponseDTO(
                salvo.getId(),
                salvo.getNome(),
                salvo.getEmail(),
                role.getNome(),
                salvo.getCreatedAt()
        );

    }
}
