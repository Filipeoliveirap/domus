package com.domus.api.modules.auth.DTO;

public record TokenPairDTO(
        String token,
        String refreshToken
) {
}
