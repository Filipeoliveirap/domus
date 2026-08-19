package com.domus.api.modules.foto;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.PessoaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Mockito puro de propósito: essa rotina roda de madrugada contra dados reais de demonstração, e sem @SpringBootTest o @Scheduled nem existe aqui. */
class LimpezaFotosJobTest {

    FotoRepository fotoRepository;
    FotoService fotoService;
    PessoaRepository pessoaRepository;
    LimpezaFotosJob job;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        fotoRepository = mock(FotoRepository.class);
        fotoService = mock(FotoService.class);
        pessoaRepository = mock(PessoaRepository.class);
        job = new LimpezaFotosJob(fotoRepository, fotoService, pessoaRepository);
        ReflectionTestUtils.setField(job, "orfaHoras", 24);
        ReflectionTestUtils.setField(job, "arquivadaMeses", 6);
    }

    @Test
    void naoRemoveFotoQueEstaEmUso() {
        // Rotina decide por ausência de referência; buscarOrfas() real já filtra fotos em uso, simulamos retornando lista vazia.
        Foto usada = fotoDe(LocalDateTime.now().minusDays(30));
        when(fotoRepository.buscarOrfas(any())).thenReturn(List.of());

        job.limparOrfas();

        verify(fotoService, never()).remover(usada.getId());
        verify(fotoService, never()).remover(any());
    }

    @Test
    void removeOrfaMaisVelhaQueOCorte() {
        Foto orfa = fotoDe(LocalDateTime.now().minusHours(25));
        when(fotoRepository.buscarOrfas(any())).thenReturn(List.of(orfa));

        job.limparOrfas();

        verify(fotoService).remover(orfa.getId());
    }

    @Test
    void naoRemoveOrfaRecemEnviada() {
        // Alguém acabou de enviar e ainda está preenchendo o formulário.
        when(fotoRepository.buscarOrfas(any())).thenReturn(List.of());

        job.limparOrfas();

        verify(fotoService, never()).remover(any());
    }

    @Test
    void removeFotoDePessoaArquivadaHaMaisQueOCorte() {
        Foto foto = fotoDe(LocalDateTime.now().minusMonths(7));
        when(fotoRepository.buscarDeArquivadas(any())).thenReturn(List.of(foto));

        job.limparDeArquivadas();

        verify(fotoService).remover(foto.getId());
    }

    @Test
    void desvinculaAPessoaAntesDeRemoverAFotoDeArquivada() {
        // FK ON DELETE RESTRICT: pessoa.foto_id precisa ser solto ANTES do DELETE FROM foto,
        // senão o banco recusa a remoção. Ver PessoaRepository.desvincularFoto.
        Foto foto = fotoDe(LocalDateTime.now().minusMonths(7));
        when(fotoRepository.buscarDeArquivadas(any())).thenReturn(List.of(foto));

        job.limparDeArquivadas();

        var ordem = inOrder(pessoaRepository, fotoService);
        ordem.verify(pessoaRepository).desvincularFoto(foto.getId());
        ordem.verify(fotoService).remover(foto.getId());
    }

    @Test
    void naoRemoveFotoDePessoaArquivadaHaMenosQueOCorte() {
        // Simula a query real: pessoa arquivada há só 2 meses ainda não passou do corte de
        // 6, então buscarDeArquivadas não a devolveria.
        when(fotoRepository.buscarDeArquivadas(any())).thenReturn(List.of());

        job.limparDeArquivadas();

        verify(fotoService, never()).remover(any());
    }

    private Foto fotoDe(LocalDateTime criadaEm) {
        return Foto.builder()
                .id(UUID.randomUUID())
                .igreja(Igreja.builder().id(UUID.randomUUID()).build())
                .chave("fotos/teste/" + UUID.randomUUID())
                .tipo("image/jpeg")
                .bytes(100)
                .createdAt(criadaEm)
                .build();
    }
}
