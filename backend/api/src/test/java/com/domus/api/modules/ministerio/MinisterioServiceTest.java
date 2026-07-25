package com.domus.api.modules.ministerio;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.ministerio.DTOs.MinisterioRequest;
import com.domus.api.modules.ministerio.DTOs.MinisterioResponse;
import com.domus.api.modules.pessoa.Endereco;
import com.domus.api.modules.ministerio.Papel;
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
    @Autowired MinisterioMembroRepository membroRepository;

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

    @org.springframework.beans.factory.annotation.Autowired
    com.domus.api.modules.pessoa.PessoaRepository pessoaRepository;

    private com.domus.api.modules.pessoa.Pessoa novaPessoa(String nome, UUID igrejaId) {
        Igreja igreja = igrejaRepository.findById(igrejaId).orElseThrow();
        com.domus.api.modules.pessoa.Pessoa pessoa = com.domus.api.modules.pessoa.Pessoa.builder()
                .igreja(igreja)
                .nome(nome)
                .email(nome.toLowerCase().replace(" ", ".") + "@teste.com")
                .vinculo(com.domus.api.modules.pessoa.Vinculo.MEMBRO)
                .build();
        return pessoaRepository.save(pessoa);
    }

    @Test
    void admin_adiciona_membro_direto_como_ativo() {
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor"), igrejaId, null).id();
        UUID pessoaId = novaPessoa("Ana", igrejaId).getId();

        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(pessoaId),
                igrejaId, null, true, null);

        var detalhe = service.detalhe(ministerioId, igrejaId, null, true);
        assertThat(detalhe.membros()).extracting(m -> m.pessoaId()).contains(pessoaId);
    }

    @Test
    void pessoa_comum_nao_pode_adicionar_membro_sem_ser_lider() {
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor"), igrejaId, null).id();
        UUID pessoaAlvo = novaPessoa("Bia", igrejaId).getId();
        UUID pessoaComum = novaPessoa("Carlos", igrejaId).getId();

        assertThatThrownBy(() -> service.adicionarMembro(
                ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(pessoaAlvo),
                igrejaId, pessoaComum, false, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void lider_do_ministerio_nao_pode_promover_ou_rebaixar_papel() {
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor"), igrejaId, null).id();
        UUID liderId = novaPessoa("Fabio", igrejaId).getId();
        UUID membroId = novaPessoa("Gabi", igrejaId).getId();

        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(liderId),
                igrejaId, null, true, null);
        service.atualizarPapel(ministerioId, liderId,
                new com.domus.api.modules.ministerio.DTOs.AtualizarPapelRequest(Papel.LIDER), igrejaId, true);
        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(membroId),
                igrejaId, liderId, false, null);

        assertThatThrownBy(() -> service.atualizarPapel(ministerioId, membroId,
                new com.domus.api.modules.ministerio.DTOs.AtualizarPapelRequest(Papel.LIDER), igrejaId, false))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void lider_do_ministerio_aceita_pedido_de_entrada() {
        UUID ministerioId = service.criar(new MinisterioRequest("Recepção"), igrejaId, null).id();
        UUID liderId = novaPessoa("Duda", igrejaId).getId();
        UUID candidataId = novaPessoa("Elis", igrejaId).getId();

        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(liderId),
                igrejaId, null, true, null);
        service.atualizarPapel(ministerioId, liderId,
                new com.domus.api.modules.ministerio.DTOs.AtualizarPapelRequest(Papel.LIDER), igrejaId, true);

        service.pedirEntrada(ministerioId, candidataId, igrejaId);
        assertThat(service.detalhe(ministerioId, igrejaId, liderId, false).pedidosPendentes())
                .extracting(m -> m.pessoaId()).contains(candidataId);

        service.aceitarPedido(ministerioId, candidataId, igrejaId, liderId, false, null);

        var detalhe = service.detalhe(ministerioId, igrejaId, liderId, false);
        assertThat(detalhe.membros()).extracting(m -> m.pessoaId()).contains(candidataId);
        assertThat(detalhe.pedidosPendentes()).isEmpty();
    }

    @Test
    void nao_permite_pedir_entrada_duas_vezes() {
        UUID ministerioId = service.criar(new MinisterioRequest("Missões"), igrejaId, null).id();
        UUID pessoaId = novaPessoa("Fábio", igrejaId).getId();

        service.pedirEntrada(ministerioId, pessoaId, igrejaId);

        assertThatThrownBy(() -> service.pedirEntrada(ministerioId, pessoaId, igrejaId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void recusar_pedido_remove_a_linha_permitindo_pedir_de_novo() {
        UUID ministerioId = service.criar(new MinisterioRequest("Jovens"), igrejaId, null).id();
        UUID pessoaId = novaPessoa("Gustavo", igrejaId).getId();

        service.pedirEntrada(ministerioId, pessoaId, igrejaId);
        service.recusarPedido(ministerioId, pessoaId, igrejaId, null, true);

        assertThat(membroRepositoryVazio(ministerioId, pessoaId)).isTrue();
        service.pedirEntrada(ministerioId, pessoaId, igrejaId); // não deve lançar
    }

    private boolean membroRepositoryVazio(UUID ministerioId, UUID pessoaId) {
        return service.detalhe(ministerioId, igrejaId, null, true).pedidosPendentes().isEmpty()
                && service.detalhe(ministerioId, igrejaId, null, true).membros().isEmpty();
    }
}
