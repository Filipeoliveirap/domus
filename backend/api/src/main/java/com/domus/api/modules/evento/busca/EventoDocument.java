package com.domus.api.modules.evento.busca;

import com.domus.api.modules.evento.Evento;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "eventos")
@Getter
@Setter
@NoArgsConstructor
public class EventoDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String igrejaId;

    @Field(type = FieldType.Text)
    private String titulo;

    @Field(type = FieldType.Text)
    private String descricao;

    @Field(type = FieldType.Text)
    private String local;

    public static EventoDocument de(Evento evento) {
        EventoDocument doc = new EventoDocument();
        doc.setId(evento.getId().toString());
        doc.setIgrejaId(evento.getIgreja().getId().toString());
        doc.setTitulo(evento.getTitulo());
        doc.setDescricao(evento.getDescricao());
        doc.setLocal(evento.getLocal());
        return doc;
    }
}