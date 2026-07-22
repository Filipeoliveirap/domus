package com.domus.api.shared.busca;

import com.domus.api.modules.evento.busca.BuscaEventoService;
import com.domus.api.modules.financeiro.categoria.busca.BuscaCategoriaService;
import com.domus.api.modules.financeiro.movimentacao.busca.BuscaMovimentacaoService;
import com.domus.api.modules.pessoa.busca.BuscaPessoaService;
import com.domus.api.modules.usuario.busca.BuscaUsuarioService;
import com.domus.api.shared.DTO.ResultadoBusca;
import com.domus.api.shared.security.Permissoes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BuscaGlobalService {

    private final BuscaPessoaService buscaPessoaService;
    private final BuscaEventoService buscaEventoService;
    private final BuscaUsuarioService buscaUsuarioService;
    private final BuscaMovimentacaoService buscaMovimentacaoService;
    private final BuscaCategoriaService buscaCategoriaService;

    private static final int LIMITE_POR_TIPO = 5;

    public List<ResultadoBusca> buscar(String termo, UUID igrejaId, String role) {
        List<ResultadoBusca> resultados = new ArrayList<>();

        resultados.addAll(buscaPessoaService.buscar(termo, igrejaId, LIMITE_POR_TIPO));
        resultados.addAll(buscaEventoService.buscar(termo, igrejaId, LIMITE_POR_TIPO));

        if (Permissoes.podeVerUsuariosEFinanceiroNaBuscaGlobal(role)) {
            resultados.addAll(buscaUsuarioService.buscar(termo, igrejaId, LIMITE_POR_TIPO));
            resultados.addAll(buscaMovimentacaoService.buscar(termo, igrejaId, LIMITE_POR_TIPO));
            resultados.addAll(buscaCategoriaService.buscar(termo, igrejaId, LIMITE_POR_TIPO));
        }

        log.debug("Busca global. termo='{}', role={}, igreja_id={}, total_resultados={}",
                termo, role, igrejaId, resultados.size());

        return resultados;
    }
}