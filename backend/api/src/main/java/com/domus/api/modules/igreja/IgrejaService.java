package com.domus.api.modules.igreja;

import com.domus.api.modules.igreja.DTO.RegistrarIgrejaAdminRequest;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class IgrejaService {

    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public void registrar(RegistrarIgrejaAdminRequest request) {
        log.info("Iniciando o cadastro da igreja. nome={}, emailAdmin={}", request.getNomeIgreja(), request.getEmailAdmin());

        if(usuarioRepository.existsByEmail(request.getEmailAdmin())) {
            log.warn("E-mail já cadastrado. email={}", request.getEmailAdmin());
            throw new BusinessException("E-mail já cadastrado no sistema.");
        }
        if(request.getCnpj() != null && !request.getCnpj().isBlank()) {
            if(igrejaRepository.existsByCnpj(request.getCnpj())) {
                log.warn("CNPJ já cadastrado. cnpj={}", request.getCnpj());
                throw new BusinessException("CNPJ já cadastrado no sistema.");
            }
        }
        Igreja igreja = Igreja.builder()
                .nome(request.getNomeIgreja())
                .emailContato(request.getEmailContato())
                .cnpj(request.getCnpj())
                .telefoneContato(request.getTelefoneContato())
                .build();
        igrejaRepository.save(igreja);
        log.info("Igreja criada. id={}, nome={}", igreja.getId(), igreja.getNome());

        Role roleAdmin = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow(() -> new IllegalStateException("Role ADMIN_IGREJA não encontrada. Verifique o seed da migration V2."));

        Usuario admin = Usuario.builder()
                .igreja(igreja)
                .nome(request.getNomeAdmin())
                .email(request.getEmailAdmin())
                .senhaHash(passwordEncoder.encode(request.getSenhaAdmin()))
                .ativo(true)
                .roles(Set.of(roleAdmin))
                .build();
        usuarioRepository.save(admin);
        log.info("Admin cadastrado. usuario_id={}, igreja_id={}",
                admin.getId(), igreja.getId());
    }
}
