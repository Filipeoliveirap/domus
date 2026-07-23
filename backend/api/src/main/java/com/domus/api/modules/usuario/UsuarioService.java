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
                // A5: distingue conta ATIVA de conta DESATIVADA (usuario.ativo) — as duas têm
                // deleteAt nulo (não foram arquivadas), mas só a desativada precisa de uma
                // mensagem que aponte o caminho certo (reativar), em vez do genérico "já tem
                // acesso" (que soa como se bastasse esperar, quando na verdade a conta nem
                // consegue logar hoje).
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

        String email = garantirEmailDoMembro(membro, data.email());

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

        String email = garantirEmailDoMembro(membro, data.email());

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
        // A5: usuário desativado (usuario.ativo=false) não deve receber convite — reenviar um
        // link de "definir senha" para uma conta que o admin decidiu desligar reabriria o
        // acesso por trás da própria decisão de desativar.
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

    /**
     * Garante que o membro tenha e-mail (o convite depende dele). Se já tem, retorna.
     * Se não tem, exige o `emailFornecido`, valida unicidade e grava no membro.
     */
    private String garantirEmailDoMembro(Pessoa membro, String emailFornecido) {
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

        // Mesmo raciocínio do arquivamento de pessoa (ver PessoaService.arquivarMembro): o
        // ON DELETE SET NULL de criado_por_usuario_id/atualizado_por_usuario_id nunca dispara
        // (Usuario usa soft delete), e criado_por é preenchido em TODO evento (não é opcional
        // como o responsável) — arquivar um usuário que já cadastrou UM evento derrubaria a
        // listagem INTEIRA se este passo faltasse. Antes do delete, pois usa o nome da pessoa
        // dele (ainda resolvível — a pessoa não foi tocada aqui).
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
            // Mesmo desvínculo do arquivamento direto (ver arquivarUsuario acima) — chamado
            // aqui de novo porque este método é o caminho de cascata (PessoaService.arquivarMembro
            // chama arquivarPorMembro ANTES do soft delete da pessoa, então usuario.getPessoa()
            // ainda resolve o nome certo).
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


}
