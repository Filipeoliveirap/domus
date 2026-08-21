package com.domus.api.modules.igreja;

import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.igreja.DTO.AtualizarIgrejaRequest;
import com.domus.api.modules.igreja.DTO.IgrejaDetalheDTO;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaAdminRequest;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse;
import com.domus.api.modules.igreja.DTO.RotulosDTO;
import com.domus.api.modules.igreja.DTO.RotulosRequest;
import com.domus.api.shared.security.AuthCookieFactory;
import com.domus.api.shared.security.UsuarioAutenticado;
import com.domus.api.shared.web.ClienteIpResolver;
import com.domus.api.modules.termos.TermoAceiteService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final UsuarioAutenticado usuarioAutenticado;
    private final ClienteIpResolver clienteIpResolver;
    private final TermoAceiteService termoAceiteService;

    @PostMapping("/registrar")
    public ResponseEntity<SessaoDTO> cadastrarIgreja(
            @RequestBody @Valid RegistrarIgrejaAdminRequest data,
            HttpServletRequest request) {
        RegistrarIgrejaResponse response = igrejaService.registrar(data, clienteIpResolver.resolver(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.access(response.token()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refresh(response.refreshToken()).toString())
                // Cadastro novo: a pessoa acabou de ser criada, ainda sem foto.
                .body(new SessaoDTO(
                        response.id(), response.nome(), response.role(),
                        response.igrejaId(), response.igrejaNome(), null,
                        null, null, null, java.util.List.of(),
                        termoAceiteService.precisaAceitar(response.id()),
                        termoAceiteService.dataUltimoAceite(response.id())));
    }

    // GET /igrejas/{id} foi removido: vazava dados de outra igreja sem checar tenant. Use /igrejas/minha (tenant vem do JWT).

    @GetMapping("/minha")
    public ResponseEntity<IgrejaDetalheDTO> buscarMinhaIgreja() {
        return ResponseEntity.ok(igrejaService.buscarDetalhe(usuarioAutenticado.getIgrejaId()));
    }

    @PutMapping("/minha")
    public ResponseEntity<IgrejaDetalheDTO> atualizarMinhaIgreja(
            @RequestBody @Valid AtualizarIgrejaRequest data) {
        return ResponseEntity.ok(igrejaService.atualizar(
                usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId(), data));
    }

    @PutMapping("/minha/rotulos")
    public ResponseEntity<RotulosDTO> atualizarRotulos(
            @RequestBody @Valid RotulosRequest data) {
        return ResponseEntity.ok(igrejaService.atualizarRotulos(usuarioAutenticado.getIgrejaId(), data));
    }
}
