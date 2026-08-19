package com.domus.api.modules.celula.busca;

import com.domus.api.modules.celula.Celula;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "celulas")
@Setting(settingPath = "elasticsearch/domus-analyzer.json")
@Getter
@Setter
@NoArgsConstructor
public class CelulaDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String igrejaId;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String nome;

    @Field(type = FieldType.Keyword)
    private String diaSemana;

    public static CelulaDocument de(Celula celula) {
        CelulaDocument doc = new CelulaDocument();
        doc.setId(celula.getId().toString());
        doc.setIgrejaId(celula.getIgreja().getId().toString());
        doc.setNome(celula.getNome());
        doc.setDiaSemana(celula.getDiaSemana() != null ? celula.getDiaSemana().name() : null);
        return doc;
    }
}
