package com.domus.api.modules.pessoa.DTO;

import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;

import java.util.UUID;

/** DTO enxuto pra tela de Arquivados — sem endereço/foto/vínculo detalhado de {@link PessoaResponse}. */
public record PessoaArquivadaResponse(
        UUID id,
        String nome,
        String email,
        boolean temVinculo,
        boolean temUsuario,
        boolean temContribuicao,
        boolean temInscricao,
        boolean temCelula,
        boolean temMinisterio
) {
    public static PessoaArquivadaResponse de(Pessoa p, PessoaRepository.VinculosPessoa v) {
        boolean temVinculo = v.getTemUsuario() || v.getTemContribuicao()
                || v.getTemInscricao() || v.getTemCelula() || v.getTemMinisterio();
        return new PessoaArquivadaResponse(
                p.getId(), p.getNome(), p.getEmail(), temVinculo,
                v.getTemUsuario(), v.getTemContribuicao(),
                v.getTemInscricao(), v.getTemCelula(), v.getTemMinisterio()
        );
    }
}
