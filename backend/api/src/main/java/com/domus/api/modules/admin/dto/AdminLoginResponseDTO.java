package com.domus.api.modules.admin.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginResponseDTO {
    private UUID id;
    private String nome;
    private String email;
    private String token;
}
