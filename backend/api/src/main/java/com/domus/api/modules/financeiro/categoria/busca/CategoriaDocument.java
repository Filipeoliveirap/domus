package com.domus.api.modules.financeiro.categoria.busca;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "categorias")
@Getter
@Setter
@NoArgsConstructor
public class CategoriaDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String igrejaId;

    @Field(type = FieldType.Text)
    private String nome;

    @Field(type = FieldType.Keyword)
    private String tipo;

    public static CategoriaDocument de(CategoriaFinanceira categoria) {
        CategoriaDocument doc = new CategoriaDocument();
        doc.setId(categoria.getId().toString());
        doc.setIgrejaId(categoria.getIgreja().getId().toString());
        doc.setNome(categoria.getNome());
        doc.setTipo(categoria.getTipo() != null ? categoria.getTipo().name() : null);
        return doc;
    }
}