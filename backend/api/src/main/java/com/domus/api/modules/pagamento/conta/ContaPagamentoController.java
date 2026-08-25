package com.domus.api.modules.pagamento.conta;

import com.domus.api.modules.pagamento.conta.DTOs.ConectarContaResponseDTO;
import com.domus.api.modules.pagamento.conta.DTOs.StatusContaPagamentoDTO;
import com.domus.api.shared.security.UsuarioAutenticado;
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
        return new ConectarContaResponseDTO(
            service.gerarUrlAutorizacao(usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId()));
    }

    @GetMapping("/callback")
    public void callback(@RequestParam String code, @RequestParam String state) {
        // `igrejaId` vem da sessão autenticada, nunca de um parâmetro da requisição — mas
        // isso sozinho não bloqueia CSRF de OAuth (Critical 3a): sem verificar `state`,
        // um atacante podia induzir um admin logado a abrir esta URL com o `code` do
        // atacante, conectando a conta MP do atacante à igreja da vítima. `state` é
        // verificado dentro do service contra o nonce gerado em `gerarUrlAutorizacao`.
        service.processarCallback(code, state, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId());
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
