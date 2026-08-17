package com.domus.api.modules.ministerio.busca;

import com.domus.api.modules.ministerio.Ministerio;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "ministerios")
@Setting(settingPath = "elasticsearch/domus-analyzer.json")
@Getter
@Setter
@NoArgsConstructor
public class MinisterioDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String igrejaId;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String nome;

    public static MinisterioDocument de(Ministerio ministerio) {
        MinisterioDocument doc = new MinisterioDocument();
        doc.setId(ministerio.getId().toString());
        doc.setIgrejaId(ministerio.getIgreja().getId().toString());
        doc.setNome(ministerio.getNome());
        return doc;
    }
}
