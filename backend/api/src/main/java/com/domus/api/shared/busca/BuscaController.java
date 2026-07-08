package com.domus.api.modules.membro.busca;

import com.domus.api.shared.DTO.ResultadoBusca;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/busca")
@RequiredArgsConstructor
public class BuscaController {

    private final BuscaMembroService buscaMembroService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping("/membros")
    public List<ResultadoBusca> buscarMembros(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return buscaMembroService.buscar(q.trim(), usuarioAutenticado.getIgrejaId(), 10);
    }
}