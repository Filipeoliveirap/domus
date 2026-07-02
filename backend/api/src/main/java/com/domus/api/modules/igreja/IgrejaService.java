package com.domus.api.modules.igreja;

import com.domus.api.config.TokenService;
import com.domus.api.modules.igreja.DTO.IgrejaDTO;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaAdminRequest;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.membro.StatusMembro;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IgrejaService {

    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final MembroRepository  membroRepository;

    @Transactional
    public RegistrarIgrejaResponse registrar(RegistrarIgrejaAdminRequest request) {
        log.info("Iniciando o cadastro da igreja. nome={}, emailAdmin={}", request.getNomeIgreja(), request.getEmailAdmin());

        if (membroRepository.existsByEmail(request.getEmailAdmin())) {
            log.warn("E-mail já cadastrado. email={}", request.getEmailAdmin());
            throw new BusinessException("EMAIL_DUPLICADO", "E-mail já cadastrado no sistema.");
        }
        if(request.getCnpj() != null && !request.getCnpj().isBlank()) {
            if(igrejaRepository.existsByCnpj(request.getCnpj())) {
                log.warn("CNPJ já cadastrado. cnpj={}", request.getCnpj());
                throw new BusinessException("CNPJ_DUPLICADO", "CNPJ já cadastrado no sistema.");
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

        Membro membroAdmin = Membro.builder()
                .igreja(igreja)
                .nome(request.getNomeAdmin())
                .email(request.getEmailAdmin())
                .status(StatusMembro.ATIVO)
                .build();
        membroRepository.save(membroAdmin);

        Usuario admin = Usuario.builder()
                .igreja(igreja)
                .membro(membroAdmin)        // ← liga ao membro
                .senhaHash(passwordEncoder.encode(request.getSenhaAdmin()))
                .ativo(true)
                .role(roleAdmin)
                .build();

        admin.registrarLogin();
        usuarioRepository.save(admin);

        log.info("Admin cadastrado. usuario_id={}, igreja_id={}",
                admin.getId(), igreja.getId());

        var token = tokenService.generateToken(admin);

        return new RegistrarIgrejaResponse(
                admin.getId(),
                token,
                request.getNomeAdmin(),
                roleAdmin.getNome(),
                admin.getIgreja().getId()
        );

    }

    @Cacheable(value = "igreja", key = "#id")
    public IgrejaDTO buscarPorId(UUID id) {
        log.info("Buscando igreja no banco. id={}", id);
        Igreja igreja = igrejaRepository.findById(id)
                .orElseThrow(() -> new BusinessException("IGREJA_NAO_ENCONTRADA", "Igreja não encontrada."));
        return IgrejaDTO.from(igreja);
    }



}
