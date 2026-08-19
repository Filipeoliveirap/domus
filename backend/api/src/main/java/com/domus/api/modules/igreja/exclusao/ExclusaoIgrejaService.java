package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.exclusao.DTO.ResumoExclusaoResponse;
import com.domus.api.modules.ministerio.MinisterioRepository;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.auth.GoogleAuthService;
import com.domus.api.shared.email.EmailService;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Agendar/cancelar a exclusão definitiva da igreja. A purga em si vive em {@code PurgaIgrejaService} (fase seguinte). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExclusaoIgrejaService {

    private final IgrejaRepository igrejaRepository;
    private final PessoaRepository pessoaRepository;
    private final EventoRepository eventoRepository;
    private final MovimentacaoFinanceiraRepository movimentacaoRepository;
    private final CelulaRepository celulaRepository;
    private final MinisterioRepository ministerioRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final GoogleAuthService googleAuthService;
    private final CacheManager cacheManager;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Link da Zona de Perigo — mesma tela onde a exclusão é cancelada. Login preserva o
     *  destino via {@code ?next=}, então funciona tanto logado quanto deslogado. */
    private String linkCancelarExclusao() {
        return frontendUrl + "/login?next=/configuracoes/igreja";
    }

    @Transactional(readOnly = true)
    public ResumoExclusaoResponse resumo(UUID igrejaId, UUID usuarioId) {
        List<String> nomesFilhas = igrejaRepository.buscarIdsDasFilhas(igrejaId).isEmpty()
                ? List.of()
                : igrejaRepository.findAllById(igrejaRepository.buscarIdsDasFilhas(igrejaId))
                        .stream().map(Igreja::getNome).toList();

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        return new ResumoExclusaoResponse(
                pessoaRepository.countByIgrejaId(igrejaId),
                eventoRepository.countByIgrejaId(igrejaId),
                movimentacaoRepository.countByIgrejaId(igrejaId),
                celulaRepository.countByIgrejaId(igrejaId),
                ministerioRepository.countByIgrejaId(igrejaId),
                usuarioRepository.countByIgrejaId(igrejaId),
                nomesFilhas,
                usuario.getSenhaHash() != null
        );
    }

    @Transactional
    public void agendar(UUID igrejaId, UUID usuarioId, String nomeConfirmacao, String senha, String googleIdToken) {
        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        if (!normalizarNome(igreja.getNome()).equals(normalizarNome(nomeConfirmacao))) {
            throw new BusinessException("NOME_NAO_CONFERE",
                    "O nome digitado não confere com o nome da igreja.");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        reautenticar(usuario, senha, googleIdToken);

        igrejaRepository.marcarExclusaoAgendada(igrejaId, usuarioId, LocalDateTime.now());
        cacheManager.getCache("igreja").evictIfPresent(igrejaId);
        log.info("Exclusão agendada. igreja_id={}, por_usuario_id={}", igrejaId, usuarioId);

        // Best-effort: o agendamento já está salvo — uma falha no envio (provedor fora do
        // ar, etc.) não pode derrubar a ação principal, só fica registrada no log.
        // Avisa o contato da igreja E todos os ADMIN_IGREJA ativos — não só quem agendou.
        String assunto = "Exclusão da sua igreja no Domus foi agendada";
        String corpo = corpoEmailAgendada(igreja.getNome());
        for (String destinatario : destinatarios(igreja.getEmailContato(), usuarioRepository.buscarEmailsAdminsAtivos(igrejaId))) {
            try {
                emailService.enviar(destinatario, assunto, corpo);
            } catch (Exception e) {
                log.error("Falha ao enviar e-mail de exclusão agendada — agendamento já foi salvo. igreja_id={}, destinatario={}",
                        igrejaId, destinatario, e);
            }
        }
    }

    /** Contato + todos os admins, sem duplicar (contato costuma ser o e-mail de um deles). */
    private java.util.Set<String> destinatarios(String emailContato, List<String> emailsAdmins) {
        java.util.Set<String> resultado = new java.util.LinkedHashSet<>();
        if (emailContato != null && !emailContato.isBlank()) {
            resultado.add(emailContato);
        }
        resultado.addAll(emailsAdmins);
        return resultado;
    }

    private String corpoEmailAgendada(String nomeIgreja) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                  <h2>Exclusão agendada</h2>
                  <p>A exclusão definitiva de "%s" foi agendada e acontecerá em 10 dias.</p>
                  <p>Se isso foi um engano, ou você mudou de ideia, cancele a qualquer momento antes do prazo:</p>
                  <p style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background: #2563eb; color: #fff; padding: 12px 24px;
                       text-decoration: none; border-radius: 6px;">Cancelar exclusão</a>
                  </p>
                  <p style="color: #666; font-size: 14px;">Se você não fez este pedido, cancele imediatamente
                     pelo link acima e troque sua senha.</p>
                </div>
                """.formatted(nomeIgreja, linkCancelarExclusao());
    }

    private void reautenticar(Usuario usuario, String senha, String googleIdToken) {
        if (usuario.getSenhaHash() != null) {
            if (senha == null || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
                throw new BusinessException("SENHA_INCORRETA", "Senha incorreta.");
            }
            return;
        }
        if (usuario.getGoogleSub() != null) {
            if (googleIdToken == null) {
                throw new BusinessException("REAUTENTICACAO_NECESSARIA", "Confirme sua identidade com o Google para continuar.");
            }
            String subConfirmado = googleAuthService.reautenticarPorGoogle(googleIdToken);
            if (!usuario.getGoogleSub().equals(subConfirmado)) {
                throw new BusinessException("REAUTENTICACAO_INVALIDA", "Não foi possível confirmar sua identidade.");
            }
            return;
        }
        throw new BusinessException("REAUTENTICACAO_NECESSARIA", "Confirme sua identidade para continuar.");
    }

    @Transactional
    public void cancelar(UUID igrejaId) {
        igrejaRepository.cancelarExclusaoAgendada(igrejaId);
        cacheManager.getCache("igreja").evictIfPresent(igrejaId);
        log.info("Exclusão cancelada. igreja_id={}", igrejaId);
    }

    private String normalizarNome(String nome) {
        return nome == null ? "" : nome.trim().toLowerCase();
    }
}
