package com.domus.api.modules.anexo.service;

import com.domus.api.modules.anexo.Anexo;
import com.domus.api.modules.anexo.AnexoRepository;
import com.domus.api.modules.anexo.AnexoService;
import com.domus.api.modules.anexo.dto.AnexoUploadResponse;
import com.domus.api.shared.armazenamento.ArmazenamentoR2;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnexoServiceTest {

    private AnexoRepository repository;
    private ArmazenamentoR2 armazenamento;
    private AnexoService service;
    private UUID igrejaId;
    private UUID anexoId;

    @BeforeEach
    void setup() {
        repository = mock(AnexoRepository.class);
        armazenamento = mock(ArmazenamentoR2.class);
        service = new AnexoService(repository, armazenamento);
        igrejaId = UUID.randomUUID();
        anexoId = UUID.randomUUID();
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private MockMultipartFile arquivoMock(String nome, String tipo, byte[] conteudo) {
        return new MockMultipartFile("arquivo", nome, tipo, conteudo);
    }

    private Anexo anexoArmazenado(UUID id) {
        return Anexo.builder()
                .id(id)
                .chave("anexos/" + igrejaId + "/" + UUID.randomUUID())
                .tipo("application/pdf")
                .bytes(1024L)
                .nomeOriginal("boleto.pdf")
                .build();
    }

    // -------------------------------------------------------------------------
    // upload()
    // -------------------------------------------------------------------------

    @Nested
    class upload {

        @Test
        void upload_arquivo_pequeno_retorna_response_com_url() {
            byte[] conteudo = "PDF_CONTENT_HERE".getBytes();
            MockMultipartFile arquivo = arquivoMock("boleto.pdf", "application/pdf", conteudo);
            Anexo salvo = anexoArmazenado(anexoId);
            when(repository.save(any(Anexo.class))).thenReturn(salvo);

            AnexoUploadResponse resp = service.upload(arquivo, igrejaId);

            assertThat(resp.id()).isEqualTo(anexoId);
            assertThat(resp.tipo()).isEqualTo("application/pdf");
            assertThat(resp.bytes()).isEqualTo(1024L);
            assertThat(resp.nomeOriginal()).isEqualTo("boleto.pdf");
            assertThat(resp.url()).isEqualTo("/anexos/" + anexoId);
            verify(armazenamento).guardar(
                    startsWith("anexos/" + igrejaId + "/"),
                    eq(conteudo),
                    eq("application/pdf"));
            verify(repository).save(any(Anexo.class));
        }

        @Test
        void upload_arquivo_maior_que_30mb_lanca_excecao() {
            byte[] grande = new byte[31 * 1024 * 1024]; // 31 MB
            MockMultipartFile arquivo = arquivoMock("grande.pdf", "application/pdf", grande);

            assertThatThrownBy(() -> service.upload(arquivo, igrejaId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("30 MB");

            verify(repository, never()).save(any());
            verify(armazenamento, never()).guardar(anyString(), any(), anyString());
        }

        @Test
        void upload_arquivo_sem_tipo_usa_octet_stream() {
            byte[] conteudo = "sem tipo".getBytes();
            MockMultipartFile arquivo = new MockMultipartFile(
                    "arquivo", "sem-tipo.dat", null, conteudo);
            Anexo salvo = anexoArmazenado(anexoId);
            when(repository.save(any(Anexo.class))).thenReturn(salvo);

            service.upload(arquivo, igrejaId);

            verify(armazenamento).guardar(
                    startsWith("anexos/" + igrejaId + "/"),
                    eq(conteudo),
                    eq("application/octet-stream"));
        }

        @Test
        void upload_arquivo_invalido_lanca_excecao() throws IOException {
            MockMultipartFile arquivo = mock(MockMultipartFile.class);
            when(arquivo.getBytes()).thenThrow(new IOException(" Erro de leitura"));

            assertThatThrownBy(() -> service.upload(arquivo, igrejaId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Não foi possível ler");

            verify(repository, never()).save(any());
            verify(armazenamento, never()).guardar(anyString(), any(), anyString());
        }
    }

    // -------------------------------------------------------------------------
    // ler()
    // -------------------------------------------------------------------------

    @Nested
    class ler {

        @Test
        void ler_anexo_existente_retorna_bytes() {
            byte[] conteudo = "conteúdo do anexo".getBytes();
            Anexo anexo = anexoArmazenado(anexoId);
            when(repository.findByIdAndIgrejaId(anexoId, igrejaId)).thenReturn(Optional.of(anexo));
            when(armazenamento.ler(anexo.getChave())).thenReturn(conteudo);

            byte[] resultado = service.ler(anexoId, igrejaId);

            assertThat(resultado).isEqualTo(conteudo);
        }

        @Test
        void ler_anexo_de_outra_igreja_lanca_nao_encontrado() {
            when(repository.findByIdAndIgrejaId(anexoId, igrejaId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.ler(anexoId, igrejaId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // -------------------------------------------------------------------------
    // buscarParaVincular()
    // -------------------------------------------------------------------------

    @Nested
    class buscarParaVincular {

        @Test
        void buscarParaVincular_com_id_nulo_retorna_null() {
            assertThat(service.buscarParaVincular(null, igrejaId)).isNull();
            verify(repository, never()).findByIdAndIgrejaId(any(), any());
        }

        @Test
        void buscarParaVincular_anexo_existente_retorna_anexo() {
            Anexo anexo = anexoArmazenado(anexoId);
            when(repository.findByIdAndIgrejaId(anexoId, igrejaId)).thenReturn(Optional.of(anexo));

            Anexo resultado = service.buscarParaVincular(anexoId, igrejaId);

            assertThat(resultado).isEqualTo(anexo);
        }

        @Test
        void buscarParaVincular_anexo_de_outra_igreja_lanca_excecao() {
            when(repository.findByIdAndIgrejaId(anexoId, igrejaId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.buscarParaVincular(anexoId, igrejaId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("não pertence");
        }
    }

    // -------------------------------------------------------------------------
    // remover()
    // -------------------------------------------------------------------------

    @Nested
    class remover {

        @Test
        void remover_anexo_existente_deleta_e_remove_do_storage() {
            Anexo anexo = anexoArmazenado(anexoId);
            when(repository.findById(anexoId)).thenReturn(Optional.of(anexo));
            doNothing().when(repository).delete(anexo);

            service.remover(anexoId);

            verify(repository).delete(anexo);
            verify(armazenamento).remover(anexo.getChave());
        }

        @Test
        void remover_anexo_inexistente_lanca_nao_encontrado() {
            when(repository.findById(anexoId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.remover(anexoId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(repository, never()).delete(any());
            verify(armazenamento, never()).remover(anyString());
        }
    }
}
