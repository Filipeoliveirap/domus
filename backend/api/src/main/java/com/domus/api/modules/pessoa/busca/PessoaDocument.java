package com.domus.api.modules.pessoa.busca;

import com.domus.api.modules.pessoa.Pessoa;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "pessoas")
@Setting(settingPath = "elasticsearch/domus-analyzer.json")
@Getter
@Setter
@NoArgsConstructor
public class PessoaDocument {

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
    private String cargo;

    @Field(type = FieldType.Keyword)
    private String vinculo;

    public static PessoaDocument de(Pessoa membro) {
        PessoaDocument doc = new PessoaDocument();
        doc.setId(membro.getId().toString());
        doc.setIgrejaId(membro.getIgreja().getId().toString());
        doc.setNome(membro.getNome());
        doc.setEmail(membro.getEmail());
        doc.setTelefone(membro.getTelefone());
        doc.setCargo(membro.getCargo());
        doc.setVinculo(membro.getVinculo() != null ? membro.getVinculo().name() : null);
        return doc;
    }
}