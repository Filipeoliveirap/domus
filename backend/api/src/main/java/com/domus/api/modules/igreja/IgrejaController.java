package com.domus.api.modules.igreja;

import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.igreja.DTO.AtualizarIgrejaRequest;
import com.domus.api.modules.igreja.DTO.IgrejaDetalheDTO;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaAdminRequest;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse;
import com.domus.api.shared.security.AuthCookieFactory;
import com.domus.api.shared.security.UsuarioAutenticado;
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

    /** Registrar a igreja já deixa a pessoa logada — então emite os cookies de sessão. */
    @PostMapping("/registrar")
    public ResponseEntity<SessaoDTO> cadastrarIgreja(
            @RequestBody @Valid RegistrarIgrejaAdminRequest data) {
        RegistrarIgrejaResponse response = igrejaService.registrar(data);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.access(response.token()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refresh(response.refreshToken()).toString())
                // Cadastro novo: a pessoa acabou de ser criada, ainda sem foto.
                .body(new SessaoDTO(
                        response.id(), response.nome(), response.role(),
                        response.igrejaId(), response.igrejaNome(), null));
    }

    /*
     * REMOVIDO em 2026-07-21: GET /igrejas/{id} lia QUALQUER igreja por UUID, sem checar
     * tenant — um usuário logado de outra igreja obtinha nome, CNPJ, e-mail e telefone dela.
     * Já tinha sido endurecido uma vez (era permitAll, virou authenticated), mas o acesso
     * cruzado seguia. Não havia um único consumidor no front nem em teste, então apagar
     * elimina a superfície em vez de remendá-la. O caso legítimo é GET /igrejas/minha, que
     * tira o tenant do JWT e por isso não tem como pedir a igreja de outro.
     */

    /**
     * Dados completos da MINHA igreja (Configurações). Sem {@code id} no path de propósito:
     * o tenant vem do JWT, então não há como pedir a igreja de outro.
     */
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
}
