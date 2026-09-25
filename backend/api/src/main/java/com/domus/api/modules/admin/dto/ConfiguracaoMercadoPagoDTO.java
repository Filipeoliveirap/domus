package com.domus.api.modules.admin.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracaoMercadoPagoDTO {
    private String accessToken;
    private String publicKey;
    private boolean configurado;
}
