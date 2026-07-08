package com.domus.api.shared.busca;

import com.domus.api.modules.evento.busca.BuscaEventoService;
import com.domus.api.modules.financeiro.movimentacao.busca.BuscaMovimentacaoService;
import com.domus.api.modules.membro.busca.BuscaMembroService;
import com.domus.api.modules.usuario.busca.BuscaUsuarioService;
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
    private final BuscaEventoService buscaEventoService;
    private final BuscaUsuarioService buscaUsuarioService;
    private final BuscaMovimentacaoService buscaMovimentacaoService;

    @GetMapping("/membros")
    public List<ResultadoBusca> buscarMembros(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return buscaMembroService.buscar(q.trim(), usuarioAutenticado.getIgrejaId(), 10);
    }

    @GetMapping("/eventos")
    public List<ResultadoBusca> buscarEventos(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return buscaEventoService.buscar(q.trim(), usuarioAutenticado.getIgrejaId(), 10);
    }

    @GetMapping("/usuarios")
    public List<ResultadoBusca> buscarUsuarios(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return buscaUsuarioService.buscar(q.trim(), usuarioAutenticado.getIgrejaId(), 10);
    }

    @GetMapping("/movimentacoes")
    public List<ResultadoBusca> buscarMovimentacoes(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return buscaMovimentacaoService.buscar(q.trim(), usuarioAutenticado.getIgrejaId(), 10);
    }
}