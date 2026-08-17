package com.domus.api.modules.evento.local.DTOs;

import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.shared.dominio.Endereco;
import java.util.UUID;

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

        Igreja igreja = local.getIgreja();
        return new LocalEventoResponse(
                local.getId(), local.getNome(), local.getCapacidade(),
                formatarEnderecoDaIgreja(igreja.getEndereco()), true,
                null, null);
    }

    private static String formatarEnderecoDaIgreja(Endereco e) {
        if (e == null) return null;

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
