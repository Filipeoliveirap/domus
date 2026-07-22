package com.domus.api.modules.foto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Rotinas de limpeza do bucket de fotos.
 *
 * <p>Sem elas, todo upload abandonado e toda pessoa arquivada deixam lixo permanente no
 * bucket — ninguém apaga na mão. As duas rotinas decidem por AUSÊNCIA de referência (ver
 * {@link FotoRepository#buscarOrfas} e {@link FotoRepository#buscarDeArquivadas}): um erro
 * de consulta aqui apaga a foto de alguém para sempre. A rede de segurança é dupla: a FK de
 * {@code foto} está com {@code ON DELETE RESTRICT}, então uma foto ainda referenciada não sai
 * do banco mesmo que a consulta erre; e o volume removido é sempre registrado no log — uma
 * limpeza silenciosa é uma limpeza que ninguém percebe estar errada.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LimpezaFotosJob {

    private final FotoRepository fotoRepository;
    private final FotoService fotoService;

    @Value("${app.fotos.orfa-horas:24}")
    private int orfaHoras;

    @Value("${app.fotos.arquivada-meses:6}")
    private int arquivadaMeses;

    /**
     * Órfãs: enviadas e nunca vinculadas a pessoa, evento ou igreja.
     *
     * <p>Acontece quando alguém envia a foto e abandona o formulário sem salvar. O corte
     * (padrão 24h) dá tempo de sobra para quem só está demorando a preencher o formulário —
     * não remove uma foto recém-enviada.
     */
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

    /**
     * Fotos de pessoas arquivadas há mais tempo que o corte (padrão 6 meses).
     *
     * <p>NÃO removemos no arquivamento: arquivar é soft delete e a Fase 3 prevê desarquivar.
     * Apagar a foto na hora tornaria o desarquivamento parcial — a pessoa voltaria sem rosto,
     * sem recuperação, por causa de centavos de armazenamento. Só depois de meses arquivada,
     * quando desarquivar já é improvável, a foto é de fato removida.
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void limparDeArquivadas() {
        LocalDateTime corte = LocalDateTime.now().minusMonths(arquivadaMeses);
        List<Foto> deArquivadas = fotoRepository.buscarDeArquivadas(corte);

        for (Foto foto : deArquivadas) {
            fotoService.remover(foto.getId());
        }

        log.info("Limpeza de fotos de pessoas arquivadas concluída. removidas={}, corte_meses={}",
                deArquivadas.size(), arquivadaMeses);
    }
}
