package com.domus.api.modules.evento.DTOs;

import com.domus.api.modules.evento.Evento;
import com.domus.api.shared.dominio.Endereco;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventoResponseLocalInfoTest {

    @Test
    void enderecoAdHoc_temTextoFormatadoEEstruturado() {
        Evento e = Evento.builder()
                .enderecoLocal(Endereco.builder()
                        .logradouro("Rua das Flores").numero("123").cidade("Recife").uf("PE").build())
                .build();

        EventoResponse.LocalInfo info = EventoResponse.LocalInfo.from(e);

        assertThat(info.id()).isNull();
        assertThat(info.nome()).isEqualTo("Rua das Flores, 123 - Recife/PE");
        assertThat(info.enderecoLocal()).isNotNull();
        assertThat(info.enderecoLocal().cidade()).isEqualTo("Recife");
        assertThat(info.enderecoLocal().logradouro()).isEqualTo("Rua das Flores");
    }

    @Test
    void semLocalNenhum_localInfoEhNull() {
        assertThat(EventoResponse.LocalInfo.from(Evento.builder().build())).isNull();
    }

    @Test
    void textoSimples_naoTemEnderecoEstruturado() {
        Evento e = Evento.builder().localTexto("Chácara do João").build();
        EventoResponse.LocalInfo info = EventoResponse.LocalInfo.from(e);
        assertThat(info.nome()).isEqualTo("Chácara do João");
        assertThat(info.enderecoLocal()).isNull();
    }
}
