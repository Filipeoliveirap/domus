package com.domus.api.modules.financeiro.movimentacao.busca;

import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "movimentacoes")
@Getter
@Setter
@NoArgsConstructor
public class MovimentacaoDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String igrejaId;

    @Field(type = FieldType.Text)
    private String descricao;

    @Field(type = FieldType.Text)
    private String categoriaNome;

    @Field(type = FieldType.Text)
    private String membroNome;

    @Field(type = FieldType.Keyword)
    private String tipo;

    public static MovimentacaoDocument de(MovimentacaoFinanceira mov) {
        MovimentacaoDocument doc = new MovimentacaoDocument();
        doc.setId(mov.getId().toString());
        doc.setIgrejaId(mov.getIgreja().getId().toString());
        doc.setDescricao(mov.getDescricao());
        doc.setCategoriaNome(mov.getCategoria() != null ? mov.getCategoria().getNome() : null);
        doc.setMembroNome(mov.getMembro() != null ? mov.getMembro().getNome() : null);
        doc.setTipo(mov.getTipo() != null ? mov.getTipo().name() : null);
        return doc;
    }
}