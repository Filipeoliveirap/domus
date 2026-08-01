package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.balancete.DTOs.BalanceteFamiliaResponseDTO;
import com.domus.api.modules.financeiro.balancete.DTOs.BalanceteResponseDTO;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/relatorios")
@RequiredArgsConstructor
public class BalanceteController {

    private final BalanceteService service;
    private final UsuarioAutenticado usuarioAutenticado;
    private final FamiliaIgrejaService familiaIgrejaService;

    private void exigirFinanceiro() {
        if (!Permissoes.podeVerFinanceiro(usuarioAutenticado.getRole(), usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException("Só um administrador ou tesoureiro pode acessar o financeiro.");
        }
    }

    @GetMapping("/balancete-anual")
    public BalanceteResponseDTO balanceteAnual(@RequestParam int ano) {
        exigirFinanceiro();
        return service.gerar(usuarioAutenticado.getIgrejaId(), ano);
    }

    @GetMapping("/balancete-anual/congregacoes")
    public BalanceteFamiliaResponseDTO balanceteFamilia(@RequestParam int ano) {
        exigirFinanceiro();
        if (familiaIgrejaService.ehFilha(usuarioAutenticado.getIgrejaId())) {
            throw new AccessDeniedException("O balancete da família só é visível pela igreja sede.");
        }
        return service.gerarFamilia(usuarioAutenticado.getIgrejaId(), ano);
    }
}
