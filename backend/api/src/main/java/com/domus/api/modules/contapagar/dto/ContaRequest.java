package com.domus.api.modules.contapagar.dto;

import com.domus.api.modules.contapagar.RecorrenciaFrequencia;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Request para criar ou editar uma conta a pagar. */
public record ContaRequest(
    @NotNull(message = "Categoria é obrigatória.") UUID categoriaId,
    @Valid @NotNull(message = "Beneficiário é obrigatório.") BeneficiarioDTO beneficiario,
    @Size(max = 20, message = "CNPJ do beneficiário deve ter no máximo 20 caracteres.") String cnpjBeneficiario,
    @Size(max = 255, message = "Descrição deve ter no máximo 255 caracteres.") String descricao,
    @NotNull(message = "Valor é obrigatório.") @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero.") BigDecimal valor,
    @NotNull(message = "Vencimento é obrigatório.") @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vencimento,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate competencia,
    @Size(max = 80, message = "Linha digitável deve ter no máximo 80 caracteres.") String linhaDigitavel,
    @Size(max = 40, message = "Número do documento deve ter no máximo 40 caracteres.") String documentoNumero,
    @Size(max = 255, message = "Observações devem ter no máximo 255 caracteres.") String observacoes,
    UUID anexoId,
    RecorrenciaDTO recorrencia
) {
    /** Validações cross-field que não cabem em anotação isolada. */
    @AssertTrue(message = "Beneficiário: preencha pessoa ou texto, não os dois.")
    public boolean xorBeneficiario() {
        int preenchidos = 0;
        if (beneficiario != null) {
            if (beneficiario.pessoaId() != null) preenchidos++;
            if (beneficiario.texto() != null && !beneficiario.texto().isBlank()) preenchidos++;
        }
        return preenchidos == 1;
    }

    @AssertTrue(message = "Beneficiário é obrigatório.")
    public boolean beneficiarioPresente() {
        if (beneficiario == null) return false;
        return beneficiario.pessoaId() != null || (beneficiario.texto() != null && !beneficiario.texto().isBlank());
    }

    @AssertTrue(message = "Dia-âncora é obrigatório para vencimento em dia 29, 30 ou 31.")
    public boolean diaAncoraObrigatorioQuandoVencimentoFimDeMes() {
        if (vencimento == null || recorrencia == null) return true;
        int dia = vencimento.getDayOfMonth();
        if (dia >= 29 && recorrencia.diaAncora() == null) return false;
        return true;
    }

    @AssertTrue(message = "Dia-âncora deve estar entre 1 e 28.")
    public boolean diaAncoraValido() {
        if (recorrencia == null || recorrencia.diaAncora() == null) return true;
        return recorrencia.diaAncora() >= 1 && recorrencia.diaAncora() <= 28;
    }

    @AssertTrue(message = "Recorrência requer frequência.")
    public boolean recorrenciaInconsistente() {
        if (recorrencia == null) return true;
        // Se recorrencia != null, frequencia é obrigatória
        return recorrencia.frequencia() != null;
    }
}
