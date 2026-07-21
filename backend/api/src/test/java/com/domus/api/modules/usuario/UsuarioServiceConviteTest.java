package com.domus.api.modules.usuario;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.auth.PasswordResetService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.membro.DTO.ConcederAcessoRequestDTO;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.usuario.DTO.UsuarioResponseDTO;
import com.domus.api.shared.email.EmailService;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UsuarioServiceConviteTest {

    UsuarioRepository usuarioRepository;
    IgrejaRepository igrejaRepository;
    RoleRepository roleRepository;
    MembroRepository membroRepository;
    CacheEvictor cacheEvictor;
    OutboxRegistrador outboxRegistrador;
    PasswordResetService passwordResetService;
    EmailService emailService;
    UsuarioService service;

    final UUID igrejaId = UUID.randomUUID();
    final UUID membroId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        usuarioRepository = mock(UsuarioRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        roleRepository = mock(RoleRepository.class);
        membroRepository = mock(MembroRepository.class);
        cacheEvictor = mock(CacheEvictor.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        passwordResetService = mock(PasswordResetService.class);
        emailService = mock(EmailService.class);
        service = new UsuarioService(usuarioRepository, igrejaRepository, roleRepository,
                membroRepository, cacheEvictor, outboxRegistrador, passwordResetService, emailService);

        Role role = new Role();
        role.setNome("MEMBRO");
        when(roleRepository.findByNome("MEMBRO")).thenReturn(Optional.of(role));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordResetService.gerarTokenDefinicaoSenha(any(), any(Duration.class))).thenReturn("tok123");
        when(passwordResetService.linkDefinicaoSenha(eq("tok123"), eq(true))).thenReturn("https://app/reset-password?token=tok123&convite=1");
    }

    private Membro membroComEmail(String email) {
        Igreja igreja = Igreja.builder().nome("Igreja Teste").build();
        return Membro.builder().igreja(igreja).nome("João").email(email).build();
    }

    @Test
    void concederAcesso_criaUsuarioSemSenhaEEnviaConvite() {
        Membro membro = membroComEmail("joao@teste.com");
        when(membroRepository.findByIdAndIgrejaId(membroId, igrejaId)).thenReturn(Optional.of(membro));
        when(usuarioRepository.findByMembroIdIncluindoArquivados(any())).thenReturn(Optional.empty());

        UsuarioResponseDTO resp = service.concederAcesso(
                new ConcederAcessoRequestDTO(membroId, "MEMBRO", null), igrejaId);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getSenhaHash()).isNull();         // sem senha: define no convite
        assertThat(captor.getValue().isAtivo()).isTrue();
        assertThat(resp.convitePendente()).isTrue();                    // nunca logou
        verify(emailService).enviar(eq("joao@teste.com"), contains("convidado"), anyString());
    }

    @Test
    void concederAcesso_membroSemEmailSemEmailFornecido_lancaMembroSemEmail() {
        Membro membro = membroComEmail(null);
        when(membroRepository.findByIdAndIgrejaId(membroId, igrejaId)).thenReturn(Optional.of(membro));
        when(usuarioRepository.findByMembroIdIncluindoArquivados(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.concederAcesso(
                new ConcederAcessoRequestDTO(membroId, "MEMBRO", null), igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Informe um e-mail");
        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void concederAcesso_membroSemEmailComEmailFornecido_gravaEmailNoMembro() {
        Membro membro = membroComEmail(null);
        when(membroRepository.findByIdAndIgrejaId(membroId, igrejaId)).thenReturn(Optional.of(membro));
        when(usuarioRepository.findByMembroIdIncluindoArquivados(any())).thenReturn(Optional.empty());
        when(membroRepository.existsByEmailIncluindoArquivados("novo@teste.com")).thenReturn(false);

        service.concederAcesso(new ConcederAcessoRequestDTO(membroId, "MEMBRO", "Novo@Teste.com"), igrejaId);

        assertThat(membro.getEmail()).isEqualTo("novo@teste.com");      // normalizado e gravado
        verify(membroRepository).save(membro);
        verify(emailService).enviar(eq("novo@teste.com"), anyString(), anyString());
    }

    @Test
    void concederAcesso_membroSemEmail_emailDuplicado_lanca() {
        Membro membro = membroComEmail(null);
        when(membroRepository.findByIdAndIgrejaId(membroId, igrejaId)).thenReturn(Optional.of(membro));
        when(usuarioRepository.findByMembroIdIncluindoArquivados(any())).thenReturn(Optional.empty());
        when(membroRepository.existsByEmailIncluindoArquivados("dup@teste.com")).thenReturn(true);

        assertThatThrownBy(() -> service.concederAcesso(
                new ConcederAcessoRequestDTO(membroId, "MEMBRO", "dup@teste.com"), igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já está em uso");
    }

    @Test
    void concederAcesso_membroJaTemAcesso_lanca() {
        Membro membro = membroComEmail("joao@teste.com");
        when(membroRepository.findByIdAndIgrejaId(membroId, igrejaId)).thenReturn(Optional.of(membro));
        Usuario ativo = Usuario.builder().ativo(true).build();  // deleteAt null = acesso ativo
        when(usuarioRepository.findByMembroIdIncluindoArquivados(any())).thenReturn(Optional.of(ativo));

        assertThatThrownBy(() -> service.concederAcesso(
                new ConcederAcessoRequestDTO(membroId, "MEMBRO", null), igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já tem acesso");
    }

    @Test
    void reenviarConvite_jaLogou_lancaConviteJaAceito() {
        Usuario logou = Usuario.builder().build();
        logou.registrarLogin();  // ultimoLoginEm != null
        UUID uid = UUID.randomUUID();
        when(usuarioRepository.findByIdAndIgrejaId(uid, igrejaId)).thenReturn(Optional.of(logou));

        assertThatThrownBy(() -> service.reenviarConvite(uid, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já fez login");
        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void concederAcesso_membroTemUsuarioDesativado_lancaMensagemPropria() {
        // A5: conta existe (deleteAt nulo, não é "arquivado"), mas ativo=false. Mensagem
        // precisa ser diferente da genérica "já tem acesso" e apontar pra reativação.
        Membro membro = membroComEmail("joao@teste.com");
        when(membroRepository.findByIdAndIgrejaId(membroId, igrejaId)).thenReturn(Optional.of(membro));
        Usuario desativado = Usuario.builder().ativo(false).build();
        when(usuarioRepository.findByMembroIdIncluindoArquivados(any())).thenReturn(Optional.of(desativado));

        assertThatThrownBy(() -> service.concederAcesso(
                new ConcederAcessoRequestDTO(membroId, "MEMBRO", null), igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("desativada");
        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void reenviarConvite_usuarioDesativado_lancaERecusaReenvio() {
        // A5: usuário desativado (ativo=false) não pode receber convite reenviado.
        Membro membro = membroComEmail("joao@teste.com");
        Usuario desativado = Usuario.builder().membro(membro).igreja(membro.getIgreja()).ativo(false).build();
        UUID uid = UUID.randomUUID();
        when(usuarioRepository.findByIdAndIgrejaId(uid, igrejaId)).thenReturn(Optional.of(desativado));

        assertThatThrownBy(() -> service.reenviarConvite(uid, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("desativado");
        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void reenviarConvite_pendente_reenvia() {
        Membro membro = membroComEmail("joao@teste.com");
        Usuario pendente = Usuario.builder().membro(membro).igreja(membro.getIgreja()).ativo(true).build();
        UUID uid = UUID.randomUUID();
        when(usuarioRepository.findByIdAndIgrejaId(uid, igrejaId)).thenReturn(Optional.of(pendente));

        service.reenviarConvite(uid, igrejaId);

        verify(emailService).enviar(eq("joao@teste.com"), contains("convidado"), anyString());
    }
}
