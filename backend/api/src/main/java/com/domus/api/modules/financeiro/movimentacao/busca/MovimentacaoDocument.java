package com.domus.api.modules.financeiro.movimentacao.busca;

import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "movimentacoes")
@Setting(settingPath = "elasticsearch/domus-analyzer.json")
@Getter
@Setter
@NoArgsConstructor
public class MovimentacaoDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String igrejaId;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String descricao;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String categoriaNome;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String pessoaNome;

    @Field(type = FieldType.Keyword)
    private String tipo;

    public static MovimentacaoDocument de(MovimentacaoFinanceira mov) {
        MovimentacaoDocument doc = new MovimentacaoDocument();
        doc.setId(mov.getId().toString());
        doc.setIgrejaId(mov.getIgreja().getId().toString());
        doc.setDescricao(mov.getDescricao());
        doc.setCategoriaNome(mov.getCategoria() != null ? mov.getCategoria().getNome() : null);
        doc.setPessoaNome(mov.getPessoa() != null ? mov.getPessoa().getNome() : null);
        doc.setTipo(mov.getTipo() != null ? mov.getTipo().name() : null);
        return doc;
    }
}