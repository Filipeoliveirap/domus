package com.domus.api.modules.ministerio;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.ministerio.DTOs.MinisterioRequest;
import com.domus.api.modules.ministerio.DTOs.MinisterioResponse;
import com.domus.api.modules.pessoa.Endereco;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MinisterioServiceTest {

    @Autowired MinisterioService service;
    @Autowired MinisterioRepository repository;
    @Autowired IgrejaRepository igrejaRepository;

    UUID igrejaId;
    UUID outraIgrejaId;

    @BeforeEach
    void setup() {
        igrejaId = igrejaRepository.save(novaIgreja("Igreja do Teste de Ministério")).getId();
        outraIgrejaId = igrejaRepository.save(novaIgreja("Outra Igreja")).getId();
    }

    private Igreja novaIgreja(String nome) {
        Igreja igreja = new Igreja();
        igreja.setNome(nome);
        igreja.setEmailContato(nome.toLowerCase().replace(" ", ".") + "@teste.com");
        igreja.setEndereco(Endereco.builder()
                .cep("01000-000").logradouro("Rua da Igreja").numero("100")
                .bairro("Centro").cidade("São Paulo").uf("SP")
                .build());
        return igreja;
    }

    @Test
    void cria_ministerio_e_retorna_id_e_nome() {
        MinisterioResponse response = service.criar(new MinisterioRequest("Louvor"), igrejaId, null);

        assertThat(response.nome()).isEqualTo("Louvor");
        assertThat(repository.findByIdAndIgrejaId(response.id(), igrejaId)).isPresent();
    }

    @Test
    void nao_permite_dois_ministerios_com_mesmo_nome_ignorando_acento_e_caixa() {
        service.criar(new MinisterioRequest("Recepção"), igrejaId, null);

        assertThatThrownBy(() -> service.criar(new MinisterioRequest("recepcao"), igrejaId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Já existe um ministério");
    }

    @Test
    void ministerio_de_outra_igreja_nao_e_encontrado() {
        UUID id = service.criar(new MinisterioRequest("Infantil"), igrejaId, null).id();

        assertThat(repository.findByIdAndIgrejaId(id, outraIgrejaId)).isEmpty();
    }

    @Test
    void arquivar_some_da_listagem() {
        UUID id = service.criar(new MinisterioRequest("Diaconato"), igrejaId, null).id();

        service.arquivar(id, igrejaId);

        assertThat(service.listar(igrejaId)).extracting(MinisterioResponse::id).doesNotContain(id);
    }
}
