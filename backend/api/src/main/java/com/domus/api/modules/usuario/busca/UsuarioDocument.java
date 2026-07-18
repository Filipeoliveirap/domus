package com.domus.api.modules.usuario.busca;

import com.domus.api.modules.usuario.Usuario;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "usuarios")
@Setting(settingPath = "elasticsearch/domus-analyzer.json")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String igrejaId;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String nome;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String email;

    @Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
    private String role;

    @Field(type = FieldType.Boolean)
    private boolean ativo;

    public static UsuarioDocument de(Usuario usuario) {
        UsuarioDocument doc = new UsuarioDocument();
        doc.setId(usuario.getId().toString());
        doc.setIgrejaId(usuario.getIgreja().getId().toString());
        doc.setNome(usuario.getNome());
        doc.setEmail(usuario.getEmail());
        doc.setRole(usuario.getRole().getNome());
        doc.setAtivo(usuario.isAtivo());
        return doc;
    }
}
