package com.domus.api.modules.membro;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Endereço do membro como objeto de valor. É {@code @Embeddable}: as 7 colunas vivem na
 * própria tabela {@code membro} (sem JOIN), mas o código trata endereço como um conceito só.
 * Tudo nulável — um membro pode ter endereço parcial ou nenhum.
 */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Endereco {

    @Column(name = "cep", length = 9)
    private String cep;

    @Column(name = "logradouro", length = 255)
    private String logradouro;

    @Column(name = "numero", length = 20)
    private String numero;

    @Column(name = "complemento", length = 255)
    private String complemento;

    @Column(name = "bairro", length = 255)
    private String bairro;

    @Column(name = "cidade", length = 255)
    private String cidade;

    @Column(name = "uf", length = 2)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String uf;
}
