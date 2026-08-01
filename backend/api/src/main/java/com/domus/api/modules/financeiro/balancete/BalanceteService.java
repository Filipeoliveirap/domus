package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.balancete.DTOs.BalanceteResponseDTO;
import com.domus.api.modules.financeiro.balancete.DTOs.LinhaCategoriaDTO;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BalanceteService {

    private final BalanceteRepository repository;
    private final CategoriaFinanceiraRepository categoriaRepository;

    @Transactional(readOnly = true)
    public BalanceteResponseDTO gerar(UUID igrejaId, int ano) {
        BigDecimal saldoAbertura = repository.saldoAntesDe(igrejaId, LocalDate.of(ano, 1, 1));
        List<BalanceteProjections.LinhaMensalAgregada> linhas = repository.agregarPorCategoriaEMes(igrejaId, ano);

        // categoriaId -> tipo ("ENTRADA"/"SAIDA") -> array de 12 posições
        Map<UUID, Map<String, BigDecimal[]>> valoresPorCategoria = new LinkedHashMap<>();
        Map<UUID, String> nomesPorCategoria = new HashMap<>();

        for (var l : linhas) {
            valoresPorCategoria
                    .computeIfAbsent(l.getCategoriaId(), k -> new HashMap<>())
                    .computeIfAbsent(l.getTipo(), k -> arrayZerado())[l.getMes() - 1] = l.getTotal();
            nomesPorCategoria.put(l.getCategoriaId(), l.getNomeCategoria());
        }

        List<CategoriaFinanceira> ativas = categoriaRepository.buscarTodasPorIgreja(igrejaId);
        Set<UUID> idsAtivas = new HashSet<>();
        for (CategoriaFinanceira c : ativas) idsAtivas.add(c.getId());

        List<LinhaCategoriaDTO> entradas = new ArrayList<>();
        List<LinhaCategoriaDTO> saidas = new ArrayList<>();

        for (CategoriaFinanceira c : ativas) {
            if (c.getTipo() == TipoCategoria.ENTRADA || c.getTipo() == TipoCategoria.AMBOS) {
                entradas.add(montarLinha(c.getId(), c.getNome(), false, valoresPorCategoria, "ENTRADA"));
            }
            if (c.getTipo() == TipoCategoria.SAIDA || c.getTipo() == TipoCategoria.AMBOS) {
                saidas.add(montarLinha(c.getId(), c.getNome(), false, valoresPorCategoria, "SAIDA"));
            }
        }

        for (UUID categoriaId : valoresPorCategoria.keySet()) {
            if (idsAtivas.contains(categoriaId)) continue; // já tratada acima
            Map<String, BigDecimal[]> porTipo = valoresPorCategoria.get(categoriaId);
            String nome = nomesPorCategoria.get(categoriaId);
            if (porTipo.containsKey("ENTRADA")) {
                entradas.add(montarLinha(categoriaId, nome, true, valoresPorCategoria, "ENTRADA"));
            }
            if (porTipo.containsKey("SAIDA")) {
                saidas.add(montarLinha(categoriaId, nome, true, valoresPorCategoria, "SAIDA"));
            }
        }

        List<BigDecimal> subtotalEntradas = somarPorMes(entradas);
        List<BigDecimal> subtotalSaidas = somarPorMes(saidas);
        List<BigDecimal> saldoDoMes = new ArrayList<>();
        List<BigDecimal> saldoAcumulado = new ArrayList<>();
        BigDecimal acumulado = saldoAbertura;
        for (int i = 0; i < 12; i++) {
            BigDecimal saldo = subtotalEntradas.get(i).subtract(subtotalSaidas.get(i));
            saldoDoMes.add(saldo);
            acumulado = acumulado.add(saldo);
            saldoAcumulado.add(acumulado);
        }

        return new BalanceteResponseDTO(ano, saldoAbertura, entradas, saidas,
                subtotalEntradas, subtotalSaidas, saldoDoMes, saldoAcumulado);
    }

    private LinhaCategoriaDTO montarLinha(UUID categoriaId, String nome, boolean arquivada,
            Map<UUID, Map<String, BigDecimal[]>> valoresPorCategoria, String tipo) {
        BigDecimal[] arr = valoresPorCategoria
                .getOrDefault(categoriaId, Map.of())
                .getOrDefault(tipo, arrayZerado());
        List<BigDecimal> valores = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : arr) {
            valores.add(v);
            total = total.add(v);
        }
        return new LinhaCategoriaDTO(categoriaId, nome, arquivada, valores, total);
    }

    private List<BigDecimal> somarPorMes(List<LinhaCategoriaDTO> linhas) {
        BigDecimal[] soma = arrayZerado();
        for (LinhaCategoriaDTO l : linhas) {
            for (int i = 0; i < 12; i++) {
                soma[i] = soma[i].add(l.valoresPorMes().get(i));
            }
        }
        return new ArrayList<>(List.of(soma));
    }

    private BigDecimal[] arrayZerado() {
        BigDecimal[] arr = new BigDecimal[12];
        Arrays.fill(arr, BigDecimal.ZERO);
        return arr;
    }
}
