package com.domus.api.modules.pessoa.DTO;

import java.util.UUID;

/** {@code null} remove a foto atual. */
public record AtualizarFotoRequest(UUID fotoId) {}
