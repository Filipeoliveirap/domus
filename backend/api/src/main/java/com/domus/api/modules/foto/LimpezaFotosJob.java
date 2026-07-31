package com.domus.api.modules.foto;

import com.domus.api.modules.pessoa.PessoaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Rotinas de limpeza de fotos órfãs (upload abandonado) e de pessoas arquivadas.
 * A FK ON DELETE RESTRICT impede remoção acidental de foto referenciada.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LimpezaFotosJob {

    private final FotoRepository fotoRepository;
    private final FotoService fotoService;
    private final PessoaRepository pessoaRepository;

    @Value("${app.fotos.orfa-horas:24}")
    private int orfaHoras;

    @Value("${app.fotos.arquivada-meses:6}")
    private int arquivadaMeses;

    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public void limparOrfas() {
        LocalDateTime corte = LocalDateTime.now().minusHours(orfaHoras);
        List<Foto> orfas = fotoRepository.buscarOrfas(corte);

        for (Foto foto : orfas) {
            fotoService.remover(foto.getId());
        }

        log.info("Limpeza de fotos órfãs concluída. removidas={}, corte_horas={}", orfas.size(), orfaHoras);
    }

    /** Remove fotos de pessoas arquivadas há mais de {@code arquivadaMeses} meses. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void limparDeArquivadas() {
        LocalDateTime corte = LocalDateTime.now().minusMonths(arquivadaMeses);
        List<Foto> deArquivadas = fotoRepository.buscarDeArquivadas(corte);

        for (Foto foto : deArquivadas) {
            pessoaRepository.desvincularFoto(foto.getId());
            fotoService.remover(foto.getId());
        }

        log.info("Limpeza de fotos de pessoas arquivadas concluída. removidas={}, corte_meses={}",
                deArquivadas.size(), arquivadaMeses);
    }
}
