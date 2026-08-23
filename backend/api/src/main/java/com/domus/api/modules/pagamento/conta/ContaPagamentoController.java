package com.domus.api.modules.pagamento.conta;

import com.domus.api.modules.pagamento.conta.DTOs.ConectarContaResponseDTO;
import com.domus.api.modules.pagamento.conta.DTOs.StatusContaPagamentoDTO;
import com.domus.api.shared.security.UsuarioAutenticado;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pagamentos/conta")
public class ContaPagamentoController {

    private final MercadoPagoOAuthService service;
    private final UsuarioAutenticado usuarioAutenticado;

    public ContaPagamentoController(MercadoPagoOAuthService service, UsuarioAutenticado usuarioAutenticado) {
        this.service = service;
        this.usuarioAutenticado = usuarioAutenticado;
    }

    @GetMapping("/conectar")
    public ConectarContaResponseDTO conectar() {
        return new ConectarContaResponseDTO(service.gerarUrlAutorizacao(usuarioAutenticado.getIgrejaId()));
    }

    @GetMapping("/callback")
    public void callback(@RequestParam String code, @RequestParam String state) {
        service.processarCallback(code, UUID.fromString(state), usuarioAutenticado.getUsuarioId());
    }

    @GetMapping("/status")
    public StatusContaPagamentoDTO status() {
        return new StatusContaPagamentoDTO(service.status(usuarioAutenticado.getIgrejaId()));
    }

    @DeleteMapping
    public void desconectar() {
        service.desconectar(usuarioAutenticado.getIgrejaId());
    }
}
