package com.domus.api.modules.igreja;

import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.igreja.DTO.IgrejaDTO;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaAdminRequest;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse;
import com.domus.api.shared.security.AuthCookieFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/igrejas")
@RequiredArgsConstructor
public class IgrejaController {
    private final IgrejaService igrejaService;
    private final AuthCookieFactory cookieFactory;

    /** Registrar a igreja já deixa a pessoa logada — então emite os cookies de sessão. */
    @PostMapping("/registrar")
    public ResponseEntity<SessaoDTO> cadastrarIgreja(
            @RequestBody @Valid RegistrarIgrejaAdminRequest data) {
        RegistrarIgrejaResponse response = igrejaService.registrar(data);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.access(response.token()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refresh(response.refreshToken()).toString())
                .body(new SessaoDTO(
                        response.id(), response.nome(), response.role(),
                        response.igrejaId(), response.igrejaNome()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IgrejaDTO> buscarIgrejaPorId(@PathVariable UUID id) {
        IgrejaDTO igreja = igrejaService.buscarPorId(id);
        return ResponseEntity.ok(igreja);
    }
}
