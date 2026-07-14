package com.domus.api.modules.igreja.DTO;

import java.util.UUID;

public record RegistrarIgrejaResponse (
        UUID id,
        String token,
        String nome,
        String role,
<<<<<<< HEAD
        UUID igrejaId
=======
        UUID igrejaId,
        String igrejaNome
>>>>>>> develop
)
{ }
