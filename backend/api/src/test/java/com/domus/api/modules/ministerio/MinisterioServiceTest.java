package com.domus.api.modules.ministerio;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.ministerio.DTOs.MinisterioRequest;
import com.domus.api.modules.ministerio.DTOs.MinisterioResponse;
import com.domus.api.shared.dominio.Endereco;
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
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

@SpringBootTest
@Transactional
class MinisterioServiceTest implements PostgresTestContainerSupport {

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
        MinisterioResponse response = service.criar(new MinisterioRequest("Louvor", null), igrejaId, null);

        assertThat(response.nome()).isEqualTo("Louvor");
        assertThat(repository.findByIdAndIgrejaId(response.id(), igrejaId)).isPresent();
    }

    @Test
    void nao_permite_dois_ministerios_com_mesmo_nome_ignorando_acento_e_caixa() {
        service.criar(new MinisterioRequest("Recepção", null), igrejaId, null);

        assertThatThrownBy(() -> service.criar(new MinisterioRequest("recepcao", null), igrejaId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Já existe um ministério");
    }

    @Test
    void ministerio_de_outra_igreja_nao_e_encontrado() {
        UUID id = service.criar(new MinisterioRequest("Infantil", null), igrejaId, null).id();

        assertThat(repository.findByIdAndIgrejaId(id, outraIgrejaId)).isEmpty();
    }

    @Test
    void arquivar_some_da_listagem() {
        UUID id = service.criar(new MinisterioRequest("Diaconato", null), igrejaId, null).id();

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
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor", null), igrejaId, null).id();
        UUID pessoaId = novaPessoa("Ana", igrejaId).getId();

        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(pessoaId),
                igrejaId, null, true, null);

        var detalhe = service.detalhe(ministerioId, igrejaId, null, true);
        assertThat(detalhe.membros()).extracting(m -> m.pessoaId()).contains(pessoaId);
    }

    @Test
    void pessoa_comum_nao_pode_adicionar_membro_sem_ser_lider() {
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor", null), igrejaId, null).id();
        UUID pessoaAlvo = novaPessoa("Bia", igrejaId).getId();
        UUID pessoaComum = novaPessoa("Carlos", igrejaId).getId();

        assertThatThrownBy(() -> service.adicionarMembro(
                ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(pessoaAlvo),
                igrejaId, pessoaComum, false, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void lider_do_ministerio_nao_pode_promover_ou_rebaixar_papel() {
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor", null), igrejaId, null).id();
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
        UUID ministerioId = service.criar(new MinisterioRequest("Recepção", null), igrejaId, null).id();
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
        UUID ministerioId = service.criar(new MinisterioRequest("Missões", null), igrejaId, null).id();
        UUID pessoaId = novaPessoa("Fábio", igrejaId).getId();

        service.pedirEntrada(ministerioId, pessoaId, igrejaId);

        assertThatThrownBy(() -> service.pedirEntrada(ministerioId, pessoaId, igrejaId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void recusar_pedido_remove_a_linha_permitindo_pedir_de_novo() {
        UUID ministerioId = service.criar(new MinisterioRequest("Jovens", null), igrejaId, null).id();
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

    @Autowired com.domus.api.modules.usuario.UsuarioRepository usuarioRepository;
    @Autowired com.domus.api.modules.usuario.RoleRepository roleRepository;
    @Autowired com.domus.api.modules.notificacao.NotificacaoRepository notificacaoRepository;

    private com.domus.api.modules.usuario.Usuario novoUsuario(com.domus.api.modules.pessoa.Pessoa pessoa) {
        com.domus.api.modules.usuario.Role role = roleRepository.findByNome("LIDER").orElseThrow();
        return usuarioRepository.save(com.domus.api.modules.usuario.Usuario.builder()
                .igreja(pessoa.getIgreja()).pessoa(pessoa).role(role).ativo(true).build());
    }

    @Test
    void pedirEntradaNotificaOLiderQueTemUsuario() {
        UUID ministerioId = service.criar(new MinisterioRequest("Intercessão", null), igrejaId, null).id();
        com.domus.api.modules.pessoa.Pessoa liderPessoa = novaPessoa("Helena", igrejaId);
        UUID candidataId = novaPessoa("Ivana", igrejaId).getId();

        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(liderPessoa.getId()),
                igrejaId, null, true, null);
        service.atualizarPapel(ministerioId, liderPessoa.getId(),
                new com.domus.api.modules.ministerio.DTOs.AtualizarPapelRequest(Papel.LIDER), igrejaId, true);
        com.domus.api.modules.usuario.Usuario usuarioLider = novoUsuario(liderPessoa);

        service.pedirEntrada(ministerioId, candidataId, igrejaId);

        var notificacoes = notificacaoRepository.findByUsuarioDestinatarioId(
                usuarioLider.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(notificacoes.getContent())
                .extracting(n -> n.getTipo())
                .contains(com.domus.api.modules.notificacao.TipoNotificacao.PEDIDO_MINISTERIO);
    }

    @Test
    void pedirEntradaNaoQuebraSeLiderNaoTemUsuario() {
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor Kids", null), igrejaId, null).id();
        UUID liderId = novaPessoa("Joana", igrejaId).getId();
        UUID candidataId = novaPessoa("Karina", igrejaId).getId();

        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(liderId),
                igrejaId, null, true, null);
        service.atualizarPapel(ministerioId, liderId,
                new com.domus.api.modules.ministerio.DTOs.AtualizarPapelRequest(Papel.LIDER), igrejaId, true);

        service.pedirEntrada(ministerioId, candidataId, igrejaId); // não deve lançar mesmo sem usuário no líder
    }

    @Test
    void adicionarMembroNotificaAPessoaAdicionada() {
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor", null), igrejaId, null).id();
        com.domus.api.modules.pessoa.Pessoa pessoa = novaPessoa("Larissa", igrejaId);
        com.domus.api.modules.usuario.Usuario usuario = novoUsuario(pessoa);

        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(pessoa.getId()),
                igrejaId, null, true, null);

        var notificacoes = notificacaoRepository.findByUsuarioDestinatarioId(
                usuario.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(notificacoes.getContent())
                .extracting(n -> n.getTipo())
                .contains(com.domus.api.modules.notificacao.TipoNotificacao.ADICIONADO_MINISTERIO);
    }

    @Test
    void naoDuplicaORotuloQuandoONomeJaComecaComRede() {
        // Nome cadastrado pelo próprio usuário já incluindo o rótulo — sem a checagem, o texto
        // virava "Você foi adicionado à Rede Rede de Louvor.".
        UUID ministerioId = service.criar(new MinisterioRequest("Rede de Louvor", null), igrejaId, null).id();
        com.domus.api.modules.pessoa.Pessoa pessoa = novaPessoa("Paula", igrejaId);
        com.domus.api.modules.usuario.Usuario usuario = novoUsuario(pessoa);

        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(pessoa.getId()),
                igrejaId, null, true, null);

        var notificacoes = notificacaoRepository.findByUsuarioDestinatarioId(
                usuario.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(notificacoes.getContent())
                .extracting(n -> n.getTexto())
                .contains("Você foi adicionado à Rede de Louvor.");
    }

    @Test
    void removerMembroNotificaAPessoaRemovida() {
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor", null), igrejaId, null).id();
        com.domus.api.modules.pessoa.Pessoa pessoa = novaPessoa("Marcos", igrejaId);
        com.domus.api.modules.usuario.Usuario usuario = novoUsuario(pessoa);
        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(pessoa.getId()),
                igrejaId, null, true, null);

        service.removerMembro(ministerioId, pessoa.getId(), igrejaId, null, true);

        var notificacoes = notificacaoRepository.findByUsuarioDestinatarioId(
                usuario.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(notificacoes.getContent())
                .extracting(n -> n.getTipo())
                .contains(com.domus.api.modules.notificacao.TipoNotificacao.REMOVIDO_MINISTERIO);
    }

    @Test
    void aceitarPedidoNotificaOCandidato() {
        UUID ministerioId = service.criar(new MinisterioRequest("Recepção", null), igrejaId, null).id();
        com.domus.api.modules.pessoa.Pessoa candidata = novaPessoa("Nina", igrejaId);
        com.domus.api.modules.usuario.Usuario usuarioCandidata = novoUsuario(candidata);

        service.pedirEntrada(ministerioId, candidata.getId(), igrejaId);
        service.aceitarPedido(ministerioId, candidata.getId(), igrejaId, null, true, null);

        var notificacoes = notificacaoRepository.findByUsuarioDestinatarioId(
                usuarioCandidata.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(notificacoes.getContent())
                .extracting(n -> n.getTipo())
                .contains(com.domus.api.modules.notificacao.TipoNotificacao.ADICIONADO_MINISTERIO);
    }

    @Test
    void naoNotificaQuandoAtorEOProprioAlvo() {
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor", null), igrejaId, null).id();
        com.domus.api.modules.pessoa.Pessoa pessoa = novaPessoa("Otávio", igrejaId);
        com.domus.api.modules.usuario.Usuario usuario = novoUsuario(pessoa);

        // Caso de borda: o próprio ator é o alvo da ação (ex.: admin se adiciona).
        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(pessoa.getId()),
                igrejaId, pessoa.getId(), true, null);

        var notificacoes = notificacaoRepository.findByUsuarioDestinatarioId(
                usuario.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(notificacoes.getContent())
                .extracting(n -> n.getTipo())
                .doesNotContain(com.domus.api.modules.notificacao.TipoNotificacao.ADICIONADO_MINISTERIO);
    }

    @org.springframework.beans.factory.annotation.Autowired
    jakarta.persistence.EntityManager entityManager;

    @Test
    void detalheEnxergaMembrosDeMinisterioArquivado() {
        // Regressão do mesmo bug já corrigido em Célula: @SQLRestriction("deleted_at IS
        // NULL") da entidade Ministerio vazando pro JOIN implícito de
        // findByMinisterioIdOrderByPapelAsc, escondendo membros de ministério arquivado.
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor", null), igrejaId, null).id();
        UUID pessoaId = novaPessoa("Ana", igrejaId).getId();
        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(pessoaId),
                igrejaId, null, true, null);
        entityManager.flush();
        entityManager.clear();

        service.arquivar(ministerioId, igrejaId);
        entityManager.flush();
        entityManager.clear();

        var detalhe = service.detalhe(ministerioId, igrejaId, null, true);

        assertThat(detalhe.arquivada()).isTrue();
        assertThat(detalhe.membros()).hasSize(1);
    }

    @Test
    void listarArquivadasRetornaSoAsArquivadas() {
        UUID ativoId = service.criar(new MinisterioRequest("Ativo", null), igrejaId, null).id();
        UUID arquivadoId = service.criar(new MinisterioRequest("Arquivado", null), igrejaId, null).id();
        service.arquivar(arquivadoId, igrejaId);

        var arquivados = service.listarArquivadas(igrejaId);

        assertThat(arquivados).extracting(MinisterioResponse::id).containsExactly(arquivadoId);
        assertThat(arquivados).extracting(MinisterioResponse::id).doesNotContain(ativoId);
    }

    @Test
    void restaurarTiraDoArquivoEBloqueiaOutraIgreja() {
        UUID ministerioId = service.criar(new MinisterioRequest("Intercessão", null), igrejaId, null).id();
        service.arquivar(ministerioId, igrejaId);

        assertThatThrownBy(() -> service.restaurar(ministerioId, outraIgrejaId))
                .isInstanceOf(com.domus.api.shared.exception.ResourceNotFoundException.class);

        service.restaurar(ministerioId, igrejaId);
        assertThat(service.listar(igrejaId)).extracting(MinisterioResponse::id).contains(ministerioId);
    }

    @Test
    void excluirDefinitivoFuncionaComMembros_desvinculaEmVezDeBloquear() {
        // Regressão: excluirDefinitivo buscava os membros pra desvincular usando a mesma
        // consulta afetada pelo vazamento do @SQLRestriction — recebia lista vazia,
        // não desvinculava nada, e a FK recusava o hard delete do ministério.
        UUID ministerioId = service.criar(new MinisterioRequest("Missões", null), igrejaId, null).id();
        UUID pessoaId = novaPessoa("Heitor", igrejaId).getId();
        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(pessoaId),
                igrejaId, null, true, null);
        entityManager.flush();
        entityManager.clear();

        service.arquivar(ministerioId, igrejaId);
        entityManager.flush();
        entityManager.clear();

        service.excluirDefinitivo(ministerioId, igrejaId);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findById(ministerioId)).isEmpty();
    }

    @org.springframework.beans.factory.annotation.Autowired
    com.domus.api.modules.foto.FotoRepository fotoRepository;

    private com.domus.api.modules.foto.Foto novaFoto(UUID igrejaId) {
        Igreja igreja = igrejaRepository.findById(igrejaId).orElseThrow();
        return fotoRepository.save(com.domus.api.modules.foto.Foto.builder()
                .igreja(igreja)
                .chave(UUID.randomUUID().toString())
                .tipo("image/webp")
                .bytes(1024)
                .build());
    }

    @Test
    void atualizarFotoTrocaSoAFotoSemTocarNoNome() {
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor", null), igrejaId, null).id();
        UUID fotoId = novaFoto(igrejaId).getId();

        service.atualizarFoto(ministerioId, igrejaId, null, fotoId);

        Ministerio salvo = repository.findByIdAndIgrejaId(ministerioId, igrejaId).orElseThrow();
        assertThat(salvo.getFoto().getId()).isEqualTo(fotoId);
        assertThat(salvo.getNome()).isEqualTo("Louvor");
    }

    @Test
    void atualizarFotoRemoveAFotoAntigaQuandoTrocada() {
        UUID fotoAntigaId = novaFoto(igrejaId).getId();
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor", fotoAntigaId), igrejaId, null).id();
        UUID fotoNovaId = novaFoto(igrejaId).getId();

        service.atualizarFoto(ministerioId, igrejaId, null, fotoNovaId);

        assertThat(fotoRepository.findById(fotoAntigaId)).isEmpty();
    }
}
