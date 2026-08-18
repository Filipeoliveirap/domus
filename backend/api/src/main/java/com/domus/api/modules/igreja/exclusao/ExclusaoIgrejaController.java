package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.igreja.exclusao.DTO.AgendarExclusaoRequest;
import com.domus.api.modules.igreja.exclusao.DTO.ResumoExclusaoResponse;
import com.domus.api.shared.exception.AcessoNegadoException;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/igrejas/exclusao")
@RequiredArgsConstructor
public class ExclusaoIgrejaController {

    private final ExclusaoIgrejaService exclusaoIgrejaService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping("/resumo")
    public ResponseEntity<ResumoExclusaoResponse> resumo() {
        exigirAdmin();
        return ResponseEntity.ok(exclusaoIgrejaService.resumo(usuarioAutenticado.getIgrejaId()));
    }

    @PostMapping("/agendar")
    public ResponseEntity<Void> agendar(@RequestBody @Valid AgendarExclusaoRequest data) {
        exigirAdmin();
        exclusaoIgrejaService.agendar(
                usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId(), data.nomeConfirmacao());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cancelar")
    public ResponseEntity<Void> cancelar() {
        exigirAdmin();
        exclusaoIgrejaService.cancelar(usuarioAutenticado.getIgrejaId());
        return ResponseEntity.ok().build();
    }

    private void exigirAdmin() {
        if (!Permissoes.podeExcluirIgreja(usuarioAutenticado.getRole())) {
            throw new AcessoNegadoException();
        }
    }
}
