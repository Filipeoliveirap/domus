package com.domus.api.modules.igreja;

import com.domus.api.config.TokenService;
import com.domus.api.modules.igreja.dto.CadastroCongregacaoRequest;
import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CadastroCongregacaoService {

    private final CodigoConviteService codigoConviteService;
    private final IgrejaRepository igrejaRepository;
    private final PessoaRepository pessoaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SessaoDTO registrarCongregacao(CadastroCongregacaoRequest request) {
        if (pessoaRepository.existsByEmailIncluindoArquivados(request.emailAdmin())) {
            throw new BusinessException("EMAIL_DUPLICADO", "Este e-mail já está cadastrado no sistema.");
        }

        Igreja igrejaFilha = Igreja.builder()
                .nome(request.nome())
                .emailContato(request.emailAdmin())
                .statusAssinatura(StatusAssinatura.ATIVA)
                .build();

        igrejaFilha = igrejaRepository.save(igrejaFilha);

        CodigoConviteCongregacao convite = codigoConviteService.validarEConsumirCodigo(request.codigoConvite(), igrejaFilha);
        igrejaFilha.setPlano(convite.getMatriz().getPlano());
        igrejaRepository.save(igrejaFilha);

        Pessoa adminPessoa = Pessoa.builder()
                .igreja(igrejaFilha)
                .nome(request.nomeAdmin())
                .email(request.emailAdmin())
                .vinculo(Vinculo.MEMBRO)
                .build();
        adminPessoa = pessoaRepository.save(adminPessoa);

        Role adminRole = roleRepository.findByNome("ADMIN_IGREJA")
                .orElseThrow(() -> new ResourceNotFoundException("Role ADMIN_IGREJA não encontrada"));

        Usuario usuario = Usuario.builder()
                .igreja(igrejaFilha)
                .pessoa(adminPessoa)
                .role(adminRole)
                .senhaHash(passwordEncoder.encode(request.senha()))
                .ativo(true)
                .build();
        usuario = usuarioRepository.save(usuario);

        log.info("Congregação registrada via código de convite. igreja_id={}, matriz_id={}, admin_email={}",
                igrejaFilha.getId(), convite.getMatriz().getId(), request.emailAdmin());

        return usuarioRepository.findSessaoById(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada"));
    }
}
