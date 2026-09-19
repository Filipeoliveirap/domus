package com.domus.api.shared.busca;

import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/reindexacao")
@RequiredArgsConstructor
public class ReindexacaoController {

    private final ReindexacaoService reindexacaoService;
    private final UsuarioAutenticado usuarioAutenticado;

    /** Reindexa TODAS as igrejas de uma vez (sem filtro de tenant, ver
     *  {@code ReindexacaoService.reindexarTudo}) — o matcher genérico de {@code /admin/**}
     *  em SecurityConfig libera ADMIN/LIDER/COMUM, então a restrição de verdade precisa
     *  estar aqui (achado em teste, 2026-09-15, ver ReindexacaoControllerTest). */
    @PostMapping
    public Map<String, Long> reindexar() {
        if (!Permissoes.podeReindexar(usuarioAutenticado.getRole())) {
            throw new AccessDeniedException("Só um administrador pode disparar a reindexação.");
        }
        return reindexacaoService.reindexarTudo();
    }
}