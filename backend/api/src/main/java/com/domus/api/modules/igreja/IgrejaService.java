package com.domus.api.modules.igreja;

import com.domus.api.config.TokenService;
import com.domus.api.shared.security.RefreshTokenService;
import com.domus.api.modules.igreja.DTO.AtualizarIgrejaRequest;
import com.domus.api.modules.igreja.DTO.IgrejaDetalheDTO;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaAdminRequest;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse;
import com.domus.api.modules.igreja.DTO.RotulosDTO;
import com.domus.api.modules.igreja.DTO.RotulosRequest;
import com.domus.api.modules.foto.Foto;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.pessoa.DTO.EnderecoDTO;
import com.domus.api.shared.dominio.Endereco;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import com.domus.api.modules.termos.TermoAceiteService;
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
import com.domus.api.shared.security.Perfil;

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
    private final PessoaRepository  membroRepository;
    private final CacheManager cacheManager;
    private final FotoService fotoService;
    private final OutboxRegistrador outboxRegistrador;
    private final TermoAceiteService termoAceiteService;

    @Transactional
    public RegistrarIgrejaResponse registrar(RegistrarIgrejaAdminRequest request, String ip) {
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
        ), request.isAceitouTermos(), ip);

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

    /** Cria igreja + pessoa + usuário ADMIN_IGREJA. Compartilhado entre cadastro nativo
     *  (senha com hash) e Google (senha null + google_sub). Não emite tokens. */
    @Transactional
    public Usuario criarIgrejaComAdmin(DadosNovaIgreja dados, boolean aceitouTermos, String ip) {
        termoAceiteService.exigirAceite(aceitouTermos);

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

        Role roleAdmin = roleRepository.findByNome(Perfil.ADMIN_IGREJA.name())
                .orElseThrow(() -> new IllegalStateException("Role ADMIN_IGREJA não encontrada. Verifique o seed da migration V2."));

        // Quem cadastra a própria igreja é assumido MEMBRO (batizado) — corrigível depois no cadastro.
        Pessoa membroAdmin = Pessoa.builder()
                .igreja(igreja)
                .nome(com.domus.api.shared.util.TextoUtil.capitalizar(dados.nomeAdmin()))
                .email(dados.emailAdmin())
                .vinculo(Vinculo.MEMBRO)
                .build();
        membroRepository.save(membroAdmin);
        outboxRegistrador.registrar(TipoEntidadeOutbox.PESSOA, TipoEventoOutbox.CRIADO, membroAdmin.getId(), igreja.getId());

        Usuario admin = Usuario.builder()
                .igreja(igreja)
                .pessoa(membroAdmin)
                .senhaHash(dados.senhaHashOuNull())
                .googleSub(dados.googleSubOuNull())
                .ativo(true)
                .role(roleAdmin)
                .build();
        admin.registrarLogin();
        usuarioRepository.save(admin);
        outboxRegistrador.registrar(TipoEntidadeOutbox.USUARIO, TipoEventoOutbox.CRIADO, admin.getId(), igreja.getId());
        log.info("Igreja + admin criados. usuario_id={}, igreja_id={}", admin.getId(), igreja.getId());

        termoAceiteService.registrarAceite(admin.getId(), ip);

        return admin;
    }

    /** Cacheado; invalidado em {@link #atualizar}. */
    @Cacheable(value = "igreja", key = "#igrejaId")
    @Transactional(readOnly = true)
    public IgrejaDetalheDTO buscarDetalhe(UUID igrejaId) {
        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));
        return IgrejaDetalheDTO.from(igreja, nomeDoAutor(igreja.getAtualizadoPor()));
    }

    /** Atualiza os dados da própria igreja. O igrejaId vem do JWT — nenhum corpo o informa. */
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
        igreja.setSigla(data.sigla() != null ? data.sigla().trim().toUpperCase() : null);
        igreja.setEmailContato(data.emailContato());
        igreja.setTelefoneContato(data.telefoneContato());
        igreja.setEndereco(paraEndereco(data.endereco()));
        igreja.setAtualizadoPor(usuarioRepository.getReferenceById(usuarioId));

        // Logo: mesma ordem de PessoaService/EventoService — aponta para a nova antes de
        // remover a antiga (ON DELETE RESTRICT recusaria o contrário).
        Foto logoAntiga = igreja.getLogoFoto();
        Foto logoNova = fotoService.buscarParaVincular(data.logoFotoId(), igrejaId);
        igreja.setLogoFoto(logoNova);

        igrejaRepository.save(igreja);

        boolean logoMudou = !java.util.Objects.equals(
                logoAntiga == null ? null : logoAntiga.getId(),
                logoNova == null ? null : logoNova.getId());
        if (logoMudou && logoAntiga != null) {
            fotoService.remover(logoAntiga.getId());
        }

        // O resumo público é cacheado por id — sem isso a tela mostraria o nome antigo.
        cacheManager.getCache("igreja").evictIfPresent(igrejaId);

        log.info("Igreja atualizada. igreja_id={}, por_usuario_id={}", igrejaId, usuarioId);
        return IgrejaDetalheDTO.from(igreja, nomeDoAutor(igreja.getAtualizadoPor()));
    }

    /** Cada bloco vem completo (3 campos) ou totalmente vazio (reseta pro padrão) — nunca parcial. */
    @Transactional
    public RotulosDTO atualizarRotulos(UUID igrejaId, RotulosRequest data) {
        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        aplicarBloco(data.ministerio(), igreja::setMinisterioNomeSingular,
                igreja::setMinisterioNomePlural, igreja::setMinisterioGenero);
        aplicarBloco(data.congregacao(), igreja::setCongregacaoNomeSingular,
                igreja::setCongregacaoNomePlural, igreja::setCongregacaoGenero);
        aplicarBloco(data.celula(), igreja::setCelulaNomeSingular,
                igreja::setCelulaNomePlural, igreja::setCelulaGenero);

        igrejaRepository.save(igreja);
        cacheManager.getCache("igreja").evictIfPresent(igrejaId);

        log.info("Rótulos customizados atualizados. igreja_id={}", igrejaId);
        return RotulosDTO.from(igreja);
    }

    private void aplicarBloco(
            RotulosRequest.Bloco bloco,
            java.util.function.Consumer<String> setSingular,
            java.util.function.Consumer<String> setPlural,
            java.util.function.Consumer<GeneroGramatical> setGenero) {
        if (bloco == null) return;

        boolean algumPreenchido = bloco.singular() != null || bloco.plural() != null || bloco.genero() != null;
        boolean todosPreenchidos = bloco.singular() != null && !bloco.singular().isBlank()
                && bloco.plural() != null && !bloco.plural().isBlank()
                && bloco.genero() != null;

        if (algumPreenchido && !todosPreenchidos) {
            throw new BusinessException("ROTULO_INCOMPLETO",
                    "Preencha singular, plural e gênero, ou deixe os três em branco pra restaurar o padrão.");
        }

        setSingular.accept(todosPreenchidos ? bloco.singular() : null);
        setPlural.accept(todosPreenchidos ? bloco.plural() : null);
        setGenero.accept(todosPreenchidos ? bloco.genero() : null);
    }

    /** CNPJ é UNIQUE: string vazia viraria um valor real e colidiria na segunda igreja sem CNPJ. */
    private String vazioViraNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }

    private String nomeDoAutor(Usuario usuario) {
        if (usuario == null || usuario.getPessoa() == null) return null;
        return usuario.getPessoa().getNome();
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
