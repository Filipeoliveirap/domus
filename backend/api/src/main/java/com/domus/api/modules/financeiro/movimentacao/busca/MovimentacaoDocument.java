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

import java.util.List;

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
    private List<String> pessoaNomes;

    @Field(type = FieldType.Keyword)
    private String tipo;

    /**
     * @param categoriaNome        resolvido à parte pelo chamador (bypass do @SQLRestriction
     *                              quando a categoria está arquivada) — nunca usar
     *                              mov.getCategoria().getNome() aqui.
     * @param nomesPorPessoa        idem, pra cada contribuinte — pessoa arquivada (mas não
     *                              excluída) precisa continuar indexando o nome real.
     */
    public static MovimentacaoDocument de(MovimentacaoFinanceira mov, String categoriaNome,
                                           java.util.Map<java.util.UUID, String> nomesPorPessoa) {
        MovimentacaoDocument doc = new MovimentacaoDocument();
        doc.setId(mov.getId().toString());
        doc.setIgrejaId(mov.getIgreja().getId().toString());
        doc.setDescricao(mov.getDescricao());
        doc.setCategoriaNome(categoriaNome);
        doc.setPessoaNomes(mov.getContribuintes().stream()
                .map(c -> c.getPessoa())
                .filter(p -> p != null)
                .map(p -> nomesPorPessoa.get(p.getId()))
                .filter(nome -> nome != null)
                .toList());
        doc.setTipo(mov.getTipo() != null ? mov.getTipo().name() : null);
        return doc;
    }
}