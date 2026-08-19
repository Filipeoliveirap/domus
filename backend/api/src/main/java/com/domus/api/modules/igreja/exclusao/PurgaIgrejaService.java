package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.local.LocalEventoRepository;
import com.domus.api.modules.visitante.VisitanteRepository;
import com.domus.api.modules.celula.CelulaMembroRepository;
import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.ministerio.MinisterioMembroRepository;
import com.domus.api.modules.ministerio.MinisterioRepository;
import com.domus.api.modules.usuario.UsuarioCapacidadeRepository;
import com.domus.api.modules.foto.FotoRepository;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.busca.PessoaSearchRepository;
import com.domus.api.modules.evento.busca.EventoSearchRepository;
import com.domus.api.modules.usuario.busca.UsuarioSearchRepository;
import com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoSearchRepository;
import com.domus.api.modules.financeiro.categoria.busca.CategoriaSearchRepository;
import com.domus.api.modules.celula.busca.CelulaSearchRepository;
import com.domus.api.modules.ministerio.busca.MinisterioSearchRepository;
import com.domus.api.modules.visitante.busca.VisitanteSearchRepository;
import com.domus.api.shared.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Purga tabela-por-tabela da igreja: uma transação, uma linha de DELETE por tabela, ordem
 *  explícita (nunca ON DELETE CASCADE) — se qualquer passo falhar, tudo desfaz e o job diário
 *  tenta de novo amanhã. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgaIgrejaService {

    private final MovimentacaoFinanceiraRepository movimentacaoRepository;
    private final CategoriaFinanceiraRepository categoriaRepository;
    private final InscricaoRepository inscricaoRepository;
    private final EventoRepository eventoRepository;
    private final VisitanteRepository visitanteRepository;
    private final LocalEventoRepository localEventoRepository;
    private final CelulaMembroRepository celulaMembroRepository;
    private final CelulaRepository celulaRepository;
    private final MinisterioMembroRepository ministerioMembroRepository;
    private final MinisterioRepository ministerioRepository;
    private final UsuarioCapacidadeRepository usuarioCapacidadeRepository;
    private final FotoRepository fotoRepository;
    private final FotoService fotoService;
    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;
    private final PessoaSearchRepository pessoaSearchRepository;
    private final EventoSearchRepository eventoSearchRepository;
    private final UsuarioSearchRepository usuarioSearchRepository;
    private final MovimentacaoSearchRepository movimentacaoSearchRepository;
    private final CategoriaSearchRepository categoriaSearchRepository;
    private final CelulaSearchRepository celulaSearchRepository;
    private final MinisterioSearchRepository ministerioSearchRepository;
    private final VisitanteSearchRepository visitanteSearchRepository;
    private final EmailService emailService;

    @Transactional
    public void purgar(UUID igrejaId) {
        log.warn("Iniciando purga definitiva da igreja. igreja_id={}", igrejaId);

        // Captura os e-mails dos admins ANTES de apagar os usuários — depois disso não tem
        // mais como saber quem eram, e o e-mail de conclusão precisa avisar todos eles.
        List<String> emailsAdmins = usuarioRepository.buscarEmailsAdminsAtivos(igrejaId);

        inscricaoRepository.deleteAllByIgrejaId(igrejaId);
        movimentacaoRepository.deleteAllByIgrejaId(igrejaId);
        categoriaRepository.deleteAllByIgrejaId(igrejaId);

        celulaMembroRepository.deleteAllByIgrejaId(igrejaId);
        celulaRepository.deleteAllByIgrejaId(igrejaId);
        ministerioMembroRepository.deleteAllByIgrejaId(igrejaId);
        ministerioRepository.deleteAllByIgrejaId(igrejaId);
        usuarioCapacidadeRepository.deleteAllByUsuarioIgrejaId(igrejaId);

        eventoRepository.deleteAllByIgrejaId(igrejaId);
        visitanteRepository.deleteAllByIgrejaId(igrejaId);
        localEventoRepository.deleteAllByIgrejaId(igrejaId);

        List<UUID> idsFilhas = igrejaRepository.buscarIdsDasFilhas(igrejaId);
        // Nomes capturados antes do desvínculo — só pro e-mail de conclusão avisar quais
        // igrejas continuam de pé, de forma independente.
        List<String> nomesFilhas = idsFilhas.isEmpty() ? List.of()
                : igrejaRepository.findAllById(idsFilhas).stream().map(Igreja::getNome).toList();
        if (!idsFilhas.isEmpty()) {
            igrejaRepository.desvincularFamiliaEmLote(idsFilhas);
            log.info("Igrejas vinculadas desvinculadas da família. igreja_mae_id={}, filhas={}", igrejaId, idsFilhas.size());
        }

        // Antes de apagar os usuários, zera as FKs de `igreja` que apontam pra eles
        // (atualizado_por, vinculado_por, exclusao_agendada_por) — senão o DELETE viola a FK.
        igrejaRepository.limparReferenciasDeUsuario(igrejaId);

        usuarioRepository.deleteAllByIgrejaId(igrejaId);
        pessoaRepository.deleteAllByIgrejaId(igrejaId);

        // Fotos por último entre os passos de banco: pessoa.foto_id é ON DELETE RESTRICT,
        // então só dá pra apagar a foto depois que a pessoa (e a igreja/logo) já sumiram.
        igrejaRepository.limparLogoFoto(igrejaId);
        for (var foto : fotoRepository.findByIgrejaId(igrejaId)) {
            fotoService.remover(foto.getId());
        }

        String idTexto = igrejaId.toString();
        for (Runnable limpezaIndice : List.<Runnable>of(
                () -> pessoaSearchRepository.deleteByIgrejaId(idTexto),
                () -> eventoSearchRepository.deleteByIgrejaId(idTexto),
                () -> usuarioSearchRepository.deleteByIgrejaId(idTexto),
                () -> movimentacaoSearchRepository.deleteByIgrejaId(idTexto),
                () -> categoriaSearchRepository.deleteByIgrejaId(idTexto),
                () -> celulaSearchRepository.deleteByIgrejaId(idTexto),
                () -> ministerioSearchRepository.deleteByIgrejaId(idTexto),
                () -> visitanteSearchRepository.deleteByIgrejaId(idTexto)
        )) {
            try {
                limpezaIndice.run();
            } catch (Exception e) {
                log.error("Falha ao limpar índice do Elasticsearch na purga da igreja — seguindo. igreja_id={}", igrejaId, e);
            }
        }

        igrejaRepository.findById(igrejaId).ifPresent(igreja -> {
            String assunto = "Sua igreja foi excluída";
            String corpo = corpoEmailConcluido(igreja.getNome(), nomesFilhas);
            for (String destinatario : destinatarios(igreja.getEmailContato(), emailsAdmins)) {
                try {
                    emailService.enviar(destinatario, assunto, corpo);
                } catch (Exception e) {
                    log.error("Falha ao enviar e-mail de exclusão concluída — a purga segue mesmo assim. igreja_id={}, destinatario={}",
                            igrejaId, destinatario, e);
                }
            }
        });

        igrejaRepository.deleteById(igrejaId);
        log.warn("Purga definitiva da igreja concluída. igreja_id={}", igrejaId);
    }

    /** Corpo do e-mail final: sem botão (nada mais pra cancelar), com o aviso de família se
     *  a igreja era mãe, e uma despedida. Conteúdo pensado pra evoluir junto com o produto. */
    private String corpoEmailConcluido(String nomeIgreja, List<String> nomesFilhas) {
        String avisoFamilia = nomesFilhas.isEmpty() ? "" : """
                <p>As igrejas vinculadas (%s) continuam funcionando normalmente — elas só
                   deixaram de estar ligadas a "%s", com todos os dados intactos.</p>
                """.formatted(String.join(", ", nomesFilhas), nomeIgreja);

        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                  <h2>Exclusão concluída</h2>
                  <p>A exclusão definitiva de "%s" foi concluída. Todos os dados foram removidos
                     permanentemente — esta ação não pode ser desfeita.</p>
                  %s
                  <p style="color: #666; font-size: 14px;">Obrigado por ter usado o Domus. Se quiser
                     voltar no futuro, é só fazer um novo cadastro quando quiser.</p>
                </div>
                """.formatted(nomeIgreja, avisoFamilia);
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
}
