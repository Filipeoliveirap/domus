package com.domus.api.modules.igreja.DTO;

import java.util.UUID;

/** {@code null} remove a logo atual. */
public record AtualizarLogoRequest(UUID fotoId) {}
