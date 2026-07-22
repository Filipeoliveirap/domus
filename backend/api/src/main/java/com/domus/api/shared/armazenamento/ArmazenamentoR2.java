package com.domus.api.shared.armazenamento;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import java.net.URI;

/**
 * Cloudflare R2 — compatível com a API do S3, então o SDK da AWS serve sem adaptação.
 *
 * <p>Bucket PRIVADO e diferente do bucket de backup (aquele é write-only por desenho).
 * Ninguém lê daqui a não ser o próprio Domus, servindo em GET /fotos/{id}.
 */
@Component
@ConditionalOnProperty(name = "app.fotos.armazenamento", havingValue = "r2", matchIfMissing = true)
@Slf4j
public class ArmazenamentoR2 implements ArmazenamentoFotos {

    @Value("${app.fotos.r2.endpoint}")   private String endpoint;
    @Value("${app.fotos.r2.bucket}")     private String bucket;
    @Value("${app.fotos.r2.access-key}") private String accessKey;
    @Value("${app.fotos.r2.secret-key}") private String secretKey;

    private S3Client cliente;

    @PostConstruct
    void iniciar() {
        // Credencial ausente não pode derrubar o boot da aplicação — só falha a foto,
        // na hora do upload/leitura. Isto acontece hoje (bucket ainda não existe) e vai
        // continuar acontecendo em qualquer ambiente sem as variáveis de R2 configuradas.
        if (endpoint == null || endpoint.isBlank()) {
            log.warn("Armazenamento de fotos: R2 sem endpoint configurado — upload/leitura "
                    + "de foto vai falhar até app.fotos.r2.* ser preenchido.");
            return;
        }
        cliente = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                // R2 ignora a região, mas o SDK exige uma.
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
        log.info("Armazenamento de fotos: R2, bucket={}", bucket);
    }

    @Override
    public void guardar(String chave, byte[] conteudo, String tipo) {
        exigirCliente();
        try {
            cliente.putObject(PutObjectRequest.builder()
                    .bucket(bucket).key(chave).contentType(tipo).build(),
                    RequestBody.fromBytes(conteudo));
        } catch (S3Exception e) {
            throw new ArmazenamentoException("Falha ao guardar foto: " + chave, e);
        }
    }

    @Override
    public byte[] ler(String chave) {
        exigirCliente();
        try {
            return cliente.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(chave).build()).asByteArray();
        } catch (S3Exception e) {
            throw new ArmazenamentoException("Falha ao ler foto: " + chave, e);
        }
    }

    @Override
    public void remover(String prefixo) {
        if (cliente == null) {
            log.warn("Armazenamento de fotos: R2 não configurado, ignorando remoção. prefixo={}", prefixo);
            return;
        }
        try {
            ListObjectsV2Response lista = cliente.listObjectsV2(
                    ListObjectsV2Request.builder().bucket(bucket).prefix(prefixo).build());
            for (S3Object o : lista.contents()) {
                cliente.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucket).key(o.key()).build());
            }
        } catch (S3Exception e) {
            // Não relança: remoção que falha não pode derrubar a operação de negócio que a
            // pediu (trocar foto, arquivar pessoa). Vira lixo no bucket, que a rotina pega.
            log.error("Falha ao remover prefixo do bucket. prefixo={}", prefixo, e);
        }
    }

    /** @throws ArmazenamentoException se o R2 não foi configurado (endpoint em branco). */
    private void exigirCliente() {
        if (cliente == null) {
            throw new ArmazenamentoException(
                    "Armazenamento de fotos (R2) não configurado: app.fotos.r2.endpoint está vazio.", null);
        }
    }
}
