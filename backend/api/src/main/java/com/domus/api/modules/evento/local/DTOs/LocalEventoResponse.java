package com.domus.api.modules.evento.local.DTOs;

import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Endereco;
import java.util.UUID;

/**
 * @param endereco                 já resolvido para EXIBIÇÃO: o próprio, ou o da igreja
 *                                 (formatado do {@link Endereco} estruturado) quando herdado
 * @param enderecoHerdado          true quando o endereço veio da igreja — a tela avisa o usuário
 * @param cepLogradouroNumero      campo CRU do local, para REIDRATAR o formulário de edição.
 *                                 null quando o local não tem endereço próprio (herda da igreja)
 * @param complementoBairroCidadeUf idem — campo cru para edição
 *
 * <p><b>Por que os campos crus além do `endereco` formatado:</b> o `endereco` colapsa os dois
 * campos num texto só, do qual o formulário não consegue reconstruir as duas partes. Sem os
 * crus, editar um local só para mudar a capacidade reabria o complemento vazio e o PUT o
 * apagava em silêncio. Os crus são a fonte fiel para o form; o `endereco` continua para a lista
 * e o detalhe.
 */
public record LocalEventoResponse(
        UUID id, String nome, Integer capacidade,
        String endereco, boolean enderecoHerdado,
        String cepLogradouroNumero, String complementoBairroCidadeUf
) {

    public static LocalEventoResponse from(LocalEvento local) {
        if (local.temEnderecoProprio()) {
            return new LocalEventoResponse(
                    local.getId(), local.getNome(), local.getCapacidade(),
                    local.getCepLogradouroNumero(), false,
                    local.getCepLogradouroNumero(), local.getComplementoBairroCidadeUf());
        }

        // Sem endereço próprio: o "Santuário Principal" É o endereço da igreja — herda dela.
        // A igreja guarda endereço como Endereco @Embeddable (colunas estruturadas), diferente
        // do local (texto livre) — por isso formata aqui em vez de reaproveitar o getter.
        // Os campos crus vão null: não há endereço PRÓPRIO para o form reidratar.
        Igreja igreja = local.getIgreja();
        return new LocalEventoResponse(
                local.getId(), local.getNome(), local.getCapacidade(),
                formatarEnderecoDaIgreja(igreja.getEndereco()), true,
                null, null);
    }

    /** Junta o Endereco estruturado da igreja num texto único, no mesmo espírito do campo
     * livre que o local usa quando tem endereço próprio. Ignora partes ausentes. */
    private static String formatarEnderecoDaIgreja(Endereco e) {
        if (e == null) return null;

        // CEP entra na mesma posição em que aparece no campo espelhado do local
        // (cepLogradouroNumero) — sem isso, local com endereço PRÓPRIO mostra CEP e local
        // HERDADO da igreja não, uma assimetria que não faz sentido pro usuário.
        StringBuilder logradouroNumero = new StringBuilder();
        if (naoVazio(e.getCep())) logradouroNumero.append(e.getCep());
        if (naoVazio(e.getLogradouro())) {
            if (!logradouroNumero.isEmpty()) logradouroNumero.append(", ");
            logradouroNumero.append(e.getLogradouro());
        }
        if (naoVazio(e.getNumero())) {
            if (!logradouroNumero.isEmpty()) logradouroNumero.append(", ");
            logradouroNumero.append(e.getNumero());
        }

        StringBuilder resto = new StringBuilder();
        if (naoVazio(e.getComplemento())) resto.append(e.getComplemento());
        if (naoVazio(e.getBairro())) {
            if (!resto.isEmpty()) resto.append(", ");
            resto.append(e.getBairro());
        }
        if (naoVazio(e.getCidade())) {
            if (!resto.isEmpty()) resto.append(", ");
            resto.append(e.getCidade());
            if (naoVazio(e.getUf())) resto.append("/").append(e.getUf());
        }

        if (logradouroNumero.isEmpty() && resto.isEmpty()) return null;
        if (logradouroNumero.isEmpty()) return resto.toString();
        if (resto.isEmpty()) return logradouroNumero.toString();
        return logradouroNumero + " - " + resto;
    }

    private static boolean naoVazio(String s) {
        return s != null && !s.isBlank();
    }
}
