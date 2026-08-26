package com.domus.api.modules.pagamento.conta;

import com.domus.api.modules.pagamento.conta.DTOs.ConectarContaResponseDTO;
import com.domus.api.modules.pagamento.conta.DTOs.StatusContaPagamentoDTO;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pagamentos/conta")
public class ContaPagamentoController {

    private final MercadoPagoOAuthService service;
    private final UsuarioAutenticado usuarioAutenticado;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public ContaPagamentoController(MercadoPagoOAuthService service, UsuarioAutenticado usuarioAutenticado) {
        this.service = service;
        this.usuarioAutenticado = usuarioAutenticado;
    }

    @GetMapping("/conectar")
    public ConectarContaResponseDTO conectar() {
        return new ConectarContaResponseDTO(
            service.gerarUrlAutorizacao(usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId()));
    }

    /**
     * O Mercado Pago redireciona o NAVEGADOR direto pra este endpoint (não é uma chamada de
     * API feita pelo front) — devolver {@code void} deixava a pessoa parada numa resposta
     * de API em branco depois de autorizar, o que convidava a recarregar/voltar a página, e
     * cada uma dessas ações reprocessava o callback com um {@code state} de uso único já
     * consumido (gerando {@code OAUTH_STATE_INVALIDO} mesmo já tendo conectado direitinho na
     * primeira vez). Por isso sempre redireciona de volta pra tela de configurações, com ou
     * sem sucesso — nunca deixa o navegador "parado" nesta URL.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        String destino = frontendUrl + "/configuracoes/igreja";
        try {
            // `igrejaId` vem da sessão autenticada, nunca de um parâmetro da requisição — mas
            // isso sozinho não bloqueia CSRF de OAuth (Critical 3a): sem verificar `state`,
            // um atacante podia induzir um admin logado a abrir esta URL com o `code` do
            // atacante, conectando a conta MP do atacante à igreja da vítima. `state` é
            // verificado dentro do service contra o nonce gerado em `gerarUrlAutorizacao`.
            service.processarCallback(code, state, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId());
            destino += "?mpConectado=1";
        } catch (BusinessException e) {
            destino += "?mpErro=" + e.getCodigo();
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, destino).build();
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
