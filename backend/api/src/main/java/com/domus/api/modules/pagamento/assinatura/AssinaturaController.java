package com.domus.api.modules.pagamento.assinatura;

import com.domus.api.modules.pagamento.assinatura.dto.AlterarPlanoRequest;
import com.domus.api.modules.pagamento.assinatura.dto.AtualizarCartaoRequest;
import com.domus.api.modules.pagamento.assinatura.dto.ContratarPlanoRequest;
import com.domus.api.modules.pagamento.assinatura.dto.ResumoAssinaturaResponse;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/igrejas/minha/assinatura", "/api/igrejas/minha/assinatura"})
@RequiredArgsConstructor
public class AssinaturaController {

    private final AssinaturaService assinaturaService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public ResponseEntity<ResumoAssinaturaResponse> obterResumo() {
        validarPermissaoAdmin();
        return ResponseEntity.ok(assinaturaService.obterResumo(usuarioAutenticado.getIgrejaId()));
    }

    @PostMapping("/alterar-plano")
    public ResponseEntity<Void> alterarPlano(@RequestBody @Valid AlterarPlanoRequest request) {
        validarPermissaoAdmin();
        assinaturaService.alterarPlano(usuarioAutenticado.getIgrejaId(), request.novoPlano());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cartao")
    public ResponseEntity<Void> atualizarCartao(@RequestBody @Valid AtualizarCartaoRequest request) {
        validarPermissaoAdmin();
        assinaturaService.atualizarCartao(usuarioAutenticado.getIgrejaId(), request.cardTokenId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cancelar")
    public ResponseEntity<Void> cancelarAssinatura() {
        validarPermissaoAdmin();
        assinaturaService.cancelarAssinatura(usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reativar")
    public ResponseEntity<Void> reativarAssinatura() {
        validarPermissaoAdmin();
        assinaturaService.reativarAssinatura(usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contratar")
    public ResponseEntity<Void> contratarPlano(@RequestBody @Valid ContratarPlanoRequest request) {
        validarPermissaoAdmin();
        assinaturaService.contratarPlano(usuarioAutenticado.getIgrejaId(), request.plano(), request.cardTokenId());
        return ResponseEntity.noContent().build();
    }

    private void validarPermissaoAdmin() {
        if (!Permissoes.ehAdmin(usuarioAutenticado.getRole())) {
            throw new AccessDeniedException("Apenas administradores podem gerenciar a assinatura da igreja.");
        }
    }
}
