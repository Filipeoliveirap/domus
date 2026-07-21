package com.domus.api.modules.igreja.DTO;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.DTO.EnderecoDTO;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A igreja inteira, para a tela de Configurações → Dados da Igreja.
 *
 * <p>Separado do {@link IgrejaDTO}, que é o resumo público (e cacheado) usado no cadastro.
 * Este aqui carrega o endereço e a auditoria — dado que só o admin da própria igreja vê.
 */
public record IgrejaDetalheDTO(
        UUID id,
        String nome,
        String razaoSocial,
        String cnpj,
        String denominacao,
        String emailContato,
        String telefoneContato,
        String logoUrl,
        EnderecoDTO endereco,
        // Alimenta o card "Logs de atividade".
        LocalDateTime atualizadoEm,
        String atualizadoPorNome) {

    public static IgrejaDetalheDTO from(Igreja igreja, String atualizadoPorNome) {
        var e = igreja.getEndereco();
        return new IgrejaDetalheDTO(
                igreja.getId(),
                igreja.getNome(),
                igreja.getRazaoSocial(),
                igreja.getCnpj(),
                igreja.getDenominacao(),
                igreja.getEmailContato(),
                igreja.getTelefoneContato(),
                igreja.getLogoUrl(),
                e == null ? null : new EnderecoDTO(
                        e.getCep(), e.getLogradouro(), e.getNumero(), e.getComplemento(),
                        e.getBairro(), e.getCidade(), e.getUf()),
                igreja.getUpdatedAt(),
                atualizadoPorNome);
    }
}
