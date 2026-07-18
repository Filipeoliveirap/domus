package com.domus.api.modules.igreja;

import com.domus.api.config.TokenService;
import com.domus.api.shared.security.RefreshTokenService;
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
import com.domus.api.shared.exception.ResourceNotFoundException;
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
    private final RefreshTokenService refreshTokenService;
    private final MembroRepository  membroRepository;

    @Transactional
    public RegistrarIgrejaResponse registrar(RegistrarIgrejaAdminRequest request) {
        log.info("Iniciando o cadastro da igreja. nome={}, emailAdmin={}", request.getNomeIgreja(), request.getEmailAdmin());

        Usuario admin = criarIgrejaComAdmin(new DadosNovaIgreja(
                request.getNomeIgreja(),
                request.getEmailContato(),
                request.getCnpj(),
                request.getTelefoneContato(),
                request.getNomeAdmin(),
                request.getEmailAdmin(),
                passwordEncoder.encode(request.getSenhaAdmin()),
                null
        ));

        var token = tokenService.generateToken(admin);
        var refreshToken = refreshTokenService.criar(admin.getId());

        return new RegistrarIgrejaResponse(
                admin.getId(),
                token,
                refreshToken,
                admin.getNome(),
                admin.getRole().getNome(),
                admin.getIgreja().getId(),
                admin.getIgreja().getNome()
        );
    }

    /**
     * Cria igreja + membro + usuário ADMIN_IGREJA. Compartilhado entre o cadastro nativo
     * (senha com hash) e o cadastro via Google (senha null + google_sub). NÃO emite tokens —
     * isso é responsabilidade de quem chama.
     */
    @Transactional
    public Usuario criarIgrejaComAdmin(DadosNovaIgreja dados) {
        if (membroRepository.existsByEmail(dados.emailAdmin())) {
            log.warn("E-mail já cadastrado. email={}", dados.emailAdmin());
            throw new BusinessException("EMAIL_DUPLICADO", "E-mail já cadastrado no sistema.");
        }
        if (dados.cnpj() != null && !dados.cnpj().isBlank()
                && igrejaRepository.existsByCnpj(dados.cnpj())) {
            log.warn("CNPJ já cadastrado. cnpj={}", dados.cnpj());
            throw new BusinessException("CNPJ_DUPLICADO", "CNPJ já cadastrado no sistema.");
        }

        Igreja igreja = Igreja.builder()
                .nome(com.domus.api.shared.util.TextoUtil.capitalizar(dados.nomeIgreja()))
                .emailContato(dados.emailContato())
                .cnpj(dados.cnpj())
                .telefoneContato(dados.telefoneContato())
                .build();
        igrejaRepository.save(igreja);
        log.info("Igreja criada. id={}, nome={}", igreja.getId(), igreja.getNome());

        Role roleAdmin = roleRepository.findByNome("ADMIN_IGREJA")
                .orElseThrow(() -> new IllegalStateException("Role ADMIN_IGREJA não encontrada. Verifique o seed da migration V2."));

        Membro membroAdmin = Membro.builder()
                .igreja(igreja)
                .nome(com.domus.api.shared.util.TextoUtil.capitalizar(dados.nomeAdmin()))
                .email(dados.emailAdmin())
                .status(StatusMembro.ATIVO)
                .build();
        membroRepository.save(membroAdmin);

        Usuario admin = Usuario.builder()
                .igreja(igreja)
                .membro(membroAdmin)
                .senhaHash(dados.senhaHashOuNull())
                .googleSub(dados.googleSubOuNull())
                .ativo(true)
                .role(roleAdmin)
                .build();
        admin.registrarLogin();
        usuarioRepository.save(admin);
        log.info("Igreja + admin criados. usuario_id={}, igreja_id={}", admin.getId(), igreja.getId());
        return admin;
    }

    @Cacheable(value = "igreja", key = "#id")
    public IgrejaDTO buscarPorId(UUID id) {
        log.info("Buscando igreja no banco. id={}", id);
        Igreja igreja = igrejaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));
        return IgrejaDTO.from(igreja);
    }



}
