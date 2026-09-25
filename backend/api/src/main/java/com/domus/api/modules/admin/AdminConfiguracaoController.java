package com.domus.api.modules.admin;

import com.domus.api.modules.admin.dto.ConfiguracaoMercadoPagoDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/configuracoes/mercadopago")
@RequiredArgsConstructor
public class AdminConfiguracaoController {

    private final AdminConfiguracaoService adminConfiguracaoService;

    @GetMapping
    public ResponseEntity<ConfiguracaoMercadoPagoDTO> obterConfiguracao() {
        return ResponseEntity.ok(adminConfiguracaoService.obterConfiguracaoMercadoPago());
    }

    @PutMapping
    public ResponseEntity<ConfiguracaoMercadoPagoDTO> salvarConfiguracao(@RequestBody @Valid ConfiguracaoMercadoPagoDTO dto) {
        return ResponseEntity.ok(adminConfiguracaoService.salvarConfiguracaoMercadoPago(dto));
    }
}
