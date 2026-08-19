package com.domus.api.shared.dominio;

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
 * Endereço como objeto de valor, reusado por Pessoa, Igreja, LocalEvento e Visitante.
 * É {@code @Embeddable}: as 7 colunas vivem na própria tabela de quem o incorpora (sem
 * JOIN), mas o código trata endereço como um conceito só. Tudo nulável.
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
