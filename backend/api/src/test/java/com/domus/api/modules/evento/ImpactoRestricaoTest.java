package com.domus.api.modules.evento;

import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.ImpactoRestricaoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.DTO.EnderecoDTO;
import com.domus.api.modules.pessoa.DTO.PessoaRequestDTO;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.PessoaService;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 6: apertar/ligar uma restrição de elegibilidade num evento que já tem inscritos NUNCA
 * cancela em silêncio. O admin vê a prévia (quem ficaria de fora) e só cancela com escolha
 * explícita ({@code cancelarNaoElegiveis=true}).
 *
 * <p>Roda contra Postgres de verdade (mesmo raciocínio de {@code EventoAuditoriaArquivamentoTest}):
 * a corrida de vaga usa lock pessimista de verdade, e a releitura via
 * {@code entityManager.flush()+clear()} prova que a mudança foi de fato persistida — sem isso a
 * instância já gerenciada na sessão manteria o status antigo em memória.
 */
@SpringBootTest
@Transactional
class ImpactoRestricaoTest {

    @Autowired EventoService eventoService;
    @Autowired InscricaoService inscricaoService;
    @Autowired PessoaService pessoaService;
    @Autowired EventoRepository eventoRepository;
    @Autowired InscricaoRepository inscricaoRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired EntityManager entityManager;

    Igreja igrejaDoTeste;
    UUID igrejaId;
    Usuario admin;

    @BeforeEach
    void setup() {
        igrejaDoTeste = igrejaRepository.save(novaIgreja("Igreja do Teste de Impacto"));
        igrejaId = igrejaDoTeste.getId();
        admin = novoUsuario(novaPessoa("Admin da Igreja", Vinculo.MEMBRO, 40), "ADMIN_IGREJA");
    }

    private Igreja novaIgreja(String nome) {
        Igreja igreja = new Igreja();
        igreja.setNome(nome);
        igreja.setEmailContato(nome.toLowerCase().replace(" ", ".") + "@teste.com");
        return igreja;
    }

    private Pessoa novaPessoa(String nome, Vinculo vinculo, int idade) {
        Pessoa pessoa = Pessoa.builder()
                .igreja(igrejaDoTeste)
                .nome(nome)
                .vinculo(vinculo)
                .dataNascimento(LocalDate.now().minusYears(idade))
                .build();
        return pessoaRepository.save(pessoa);
    }

    private Usuario novoUsuario(Pessoa pessoa, String roleNome) {
        Role role = roleRepository.findByNome(roleNome).orElseThrow();
        Usuario usuario = Usuario.builder()
                .igreja(igrejaDoTeste)
                .pessoa(pessoa)
                .ativo(true)
                .role(role)
                .build();
        return usuarioRepository.save(usuario);
    }

    private Evento novoEvento(String titulo, boolean exclusivoMembros) {
        Evento evento = Evento.builder()
                .igreja(igrejaDoTeste)
                .titulo(titulo)
                .inicioEm(LocalDateTime.now().plusDays(10))
                .requerInscricao(true)
                .exclusivoMembros(exclusivoMembros)
                .build();
        return eventoRepository.save(evento);
    }

    private EventoRequest requestDe(Evento e, Integer idadeMin, Integer idadeMax, Boolean exclusivoMembros) {
        return new EventoRequest(e.getTitulo(), null, e.getInicioEm(), null,
                null, null, null, null, null, idadeMin, idadeMax, null, null,
                null, null, exclusivoMembros, true, null, null, null);
    }

    private long confirmados(UUID eventoId) {
        return inscricaoRepository.contarPessoasConfirmadas(eventoId);
    }

    @Test
    void apertar_a_faixa_NAO_cancela_ninguem_sozinho() {
        Evento eventoJovens = novoEvento("Retiro de Jovens", false);
        Pessoa pessoaDe34 = novaPessoa("Maria Souza", Vinculo.MEMBRO, 34);
        inscricaoService.inscrever(eventoJovens.getId(), pessoaDe34.getId(), admin.getId(),
                admin.getPessoa().getId(), "ADMIN_IGREJA", true, igrejaId);

        eventoService.atualizarEvento(eventoJovens.getId(),
                requestDe(eventoJovens, 18, 25, false), igrejaId, admin.getId(), false);

        entityManager.flush();
        entityManager.clear();

        assertThat(confirmados(eventoJovens.getId())).isEqualTo(1);
    }

    @Test
    void previa_lista_quem_ficaria_de_fora_sem_alterar_nada() {
        Evento eventoJovens = novoEvento("Retiro de Jovens", false);
        Pessoa pessoaDe34 = novaPessoa("Maria Souza", Vinculo.MEMBRO, 34);
        inscricaoService.inscrever(eventoJovens.getId(), pessoaDe34.getId(), admin.getId(),
                admin.getPessoa().getId(), "ADMIN_IGREJA", true, igrejaId);

        ImpactoRestricaoResponse impacto = eventoService.calcularImpacto(eventoJovens.getId(),
                requestDe(eventoJovens, 18, 25, false), igrejaId, "ADMIN_IGREJA");

        assertThat(impacto.afetados()).extracting("nome").containsExactly("Maria Souza");
        assertThat(impacto.afetados().get(0).motivos()).isNotEmpty();

        entityManager.flush();
        entityManager.clear();
        assertThat(confirmados(eventoJovens.getId())).isEqualTo(1);
    }

    @Test
    void com_a_escolha_explicita_cancela() {
        Evento eventoJovens = novoEvento("Retiro de Jovens", false);
        Pessoa pessoaDe34 = novaPessoa("Maria Souza", Vinculo.MEMBRO, 34);
        inscricaoService.inscrever(eventoJovens.getId(), pessoaDe34.getId(), admin.getId(),
                admin.getPessoa().getId(), "ADMIN_IGREJA", true, igrejaId);

        eventoService.atualizarEvento(eventoJovens.getId(),
                requestDe(eventoJovens, 18, 25, false), igrejaId, admin.getId(), true);

        entityManager.flush();
        entityManager.clear();

        assertThat(confirmados(eventoJovens.getId())).isZero();
    }

    @Test
    void ligar_exclusivoMembros_tambem_deixou_de_cancelar_sozinho() {
        // Mudança deliberada de comportamento — antes cancelava em silêncio.
        Evento eventoAberto = novoEvento("Culto Aberto", false);
        Pessoa congregante = novaPessoa("Carlos Congregante", Vinculo.CONGREGANTE, 30);
        inscricaoService.inscrever(eventoAberto.getId(), congregante.getId(), null,
                congregante.getId(), "ACESSO_COMUM", false, igrejaId);

        eventoService.atualizarEvento(eventoAberto.getId(),
                requestDe(eventoAberto, null, null, true), igrejaId, admin.getId(), false);

        entityManager.flush();
        entityManager.clear();

        assertThat(confirmados(eventoAberto.getId())).isEqualTo(1);
    }

    @Test
    void pessoa_que_deixa_de_ser_membro_CONTINUA_sendo_cancelada() {
        Evento eventoExclusivo = novoEvento("Retiro Só de Membros", true);
        Pessoa membro = novaPessoa("Joana Membro", Vinculo.MEMBRO, 25);
        inscricaoService.inscrever(eventoExclusivo.getId(), membro.getId(), null,
                membro.getId(), "ACESSO_COMUM", false, igrejaId);

        pessoaService.atualizarMembro(membro.getId(), requisicaoDeVinculo(membro, Vinculo.CONGREGANTE), igrejaId);

        entityManager.flush();
        entityManager.clear();

        assertThat(confirmados(eventoExclusivo.getId())).isZero();
    }

    @Test
    void acesso_comum_recebe_403_ao_pedir_a_previa_de_impacto() {
        Evento eventoJovens = novoEvento("Retiro de Jovens", false);

        assertThatThrownBy(() -> eventoService.calcularImpacto(eventoJovens.getId(),
                requestDe(eventoJovens, 18, 25, false), igrejaId, "ACESSO_COMUM"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private PessoaRequestDTO requisicaoDeVinculo(Pessoa pessoa, Vinculo vinculo) {
        return new PessoaRequestDTO(pessoa.getNome(), null, null, pessoa.getDataNascimento(),
                new EnderecoDTO(null, null, null, null, null, null, null), vinculo, null, null,
                null, null, null, null);
    }
}
