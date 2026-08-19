package com.domus.api.modules.visitante.busca;

import com.domus.api.modules.visitante.Visitante;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "visitantes")
@Setting(settingPath = "elasticsearch/domus-analyzer.json")
@Getter
@Setter
@NoArgsConstructor
public class VisitanteDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String igrejaId;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String nome;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String telefone;

    // Não indexado pra busca textual — só carregado de volta pra montar a rota certa
    // no front (visitante numa célula não aparece mais na listagem de visitantes).
    @Field(type = FieldType.Keyword, index = false)
    private String celulaId;

    public static VisitanteDocument de(Visitante visitante, java.util.UUID celulaId) {
        VisitanteDocument doc = new VisitanteDocument();
        doc.setId(visitante.getId().toString());
        doc.setIgrejaId(visitante.getIgreja().getId().toString());
        doc.setNome(visitante.getNome());
        doc.setTelefone(visitante.getTelefone());
        doc.setCelulaId(celulaId != null ? celulaId.toString() : null);
        return doc;
    }
}
