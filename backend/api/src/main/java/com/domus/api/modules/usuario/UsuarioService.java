package com.domus.api.modules.usuario;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.DTO.ConcederAcessoRequestDTO;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.usuario.DTO.UsuarioResponseDTO;
import com.domus.api.modules.auth.PasswordResetService;
import com.domus.api.shared.email.EmailService;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.security.Perfil;

@Service
@Slf4j
@RequiredArgsConstructor
public class UsuarioService {

    private static final String ROLE_ADMIN = Perfil.ADMIN_IGREJA.name();
    private static final Duration TTL_CONVITE = Duration.ofDays(7);

    private final UsuarioRepository usuarioRepository;
    private final IgrejaRepository igrejaRepository;
    private final RoleRepository roleRepository;
    private final PessoaRepository membroRepository;
    private final CacheEvictor cacheEvictor;
    private final OutboxRegistrador outboxRegistrador;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;
    private final com.domus.api.modules.evento.EventoRepository eventoRepository;
    private final UsuarioCapacidadeRepository capacidadeRepository;

    @Transactional
    public UsuarioResponseDTO concederAcesso(ConcederAcessoRequestDTO data, UUID igrejaId) {
        log.info("Concedendo acesso (convite) a membro. pessoaId={}, igreja_id={}", data.pessoaId(), igrejaId);

        Pessoa membro = membroRepository.findByIdAndIgrejaId(data.pessoaId(), igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));

        Usuario existente = usuarioRepository
                .findByPessoaIdIncluindoArquivados(membro.getId())
                .orElse(null);
        if (existente != null) {
            if (existente.getDeleteAt() == null) {
                // Conta ATIVA vs DESATIVADA: ambas têm deleteAt nulo (não arquivadas),
                // mas só a desativada precisa de mensagem de reativação, não de "já tem acesso".
                if (!existente.isAtivo()) {
                    throw new BusinessException("USUARIO_DESATIVADO",
                            "Este membro já tem uma conta, mas ela está desativada. "
                            + "Reative o acesso em vez de convidar novamente.");
                }
                throw new BusinessException("MEMBRO_JA_TEM_ACESSO",
                        "Esta pessoa já tem acesso ao sistema.");
            }
            throw new BusinessException("MEMBRO_TEM_USUARIO_ARQUIVADO",
                    "Este membro já teve acesso, que foi arquivado. Deseja reativar?");
        }

        String email = garantirEmailDoMembro(membro, data.email(), igrejaId);

        Role role = roleRepository.findByNome(data.role())
                .orElseThrow(() -> new BusinessException("Perfil inválido"));

        Usuario usuario = Usuario.builder()
                .igreja(membro.getIgreja())
                .pessoa(membro)
                .senhaHash(null)
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
        enviarConvite(salvo, email, membro.getIgreja().getNome());
        log.info("Acesso concedido por convite. usuario_id={}, pessoa_id={}, igreja_id={}", salvo.getId(), membro.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("usuarios", igrejaId);
        return UsuarioResponseDTO.from(salvo);
    }

    @Transactional
    public UsuarioResponseDTO reativarAcesso(ConcederAcessoRequestDTO data, UUID igrejaId) {
        log.info("Reativando acesso (convite) de membro. pessoaId={}, igreja_id={}", data.pessoaId(), igrejaId);
        Pessoa membro = membroRepository.findByIdAndIgrejaId(data.pessoaId(), igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));

        Usuario arquivado = usuarioRepository.findByPessoaIdIncluindoArquivados(membro.getId())
                .filter(u -> u.getDeleteAt() != null)
                .orElseThrow(() -> new BusinessException("Nenhum acesso arquivado encontrado."));

        String email = garantirEmailDoMembro(membro, data.email(), igrejaId);

        Role role = roleRepository.findByNome(data.role())
                .orElseThrow(() -> new BusinessException("Perfil inválido"));

        arquivado.setDeleteAt(null);
        arquivado.setAtivo(true);
        arquivado.setRole(role);
        arquivado.marcarConvitePendente();

        Usuario salvo = usuarioRepository.save(arquivado);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.USUARIO,
                TipoEventoOutbox.ATUALIZADO,
                salvo.getId(),
                igrejaId
        );
        enviarConvite(salvo, email, membro.getIgreja().getNome());
        log.info("Acesso reativado por convite. usuario_id={}, pessoa_id={}, igrejaId={}", salvo.getId(), membro.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("usuarios", igrejaId);
        return UsuarioResponseDTO.from(salvo);
    }

    @Transactional(readOnly = true)
    public void reenviarConvite(UUID usuarioId, UUID igrejaId) {
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        if (usuario.getUltimoLoginEm() != null) {
            throw new BusinessException("CONVITE_JA_ACEITO",
                    "Este usuário já fez login — o convite já foi aceito.");
        }
        // Usuário desativado não recebe convite: reenviar o link reabriria o acesso
        // por trás da decisão do admin de desativar.
        if (!usuario.isAtivo()) {
            throw new BusinessException("USUARIO_DESATIVADO",
                    "Este usuário está desativado. Reative o acesso antes de reenviar o convite.");
        }
        String email = usuario.getEmail();
        if (email == null || email.isBlank()) {
            throw new BusinessException("MEMBRO_SEM_EMAIL",
                    "Este usuário não tem e-mail para reenviar o convite.");
        }
        enviarConvite(usuario, email, usuario.getIgreja().getNome());
        log.info("Convite reenviado. usuario_id={}", usuarioId);
    }

    /** Garante e-mail no membro (o convite depende dele). Se não tem, valida unicidade e grava. */
    private String garantirEmailDoMembro(Pessoa membro, String emailFornecido, UUID igrejaId) {
        if (membro.getEmail() != null && !membro.getEmail().isBlank()) {
            return membro.getEmail();
        }
        if (emailFornecido == null || emailFornecido.isBlank()) {
            throw new BusinessException("MEMBRO_SEM_EMAIL",
                    "Este membro não tem e-mail. Informe um e-mail para enviar o convite.");
        }
        String email = emailFornecido.trim().toLowerCase();
        if (membroRepository.existsByEmailIncluindoArquivados(email)) {
            throw new BusinessException("EMAIL_DUPLICADO", "Este e-mail já está em uso por outro cadastro.");
        }
        membro.setEmail(email);
        membroRepository.save(membro);
        outboxRegistrador.registrar(TipoEntidadeOutbox.PESSOA, TipoEventoOutbox.ATUALIZADO, membro.getId(), igrejaId);
        return email;
    }

    /** Gera o token de definição de senha (7 dias) e envia o e-mail de convite. */
    private void enviarConvite(Usuario usuario, String email, String nomeIgreja) {
        String token = passwordResetService.gerarTokenDefinicaoSenha(usuario.getId(), TTL_CONVITE);
        String link = passwordResetService.linkDefinicaoSenha(token, true);
        try {
            emailService.enviar(email, "Você foi convidado para o Domus",
                    corpoConvite(usuario.getNome(), nomeIgreja, link));
            log.info("Convite enviado. usuario_id={}", usuario.getId());
        } catch (RuntimeException e) {
            // Não vira 500: o usuário foi criado/reativado. Se o e-mail falhar, o admin usa "reenviar convite".
            log.error("Falha ao enviar convite. usuario_id={}", usuario.getId(), e);
        }
    }

    private String corpoConvite(String nome, String nomeIgreja, String link) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                  <h2>Você foi convidado para o Domus</h2>
                  <p>Olá, %s.</p>
                  <p>Você recebeu acesso ao sistema da <strong>%s</strong> no Domus.
                     Para ativar seu acesso, defina uma senha:</p>
                  <p style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background: #2563eb; color: #fff; padding: 12px 24px;
                       text-decoration: none; border-radius: 6px;">Definir minha senha</a>
                  </p>
                  <p style="color: #666; font-size: 14px;">Ou, se preferir, entre direto com sua conta
                     Google usando este mesmo e-mail.</p>
                  <p style="color: #666; font-size: 14px;">O convite expira em 7 dias.</p>
                </div>
                """.formatted(nome, nomeIgreja, link);
    }

    @Cacheable(
            value = "usuarios",
            key = "T(com.domus.api.config.redis.CacheKeys).usuarios(#igrejaId, #q, #pageable)"
    )

    @Transactional(readOnly = true)
    public PagedResponse<UsuarioResponseDTO> listar(UUID igrejaId, String q, Pageable pageable) {
        Page<UsuarioResponseDTO> pagina = usuarioRepository.buscarPorIgreja(igrejaId, q, pageable);
        List<UsuarioResponseDTO> enriquecido = pagina.getContent().stream()
                .map(this::enriquecerComCapacidades).toList();
        return new PagedResponse<>(enriquecido, pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages(), pagina.isLast());
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
        return enriquecerComCapacidades(UsuarioResponseDTO.from(usuario));
    }

    private UsuarioResponseDTO enriquecerComCapacidades(UsuarioResponseDTO dto) {
        List<String> capacidades = capacidadeRepository.findByUsuarioId(dto.id())
                .stream().map(UsuarioCapacidade::getCapacidade).toList();
        return new UsuarioResponseDTO(dto.id(), dto.nome(), dto.email(), dto.role(),
                dto.ativo(), dto.ultimoLoginEm(), dto.convitePendente(), dto.criadoEm(),
                dto.fotoId(), capacidades);
    }

    @Transactional
    public void arquivarUsuario(UUID id, UUID igrejaId) {
        log.info("Arquivando usuário. id={}, igreja_id={}", id, igrejaId);
        Usuario usuario = usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario não encontrado."));
        garantirNaoEhUltimoAdmin(usuario, igrejaId);

        // Desvincula referências de auditoria em evento antes do delete.
        // ON DELETE SET NULL nunca dispara (soft delete), e criado_por é preenchido em
        // todo evento — arquivar um usuário sem este passo quebraria a listagem inteira.
        eventoRepository.desvincularUsuario(usuario.getId(), usuario.getPessoa().getNome());

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
    public void arquivarPorMembro(UUID pessoaId, UUID igrejaId) {
        usuarioRepository.findByPessoaId(pessoaId).ifPresent(usuario -> {
            log.info("Arquivando usuário em cascata (membro arquivado). usuario_id={}, pessoa_id={}, igrejaId={}", usuario.getId(), pessoaId, igrejaId);
            // Chamado antes do soft delete da pessoa — usuario.getPessoa() ainda resolve.
            eventoRepository.desvincularUsuario(usuario.getId(), usuario.getPessoa().getNome());
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
    public void reindexarPorMembro(UUID pessoaId, UUID igrejaId) {
        usuarioRepository.findByPessoaId(pessoaId).ifPresent(usuario -> {
            log.debug("Reindexando usuário por alteração no membro. usuario_id={}, pessoa_id={}", usuario.getId(), pessoaId);
            outboxRegistrador.registrar(
                    TipoEntidadeOutbox.USUARIO,
                    TipoEventoOutbox.ATUALIZADO,
                    usuario.getId(),
                    igrejaId
            );
        });
    }

    private static final java.util.Set<String> CAPACIDADES_VALIDAS =
            java.util.Set.of("SECRETARIO", "TESOUREIRO");

    private void validarCapacidade(String capacidade) {
        if (!CAPACIDADES_VALIDAS.contains(capacidade)) {
            throw new BusinessException("CAPACIDADE_INVALIDA",
                    "Capacidade inválida. Use SECRETARIO ou TESOUREIRO.");
        }
    }

    @Transactional
    public void concederCapacidade(UUID id, String capacidade, UUID igrejaId, UUID concedidoPorId) {
        validarCapacidade(capacidade);
        usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        if (capacidadeRepository.existsByUsuarioIdAndCapacidade(id, capacidade)) return;

        capacidadeRepository.save(UsuarioCapacidade.builder()
                .usuarioId(id).capacidade(capacidade).concedidoPorUsuarioId(concedidoPorId).build());
        cacheEvictor.evictPorIgreja("usuarios", igrejaId);
        log.info("Capacidade concedida. usuario_id={}, capacidade={}", id, capacidade);
    }

    @Transactional
    public void revogarCapacidade(UUID id, String capacidade, UUID igrejaId) {
        validarCapacidade(capacidade);
        usuarioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        capacidadeRepository.deleteByUsuarioIdAndCapacidade(id, capacidade);
        cacheEvictor.evictPorIgreja("usuarios", igrejaId);
        log.info("Capacidade revogada. usuario_id={}, capacidade={}", id, capacidade);
    }
}
