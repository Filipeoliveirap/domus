package com.domus.api.modules.igreja.familia;

import com.domus.api.modules.igreja.familia.DTO.VinculoDTOs.*;
import com.domus.api.shared.security.Perfil;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Path próprio (não {@code /igrejas/**}) porque são recursos com regras de acesso diferentes; papel exigido fica no {@code SecurityConfig}. */
@RestController
@RequestMapping("/igrejas-vinculadas")
@RequiredArgsConstructor
public class VinculoController {

    private final VinculoService vinculoService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public VinculoStatusResponse status() {
        VinculoStatusResponse status = vinculoService.status(usuarioAutenticado.getIgrejaId());
        if (Perfil.ADMIN_IGREJA.name().equals(usuarioAutenticado.getRole())) {
            return status;
        }
        return new VinculoStatusResponse(status.estado(), null, null, status.mae(), status.congregacoes());
    }

    @PostMapping("/codigo")
    public Map<String, String> gerarCodigo() {
        return Map.of("codigoVinculo", vinculoService.gerarCodigo(usuarioAutenticado.getIgrejaId()));
    }

    @PostMapping("/entrar")
    public VinculoStatusResponse entrar(@RequestBody @Valid EntrarNaFamiliaRequest request) {
        return vinculoService.entrarNaFamilia(
                usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId(), request.codigo());
    }

    @DeleteMapping("/congregacoes/{congregacaoId}")
    public ResponseEntity<Void> desvincular(@PathVariable UUID congregacaoId) {
        vinculoService.desvincularCongregacao(usuarioAutenticado.getIgrejaId(), congregacaoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sair")
    public ResponseEntity<Void> sair() {
        vinculoService.sairDaFamilia(usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }
}
