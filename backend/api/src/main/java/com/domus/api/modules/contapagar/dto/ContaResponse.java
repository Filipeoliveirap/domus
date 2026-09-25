package com.domus.api.modules.contapagar.dto;

import com.domus.api.modules.contapagar.ContaAPagar;
import com.domus.api.modules.contapagar.RecorrenciaFrequencia;
import com.domus.api.modules.contapagar.StatusConta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Resposta de uma conta a pagar. */
public record ContaResponse(
    UUID id,
    UUID categoriaId,
    String categoriaNome,
    BeneficiarioResponse beneficiario,
    String cnpjBeneficiario,
    String descricao,
    BigDecimal valor,
    BigDecimal valorPago,
    LocalDate vencimento,
    Integer diasParaVencimento,
    Boolean atrasada,
    String status,
    LocalDate competencia,
    String linhaDigitavel,
    String documentoNumero,
    String observacoes,
    UUID anexoId,
    AnexoResponse anexo,
    RecorrenciaResponse recorrencia,
    Boolean podeReceberPagamento,
    Boolean divergeDaSerie,
    String criadoPorNome,
    LocalDateTime criadoEm,
    LocalDateTime atualizadoEm,
    List<PagamentoResponse> pagamentos
) {
    public static ContaResponse from(ContaAPagar conta) {
        return from(conta, null);
    }

    public static ContaResponse from(ContaAPagar conta, List<PagamentoResponse> pagamentos) {
        LocalDate hoje = LocalDate.now();
        boolean atrasada = conta.getStatus() == StatusConta.EM_ABERTO
                           && conta.getVencimento().isBefore(hoje);
        Integer dias = null;
        if (conta.getStatus() == StatusConta.EM_ABERTO) {
            dias = (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, conta.getVencimento());
        }

        BeneficiarioResponse benefResp = new BeneficiarioResponse(
            conta.getBeneficiarioPessoa() != null ? conta.getBeneficiarioPessoa().getId() : null,
            conta.getBeneficiarioPessoa() != null ? conta.getBeneficiarioPessoa().getNome() : null,
            conta.getBeneficiarioTexto(),
            conta.getBeneficiarioTexto() != null
                && conta.getBeneficiarioTexto().equals("Pessoa removida do sistema")
        );

        RecorrenciaResponse recurResp = null;
        if (conta.getRecorrenciaFrequencia() != null) {
            int total = conta.getRecorrenciaVezes() != null ? conta.getRecorrenciaVezes() : 0;
            // ocorrencias restantes = teto - ja materializadas (aproximado)
            int restantes = total > 0 ? total - 1 : null; // -1 porque a geradora conta
            recurResp = new RecorrenciaResponse(
                conta.getRecorrenciaFrequencia().name(),
                conta.getRecorrenciaAte(),
                conta.getRecorrenciaVezes(),
                conta.getRecorrenciaDiaAncora(),
                restantes,
                conta.ehGeradora()
            );
        }

        String status = conta.getStatus().name();
        // Se tem pagamento parcial mas não está paga, mostra PARCIAL
        if (conta.getStatus() == StatusConta.EM_ABERTO
            && conta.getValorPago() != null
            && conta.getValorPago().compareTo(BigDecimal.ZERO) > 0) {
            status = "PARCIAL";
        }

        return new ContaResponse(
            conta.getId(),
            conta.getCategoria() != null ? conta.getCategoria().getId() : null,
            conta.getCategoria() != null ? conta.getCategoria().getNome() : null,
            benefResp,
            conta.getCnpjBeneficiario(),
            conta.getDescricao(),
            conta.getValor(),
            conta.getValorPago(),
            conta.getVencimento(),
            dias,
            atrasada,
            status,
            conta.getCompetencia(),
            conta.getLinhaDigitavel(),
            conta.getDocumentoNumero(),
            conta.getObservacoes(),
            conta.getAnexo() != null ? conta.getAnexo().getId() : null,
            AnexoResponse.from(conta.getAnexo()),
            recurResp,
            conta.getStatus() != StatusConta.PAGA,
            conta.getDivergeDaSerie(),
            conta.getCriadoPorTexto(),
            conta.getCreatedAt(),
            conta.getUpdatedAt(),
            pagamentos
        );
    }
}
