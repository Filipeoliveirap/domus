package com.domus.api.modules.igreja.familia;

import com.domus.api.modules.igreja.familia.DTO.VinculoDTOs.*;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Path próprio (não {@code /igrejas/**}) de propósito: o matcher {@code GET /igrejas/*} do
 * {@code SecurityConfig} era {@code permitAll} quando esta feature foi escrita, e o status
 * da família teria caído nessa regra. Hoje aquele matcher exige autenticação, mas o path
 * separado continua certo: são recursos diferentes, com regras de acesso diferentes.
 *
 * <p>Papel exigido (ADMIN_IGREJA) fica no {@code SecurityConfig}, como todo o resto do projeto.
 */
@RestController
@RequestMapping("/igrejas-vinculadas")
@RequiredArgsConstructor
public class VinculoController {

    private final VinculoService vinculoService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public VinculoStatusResponse status() {
        return vinculoService.status(usuarioAutenticado.getIgrejaId());
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
