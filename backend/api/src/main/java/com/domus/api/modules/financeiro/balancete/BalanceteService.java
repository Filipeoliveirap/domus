package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.balancete.DTOs.BalanceteFamiliaResponseDTO;
import com.domus.api.modules.financeiro.balancete.DTOs.BalanceteIgrejaDTO;
import com.domus.api.modules.financeiro.balancete.DTOs.BalanceteResponseDTO;
import com.domus.api.modules.financeiro.balancete.DTOs.LinhaCategoriaDTO;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
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
    private final FamiliaIgrejaService familiaIgrejaService;
    private final IgrejaRepository igrejaRepository;

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

    @Transactional(readOnly = true)
    public BalanceteFamiliaResponseDTO gerarFamilia(UUID igrejaSedeId, int ano) {
        List<UUID> idsFamilia = familiaIgrejaService.idsDaFamilia(igrejaSedeId);
        List<Igreja> igrejas = igrejaRepository.findAllById(idsFamilia);

        List<BalanceteIgrejaDTO> porIgreja = igrejas.stream()
                .map(igreja -> new BalanceteIgrejaDTO(
                        igreja.getId(),
                        igreja.getNome(),
                        igreja.getIgrejaMae() == null,
                        gerar(igreja.getId(), ano)))
                .toList();

        return new BalanceteFamiliaResponseDTO(porIgreja, gerarConsolidado(idsFamilia, ano));
    }

    private BalanceteResponseDTO gerarConsolidado(List<UUID> igrejaIds, int ano) {
        BigDecimal saldoAbertura = repository.saldoAntesDeVariasIgrejas(igrejaIds, LocalDate.of(ano, 1, 1));
        List<BalanceteProjections.LinhaMensalConsolidada> linhas =
                repository.agregarConsolidadoPorCategoriaEMes(igrejaIds, ano);

        Set<String> nomesAtivosNormalizados = new HashSet<>();
        for (UUID igrejaId : igrejaIds) {
            for (CategoriaFinanceira c : categoriaRepository.buscarTodasPorIgreja(igrejaId)) {
                nomesAtivosNormalizados.add(normalizar(c.getNome()));
            }
        }

        Map<String, Map<String, BigDecimal[]>> valoresPorChave = new LinkedHashMap<>();
        Map<String, String> nomesPorChave = new HashMap<>();
        for (var l : linhas) {
            valoresPorChave
                    .computeIfAbsent(l.getChave(), k -> new HashMap<>())
                    .computeIfAbsent(l.getTipo(), k -> arrayZerado())[l.getMes() - 1] = l.getTotal();
            nomesPorChave.put(l.getChave(), l.getNomeExibicao());
        }

        List<LinhaCategoriaDTO> entradas = new ArrayList<>();
        List<LinhaCategoriaDTO> saidas = new ArrayList<>();
        for (String chave : valoresPorChave.keySet()) {
            boolean arquivada = !nomesAtivosNormalizados.contains(chave);
            Map<String, BigDecimal[]> porTipo = valoresPorChave.get(chave);
            String nome = nomesPorChave.get(chave);
            if (porTipo.containsKey("ENTRADA")) {
                entradas.add(montarLinhaConsolidada(nome, arquivada, porTipo.get("ENTRADA")));
            }
            if (porTipo.containsKey("SAIDA")) {
                saidas.add(montarLinhaConsolidada(nome, arquivada, porTipo.get("SAIDA")));
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

    private LinhaCategoriaDTO montarLinhaConsolidada(String nome, boolean arquivada, BigDecimal[] valores) {
        List<BigDecimal> lista = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : valores) {
            lista.add(v);
            total = total.add(v);
        }
        return new LinhaCategoriaDTO(null, nome, arquivada, lista, total);
    }

    private String normalizar(String nome) {
        return java.text.Normalizer.normalize(nome, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }
}
