package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.exclusao.DTO.ResumoExclusaoResponse;
import com.domus.api.modules.ministerio.MinisterioRepository;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.email.EmailService;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional(readOnly = true)
    public ResumoExclusaoResponse resumo(UUID igrejaId) {
        List<String> nomesFilhas = igrejaRepository.buscarIdsDasFilhas(igrejaId).isEmpty()
                ? List.of()
                : igrejaRepository.findAllById(igrejaRepository.buscarIdsDasFilhas(igrejaId))
                        .stream().map(Igreja::getNome).toList();

        return new ResumoExclusaoResponse(
                pessoaRepository.countByIgrejaId(igrejaId),
                eventoRepository.countByIgrejaId(igrejaId),
                movimentacaoRepository.countByIgrejaId(igrejaId),
                celulaRepository.countByIgrejaId(igrejaId),
                ministerioRepository.countByIgrejaId(igrejaId),
                usuarioRepository.countByIgrejaId(igrejaId),
                nomesFilhas
        );
    }

    @Transactional
    public void agendar(UUID igrejaId, UUID usuarioId, String nomeConfirmacao) {
        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        if (!normalizarNome(igreja.getNome()).equals(normalizarNome(nomeConfirmacao))) {
            throw new BusinessException("NOME_NAO_CONFERE",
                    "O nome digitado não confere com o nome da igreja.");
        }

        igrejaRepository.marcarExclusaoAgendada(igrejaId, usuarioId, LocalDateTime.now());
        log.info("Exclusão agendada. igreja_id={}, por_usuario_id={}", igrejaId, usuarioId);

        emailService.enviar(igreja.getEmailContato(), "Exclusão da sua igreja no Domus foi agendada",
                "A exclusão definitiva de \"" + igreja.getNome() + "\" foi agendada e acontecerá em 10 dias. "
                        + "Você pode cancelar a qualquer momento antes disso, entrando em Configurações → Sistema.");
    }

    @Transactional
    public void cancelar(UUID igrejaId) {
        igrejaRepository.cancelarExclusaoAgendada(igrejaId);
        log.info("Exclusão cancelada. igreja_id={}", igrejaId);
    }

    private String normalizarNome(String nome) {
        return nome == null ? "" : nome.trim().toLowerCase();
    }
}
