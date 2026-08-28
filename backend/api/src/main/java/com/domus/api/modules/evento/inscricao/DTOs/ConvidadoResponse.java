package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import java.util.UUID;

public record ConvidadoResponse(
        UUID inscricaoId,
        String nome,
        String telefone,
        UUID cobrancaId,
        String tokenLinkPublico
) {
    public static ConvidadoResponse from(InscricaoEvento i, CobrancaEvento cobrancaOuNull) {
        return new ConvidadoResponse(
                i.getId(), i.getNomeConvidado(), i.getTelefoneConvidado(),
                cobrancaOuNull != null ? cobrancaOuNull.getId() : null,
                cobrancaOuNull != null ? cobrancaOuNull.getTokenLinkPublico() : null
        );
    }
}
