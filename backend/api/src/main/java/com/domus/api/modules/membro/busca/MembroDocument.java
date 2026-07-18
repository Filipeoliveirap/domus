package com.domus.api.modules.membro.busca;

import com.domus.api.modules.membro.Membro;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "membros")
@Setting(settingPath = "elasticsearch/domus-analyzer.json")
@Getter
@Setter
@NoArgsConstructor
public class MembroDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String igrejaId;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String nome;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String email;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String telefone;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String ministerio;

    @Field(type = FieldType.Keyword)
    private String status;

    public static MembroDocument de(Membro membro) {
        MembroDocument doc = new MembroDocument();
        doc.setId(membro.getId().toString());
        doc.setIgrejaId(membro.getIgreja().getId().toString());
        doc.setNome(membro.getNome());
        doc.setEmail(membro.getEmail());
        doc.setTelefone(membro.getTelefone());
        doc.setMinisterio(membro.getMinisterio());
        doc.setStatus(membro.getStatus() != null ? membro.getStatus().name() : null);
        return doc;
    }
}