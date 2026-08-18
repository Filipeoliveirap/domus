package com.domus.api.modules.igreja.exclusao.DTO;

import java.util.List;

public record ResumoExclusaoResponse(
        long pessoas,
        long eventos,
        long movimentacoesFinanceiras,
        long celulas,
        long ministerios,
        long usuarios,
        List<String> igrejasVinculadas
) {}
