package com.domus.api.modules.usuario.DTO;

import com.domus.api.modules.foto.Foto;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.Usuario;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioResponseDTOTest {

    @Test
    void from_incluiFotoIdDaPessoaVinculada() {
        UUID fotoId = UUID.randomUUID();
        Foto foto = new Foto();
        foto.setId(fotoId);

        Pessoa pessoa = Pessoa.builder().nome("Ana").email("ana@ex.com").foto(foto).build();
        Role role = new Role();
        role.setNome("ACESSO_COMUM");
        Usuario usuario = Usuario.builder().id(UUID.randomUUID()).pessoa(pessoa).role(role).ativo(true).build();

        UsuarioResponseDTO dto = UsuarioResponseDTO.from(usuario);

        assertThat(dto.fotoId()).isEqualTo(fotoId);
    }

    @Test
    void from_semFoto_fotoIdNulo() {
        Pessoa pessoa = Pessoa.builder().nome("Ana").email("ana@ex.com").build();
        Role role = new Role();
        role.setNome("ACESSO_COMUM");
        Usuario usuario = Usuario.builder().id(UUID.randomUUID()).pessoa(pessoa).role(role).ativo(true).build();

        UsuarioResponseDTO dto = UsuarioResponseDTO.from(usuario);

        assertThat(dto.fotoId()).isNull();
    }
}
