package com.domus.api.modules.pagamento.cobranca.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /cobrancas/{id}/pagar} (Task 14) — o payload que o Payment Brick
 * devolve em {@code onSubmit({ formData })} no front, repassado quase igual pro backend.
 * {@code token} e {@code installments} vêm nulos quando o meio escolhido é PIX (o Brick
 * não tokeniza cartão nesse caso); {@code paymentMethodId} vem sempre (ex.: {@code "pix"},
 * {@code "visa"}, {@code "master"}).
 *
 * <p><b>Validação (2026-09-07):</b> os campos vão quase crus pro Mercado Pago. Sem as
 * anotações abaixo, input malformado só falhava lá na borda da API do MP, virando erro
 * genérico opaco pro usuário. Não é uma brecha de segurança (o valor cobrado sempre vem
 * de {@code cobranca.getValor()} no servidor, nunca do request) — o ganho é só um 400
 * limpo em vez do erro genérico. Só {@code paymentMethodId} e {@code payerEmail} são
 * obrigatórios: os dois existem nos dois fluxos (cartão e Pix) e o MP exige ambos de
 * qualquer forma. {@code token}/{@code installments}/{@code issuerId} chegam nulos no
 * Pix, então só ganham teto de tamanho/sinal, nunca {@code @NotNull}.
 */
public record PagarCobrancaRequest(
    @Size(max = 255, message = "token inválido")
    String token,

    @NotBlank(message = "meio de pagamento é obrigatório")
    @Size(max = 50, message = "meio de pagamento inválido")
    String paymentMethodId,

    @Positive(message = "número de parcelas inválido")
    Integer installments,

    @NotBlank(message = "e-mail do pagador é obrigatório")
    @Email(message = "e-mail do pagador inválido")
    @Size(max = 254, message = "e-mail do pagador inválido")
    String payerEmail,

    /** {@code formData.issuer_id} do Brick — nulo pra Pix. Sem ele, o Mercado Pago falha o
     *  cálculo de parcelamento/preço pra alguns bancos emissores ({@code error_pricing},
     *  código 10107) mesmo com token/cartão válidos. */
    @Size(max = 32, message = "emissor inválido")
    String issuerId
) {}
