package com.domus.api.modules.igreja.DTO;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.DTO.EnderecoDTO;

import java.time.LocalDateTime;
import java.util.UUID;

/** Diferente do {@link IgrejaDTO} (resumo público cacheado): carrega endereço e auditoria, visível só ao admin da própria igreja. */
public record IgrejaDetalheDTO(
        UUID id,
        String nome,
        String razaoSocial,
        String cnpj,
        String denominacao,
        String sigla,
        String emailContato,
        String telefoneContato,
        UUID logoFotoId,
        EnderecoDTO endereco,
        // Alimenta o card "Logs de atividade".
        LocalDateTime atualizadoEm,
        String atualizadoPorNome,
        LocalDateTime exclusaoAgendadaEm,
        Integer diasRestantes,
        RotulosDTO rotulos) {

    public static IgrejaDetalheDTO from(Igreja igreja, String atualizadoPorNome) {
        var e = igreja.getEndereco();

        Integer diasRestantes = null;
        if (igreja.getExclusaoAgendadaEm() != null) {
            long decorridos = java.time.temporal.ChronoUnit.DAYS.between(
                    igreja.getExclusaoAgendadaEm(), LocalDateTime.now());
            diasRestantes = (int) Math.max(0, 10 - decorridos);
        }

        return new IgrejaDetalheDTO(
                igreja.getId(),
                igreja.getNome(),
                igreja.getRazaoSocial(),
                igreja.getCnpj(),
                igreja.getDenominacao(),
                igreja.getSigla(),
                igreja.getEmailContato(),
                igreja.getTelefoneContato(),
                igreja.getLogoFoto() == null ? null : igreja.getLogoFoto().getId(),
                e == null ? null : new EnderecoDTO(
                        e.getCep(), e.getLogradouro(), e.getNumero(), e.getComplemento(),
                        e.getBairro(), e.getCidade(), e.getUf()),
                igreja.getUpdatedAt(),
                atualizadoPorNome,
                igreja.getExclusaoAgendadaEm(),
                diasRestantes,
                RotulosDTO.from(igreja));
    }
}
