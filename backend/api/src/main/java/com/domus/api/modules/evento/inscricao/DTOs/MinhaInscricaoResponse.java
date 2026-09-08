package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import java.util.UUID;

/**
 * O que o próprio usuário vê sobre a sua inscrição no evento.
 *
 * <p>{@code cobrancaPendenteId} (Task 14) é a lacuna que faltava pro front conseguir abrir
 * o Payment Brick do titular logo após a inscrição num evento pago: sem esse id, o front
 * não tinha como saber QUAL {@code CobrancaEvento} chamar em
 * {@code POST /cobrancas/{id}/pagar} — {@code null} quando o evento é gratuito ou já não
 * há cobrança pendente (paga/cancelada/expirada).
 */
public record MinhaInscricaoResponse(
        UUID id,
        boolean inscrito,
        UUID cobrancaPendenteId,
        /** Evento formal (inscrição) vs. só "marcar presença" ("Eu vou") — o front usa pra
         *  não mostrar o selo "Você está inscrito" quando a pessoa só confirmou presença. */
        boolean requerInscricao
) {
    public static MinhaInscricaoResponse from(InscricaoEvento i) {
        return from(i, null);
    }

    public static MinhaInscricaoResponse from(InscricaoEvento i, UUID cobrancaPendenteId) {
        return new MinhaInscricaoResponse(
                i.getId(),
                i.estaConfirmada(),
                cobrancaPendenteId,
                i.getEvento() != null && i.getEvento().isRequerInscricao()
        );
    }

    public static MinhaInscricaoResponse naoInscrito() {
        return new MinhaInscricaoResponse(null, false, null, false);
    }
}
