package com.domus.api.modules.pagamento.cobranca;

import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.AcompanhanteRepository;
import com.domus.api.modules.pagamento.cobranca.DTOs.CobrancaPublicaDTO;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.*;

/**
 * Rota pública (sem autenticação) consumida pela página de checkout do pagador —
 * ver Task 14 no front. Por ser pública, o DTO devolvido carrega estritamente o
 * necessário para montar a tela: nunca telefone/e-mail/outros campos de Pessoa ou
 * AcompanhanteInscricao além do nome.
 */
@RestController
@RequestMapping("/cobrancas")
public class CobrancaController {

    private final CobrancaEventoService service;
    private final EventoRepository eventoRepository;
    private final PessoaRepository pessoaRepository;
    private final AcompanhanteRepository acompanhanteRepository;

    public CobrancaController(CobrancaEventoService service,
                               EventoRepository eventoRepository,
                               PessoaRepository pessoaRepository,
                               AcompanhanteRepository acompanhanteRepository) {
        this.service = service;
        this.eventoRepository = eventoRepository;
        this.pessoaRepository = pessoaRepository;
        this.acompanhanteRepository = acompanhanteRepository;
    }

    @GetMapping("/{token}")
    public CobrancaPublicaDTO buscar(@PathVariable String token) {
        var cobranca = service.buscarPorToken(token);

        var evento = eventoRepository.findById(cobranca.getEventoId())
            .orElseThrow(() -> new ResourceNotFoundException("Evento da cobrança não encontrado."));

        String nomePagador;
        if (cobranca.getPessoaId() != null) {
            nomePagador = pessoaRepository.findById(cobranca.getPessoaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagador da cobrança não encontrado."))
                .getNome();
        } else {
            nomePagador = acompanhanteRepository.findById(cobranca.getAcompanhanteId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagador da cobrança não encontrado."))
                .getNome();
        }

        return new CobrancaPublicaDTO(
            evento.getTitulo(),
            nomePagador,
            cobranca.getValor(),
            cobranca.getStatus().name(),
            cobranca.getExpiraEm()
        );
    }
}
