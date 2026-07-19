package com.domus.api.modules.igreja;

import com.domus.api.config.TokenService;
import com.domus.api.shared.security.RefreshTokenService;
import com.domus.api.modules.igreja.DTO.AtualizarIgrejaRequest;
import com.domus.api.modules.igreja.DTO.IgrejaDTO;
import com.domus.api.modules.igreja.DTO.IgrejaDetalheDTO;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaAdminRequest;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse;
import com.domus.api.modules.membro.DTO.EnderecoDTO;
import com.domus.api.modules.membro.Endereco;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.membro.StatusMembro;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.util.TextoUtil;
import org.springframework.cache.CacheManager;
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
    private final CacheManager cacheManager;

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

    /**
     * A igreja inteira para a tela de Configurações. Não é cacheado: é a tela de edição,
     * onde ver dado velho logo depois de salvar seria pior do que a ida ao banco.
     */
    @Transactional(readOnly = true)
    public IgrejaDetalheDTO buscarDetalhe(UUID igrejaId) {
        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));
        return IgrejaDetalheDTO.from(igreja, nomeDoAutor(igreja.getAtualizadoPor()));
    }

    /**
     * Atualiza os dados da própria igreja. O id vem do JWT (nunca do corpo), como toda
     * operação do sistema — o admin só edita a igreja dele.
     *
     * <p>Registra quem alterou (o {@code updated_at} é automático), alimentando o card
     * de logs de atividade.
     */
    @Transactional
    public IgrejaDetalheDTO atualizar(UUID igrejaId, UUID usuarioId, AtualizarIgrejaRequest data) {
        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        // CNPJ é único no sistema: só reclama se outra igreja já usa o mesmo.
        if (data.cnpj() != null && !data.cnpj().isBlank()
                && !data.cnpj().equals(igreja.getCnpj())
                && igrejaRepository.existsByCnpj(data.cnpj())) {
            throw new BusinessException("CNPJ_EM_USO", "Este CNPJ já está cadastrado em outra igreja.");
        }

        igreja.setNome(TextoUtil.capitalizar(data.nome()));
        igreja.setRazaoSocial(data.razaoSocial());
        igreja.setCnpj(vazioViraNulo(data.cnpj()));
        igreja.setDenominacao(TextoUtil.capitalizar(data.denominacao()));
        igreja.setEmailContato(data.emailContato());
        igreja.setTelefoneContato(data.telefoneContato());
        igreja.setLogoUrl(data.logoUrl());
        igreja.setEndereco(paraEndereco(data.endereco()));
        igreja.setAtualizadoPor(usuarioRepository.getReferenceById(usuarioId));

        igrejaRepository.save(igreja);
        // O resumo público é cacheado por id — sem isso a tela mostraria o nome antigo.
        cacheManager.getCache("igreja").evictIfPresent(igrejaId);

        log.info("Igreja atualizada. igreja_id={}, por_usuario_id={}", igrejaId, usuarioId);
        return IgrejaDetalheDTO.from(igreja, nomeDoAutor(igreja.getAtualizadoPor()));
    }

    /** CNPJ é UNIQUE: string vazia viraria um valor real e colidiria na segunda igreja sem CNPJ. */
    private String vazioViraNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }

    private String nomeDoAutor(Usuario usuario) {
        if (usuario == null || usuario.getMembro() == null) return null;
        return usuario.getMembro().getNome();
    }

    private Endereco paraEndereco(EnderecoDTO dto) {
        if (dto == null) return null;
        return Endereco.builder()
                .cep(dto.cep())
                .logradouro(TextoUtil.capitalizar(dto.logradouro()))
                .numero(dto.numero())
                .complemento(dto.complemento())
                .bairro(TextoUtil.capitalizar(dto.bairro()))
                .cidade(TextoUtil.capitalizar(dto.cidade()))
                .uf(dto.uf() == null ? null : dto.uf().toUpperCase())
                .build();
    }
}
