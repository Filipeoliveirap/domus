package com.domus.api.modules.usuario.DTO;

import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.usuario.Usuario;

import java.util.UUID;

/** DTO enxuto pra tela de Arquivados. */
public record UsuarioArquivadoResponse(
        UUID id,
        String nome,
        String email,
        String role
) {
    private static final String NOME_PESSOA_REMOVIDA = "Pessoa removida do sistema";

    /**
     * @param pessoaResolvida resolvida em lote pelo chamador via bypass do @SQLRestriction —
     *                        {@code u.getPessoa().getNome()} direto quebraria se a pessoa
     *                        também tivesse sido arquivada nesse meio tempo.
     */
    public static UsuarioArquivadoResponse de(Usuario u, Pessoa pessoaResolvida) {
        return new UsuarioArquivadoResponse(
                u.getId(),
                pessoaResolvida != null ? pessoaResolvida.getNome() : NOME_PESSOA_REMOVIDA,
                pessoaResolvida != null ? pessoaResolvida.getEmail() : null,
                u.getRole().getNome()
        );
    }
}
