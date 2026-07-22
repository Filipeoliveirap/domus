package com.domus.api.modules.evento.local.DTOs;

import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Endereco;
import java.util.UUID;

/**
 * @param endereco        já resolvido: o próprio (texto livre cadastrado no local), ou o da
 *                        igreja (formatado a partir do {@link Endereco} estruturado) quando
 *                        herdado
 * @param enderecoHerdado true quando o endereço veio da igreja — a tela avisa o usuário
 */
public record LocalEventoResponse(
        UUID id, String nome, Integer capacidade,
        String endereco, boolean enderecoHerdado
) {

    public static LocalEventoResponse from(LocalEvento local) {
        if (local.temEnderecoProprio()) {
            return new LocalEventoResponse(
                    local.getId(), local.getNome(), local.getCapacidade(),
                    local.getCepLogradouroNumero(), false);
        }

        // Sem endereço próprio: o "Santuário Principal" É o endereço da igreja — herda dela.
        // A igreja guarda endereço como Endereco @Embeddable (colunas estruturadas), diferente
        // do local (texto livre) — por isso formata aqui em vez de reaproveitar o getter.
        Igreja igreja = local.getIgreja();
        return new LocalEventoResponse(
                local.getId(), local.getNome(), local.getCapacidade(),
                formatarEnderecoDaIgreja(igreja.getEndereco()), true);
    }

    /** Junta o Endereco estruturado da igreja num texto único, no mesmo espírito do campo
     * livre que o local usa quando tem endereço próprio. Ignora partes ausentes. */
    private static String formatarEnderecoDaIgreja(Endereco e) {
        if (e == null) return null;

        StringBuilder logradouroNumero = new StringBuilder();
        if (naoVazio(e.getLogradouro())) logradouroNumero.append(e.getLogradouro());
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
