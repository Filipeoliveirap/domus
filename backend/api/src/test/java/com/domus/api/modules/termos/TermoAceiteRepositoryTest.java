package com.domus.api.modules.termos;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TermoAceiteRepositoryTest {

    @Autowired TermoAceiteRepository termoAceiteRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EntityManager entityManager;

    private Usuario criarUsuario() {
        Igreja igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja de Teste Termo Aceite").emailContato("termo@teste.com").build());
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Fulano").email("fulano-termo@teste.com")
                .vinculo(Vinculo.MEMBRO).build());
        Role role = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();
        return usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(role).senhaHash("hash").ativo(true).build());
    }

    @Test
    void countByUsuarioIdAndVersaoContaSoAVersaoCerta() {
        Usuario usuario = criarUsuario();
        termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuario).tipo(TipoTermo.TERMOS_DE_USO).versao("1.0").ip("1.2.3.4").build());
        termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuario).tipo(TipoTermo.POLITICA_PRIVACIDADE).versao("1.0").ip("1.2.3.4").build());
        termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuario).tipo(TipoTermo.TERMOS_DE_USO).versao("0.9").ip("1.2.3.4").build());
        entityManager.flush();
        entityManager.clear();

        long total = termoAceiteRepository.countByUsuarioIdAndVersao(usuario.getId(), "1.0");

        assertThat(total).isEqualTo(2L);
    }

    @Test
    void buscarUltimoAceiteRetornaODataMaisRecente() {
        Usuario usuario = criarUsuario();
        TermoAceite antigo = termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuario).tipo(TipoTermo.TERMOS_DE_USO).versao("1.0").ip("1.2.3.4").build());
        entityManager.flush();
        antigo.setAceitoEm(LocalDateTime.now().minusDays(5));
        termoAceiteRepository.save(antigo);
        termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuario).tipo(TipoTermo.POLITICA_PRIVACIDADE).versao("1.0").ip("1.2.3.4").build());
        entityManager.flush();
        entityManager.clear();

        LocalDateTime ultimo = termoAceiteRepository.buscarUltimoAceite(usuario.getId());

        assertThat(ultimo).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void buscarUltimoAceiteRetornaNullQuandoNuncaAceitou() {
        Usuario usuario = criarUsuario();

        LocalDateTime ultimo = termoAceiteRepository.buscarUltimoAceite(usuario.getId());

        assertThat(ultimo).isNull();
    }
}
