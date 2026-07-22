# Upload de Foto — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir enviar foto para pessoa, evento e igreja, guardada em bucket R2 privado e servida pelo próprio Domus com autenticação e isolamento por igreja.

**Architecture:** O armazenamento fica atrás de uma interface (`ArmazenamentoFotos`), com implementação R2 e uma de memória para teste — mesmo padrão do `EmailService`. O processamento de imagem é isolado num componente próprio, porque é onde mora o risco (EXIF, bomba de descompressão, memória). O id da foto é imutável, o que permite servir com `Cache-Control: immutable` e reduzir a API a uma requisição por imagem por navegador.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL, AWS SDK v2 (S3, compatível com R2), Thumbnailator; Next.js 16, TypeScript, TanStack Query, CSS Modules.

**Spec:** `docs/superpowers/specs/2026-07-22-upload-foto-design.md` — leia antes de começar.

## Global Constraints

- `igreja_id` SEMPRE do JWT (`usuarioAutenticado.getIgrejaId()`), NUNCA do corpo da requisição.
- Camadas `controller → service → repository`; services retornam DTOs, nunca entidades.
- **Esconder no front não é esconder.** Restrição por perfil se faz no backend.
- **Pergunte pela capacidade, não pela identidade** — autorização via `Permissoes`/`lib/permissoes.ts`, nunca comparando string de perfil.
- **Dependa de abstração onde há troca prevista** (`EmailService` é o exemplo bom do projeto). Onde a troca não é prevista, interface é cerimônia.
- Comentários, Javadoc e mensagens ao usuário em **português brasileiro**.
- `notificar.sucesso/erro/aviso/info` no front — NUNCA `toast` do sonner. NUNCA `window.confirm`.
- Mobile faz parte da entrega (375px), sem overflow horizontal.
- Invalidação de cache via `invalidarCache`; nunca `queryClient.invalidateQueries` em componente.
- **Sem `Co-Authored-By`** em commits ou PRs.
- Não encadeie `&& echo OK` depois de um pipe — use `$?` ou `${PIPESTATUS[0]}`.
- `mvn -q test` precisa do `.env` carregado; o `.env` tem um espaço sem aspas em `EMAIL_FROM` que quebra `source` puro — contorne sem editar o `.env`.
- Banco: Neon remoto compartilhado. Não deixe linha de teste para trás.
- Migration atual: **V1**. A próxima é **V2**.

---

## ⚠️ Dois pontos que a spec não previu — leia antes da Task 1

**1. Tirar o EXIF sem aplicar a rotação deixa foto de celular deitada.**

Celular grava a foto no sensor e anota "gire 90°" no EXIF. Se a gente apaga o EXIF sem aplicar
a rotação antes, toda foto tirada em pé aparece **deitada**. É o bug mais visível possível e
some no teste se só usarmos imagem gerada por código, que nunca tem EXIF.

O Thumbnailator resolve (`Thumbnails.of(...).useExifOrientation(true)`), mas **precisa de teste
com arquivo real** que tenha a tag.

**2. Limite de 5 MB NÃO limita memória.**

Um PNG de 5 MB pode decodificar para gigabytes — é a *decompression bomb*. Decodificar antes de
conferir dimensão derruba a aplicação com um único upload.

Portanto: **ler as dimensões do cabeçalho primeiro** (`ImageIO.getImageReaders`, sem decodificar
os pixels) e recusar acima de **50 megapixels** antes de tocar no conteúdo.

**3. Desvio consciente da spec — WebP não entra como ENTRADA.**

A spec lista JPEG, PNG e WebP como aceitos. O `ImageIO` do Java 21 **não lê WebP** sem
dependência extra. Como o seletor de arquivo de celular e de desktop entrega JPEG ou PNG em
praticamente todos os casos, este plano aceita **JPEG e PNG** e evita mais uma dependência.
A **saída** é sempre JPEG. Registre isso no relatório e no BACKLOG para o autor decidir depois.

---

## File Structure

**Backend — criar**
- `db/migration/V2__foto.sql`
- `modules/foto/Foto.java` — entidade
- `modules/foto/FotoRepository.java`
- `modules/foto/FotoService.java` — orquestra validação, processamento e armazenamento
- `modules/foto/FotoController.java` — `POST /fotos`, `GET /fotos/{id}`
- `modules/foto/ProcessadorImagem.java` — validação e redimensionamento (onde mora o risco)
- `modules/foto/TamanhoFoto.java` — enum `DISPLAY`, `THUMB`
- `modules/foto/DTOs/FotoResponse.java`
- `modules/foto/LimpezaFotosJob.java` — órfãs e arquivadas
- `shared/armazenamento/ArmazenamentoFotos.java` — interface
- `shared/armazenamento/ArmazenamentoR2.java` — implementação
- `shared/armazenamento/ArmazenamentoEmMemoria.java` — para teste

**Backend — modificar**
- `modules/pessoa/Pessoa.java`, `modules/evento/Evento.java`, `modules/igreja/Igreja.java` — `foto`/`logoUrl` viram FK
- `config/SecurityConfig.java` — matchers de `/fotos/**`
- `application.properties` — multipart, R2, prazos de limpeza
- `pom.xml` — AWS SDK v2 e Thumbnailator

**Frontend — criar**
- `src/components/common/UploadFoto/UploadFoto.tsx` (+ CSS)
- `src/hooks/foto/useUploadFoto.ts`
- `src/lib/urlFoto.ts` — monta a URL a partir do id

---

### Task 1: Schema e dependências

**Files:**
- Create: `src/main/resources/db/migration/V2__foto.sql`
- Create: `src/main/java/com/domus/api/modules/foto/Foto.java`
- Create: `src/main/java/com/domus/api/modules/foto/FotoRepository.java`
- Create: `src/main/java/com/domus/api/modules/foto/TamanhoFoto.java`
- Modify: `pom.xml`
- Modify: `modules/pessoa/Pessoa.java`, `modules/evento/Evento.java`, `modules/igreja/Igreja.java`

**Interfaces:**
- Produces: entidade `Foto` (getters `getId`, `getIgreja`, `getChave`, `getTipo`, `getBytes`, `getCreatedAt`), enum `TamanhoFoto{DISPLAY,THUMB}`, `FotoRepository`.

- [ ] **Step 1: Migration**

```sql
-- V2: fotos de pessoa, evento e igreja.
--
-- O bucket é PRIVADO e a imagem é servida pelo próprio Domus (GET /fotos/{id}), com
-- sessão e igreja validadas. URL pública de R2 seria permanente e sem autenticação —
-- inaceitável para rosto de membro, inclusive criança.

CREATE TABLE foto (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id  UUID         NOT NULL REFERENCES igreja(id),
    chave      VARCHAR(255) NOT NULL UNIQUE,
    tipo       VARCHAR(50)  NOT NULL,
    bytes      BIGINT       NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_foto_bytes_positivo CHECK (bytes > 0)
);

CREATE INDEX idx_foto_igreja ON foto (igreja_id);
-- A rotina de órfãs filtra por idade; sem este índice ela varre a tabela inteira.
CREATE INDEX idx_foto_created_at ON foto (created_at);

-- As colunas de foto deixam de ser texto e viram FK.
--
-- ON DELETE RESTRICT é a defesa que importa: a rotina de limpeza decide por AUSÊNCIA de
-- referência, e um erro na consulta dela apagaria a foto de alguém para sempre. Com a FK,
-- o banco recusa — a proteção não depende de a consulta estar certa.
ALTER TABLE pessoa
    DROP COLUMN foto,
    ADD COLUMN foto_id UUID REFERENCES foto(id) ON DELETE RESTRICT;

ALTER TABLE evento
    DROP COLUMN foto,
    ADD COLUMN foto_id UUID REFERENCES foto(id) ON DELETE RESTRICT;

ALTER TABLE igreja
    DROP COLUMN logo_url,
    ADD COLUMN logo_foto_id UUID REFERENCES foto(id) ON DELETE RESTRICT;

CREATE INDEX idx_pessoa_foto ON pessoa (foto_id);
CREATE INDEX idx_evento_foto ON evento (foto_id);
CREATE INDEX idx_igreja_logo ON igreja (logo_foto_id);

COMMENT ON TABLE foto IS
    'Metadado da foto. Os bytes vivem no R2 (bucket privado); "chave" é o prefixo lá.';
```

> `DROP COLUMN` é seguro: as três colunas estão vazias em dev e em produção (o upload nunca existiu).

- [ ] **Step 2: Dependências no `pom.xml`**

Dentro de `<dependencies>`:

```xml
        <!-- R2 é compatível com a API do S3; o SDK da AWS fala com ele sem adaptação. -->
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>s3</artifactId>
            <version>2.31.7</version>
        </dependency>

        <!-- Redimensiona e, crucialmente, APLICA a orientação do EXIF antes de descartá-lo.
             Sem isso, foto de celular tirada em pé aparece deitada. -->
        <dependency>
            <groupId>net.coobird</groupId>
            <artifactId>thumbnailator</artifactId>
            <version>0.4.20</version>
        </dependency>
```

- [ ] **Step 3: Enum e entidade**

```java
package com.domus.api.modules.foto;

/** Versões servidas. O original é guardado mas nunca servido. */
public enum TamanhoFoto {
    /** 1200px no maior lado — banner, detalhe, post. */
    DISPLAY(1200),
    /** 200px no maior lado — avatar em lista. */
    THUMB(200);

    private final int ladoMaximo;

    TamanhoFoto(int ladoMaximo) { this.ladoMaximo = ladoMaximo; }

    public int getLadoMaximo() { return ladoMaximo; }

    /** Sufixo da chave no bucket: `{chave}/display.jpg`. */
    public String sufixo() { return name().toLowerCase() + ".jpg"; }
}
```

```java
package com.domus.api.modules.foto;

import com.domus.api.modules.igreja.Igreja;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Metadado da foto. Os BYTES vivem no R2 — aqui só o que o banco precisa saber para
 * localizar, isolar por igreja e decidir sobre limpeza.
 */
@Entity
@Table(name = "foto")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Foto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    /** Prefixo no bucket. As três versões vivem sob ele: `{chave}/original`, `/display.jpg`, `/thumb.jpg`. */
    @Column(nullable = false, unique = true)
    private String chave;

    /** Tipo do ORIGINAL. As versões derivadas são sempre JPEG. */
    @Column(nullable = false, length = 50)
    private String tipo;

    /** Tamanho do original, para acompanhar consumo do bucket. */
    @Column(nullable = false)
    private long bytes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

```java
package com.domus.api.modules.foto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FotoRepository extends JpaRepository<Foto, UUID> {

    Optional<Foto> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    /**
     * Fotos órfãs: mais velhas que o corte e que NENHUMA das três tabelas referencia.
     *
     * <p>Acontece quando alguém envia a foto e abandona o formulário sem salvar. Sem esta
     * limpeza, todo formulário abandonado deixa lixo permanente no bucket.
     */
    @Query("""
        SELECT f FROM Foto f
        WHERE f.createdAt < :corte
          AND NOT EXISTS (SELECT 1 FROM Pessoa p WHERE p.foto = f)
          AND NOT EXISTS (SELECT 1 FROM Evento e WHERE e.foto = f)
          AND NOT EXISTS (SELECT 1 FROM Igreja i WHERE i.logoFoto = f)
    """)
    List<Foto> buscarOrfas(@Param("corte") LocalDateTime corte);
}
```

- [ ] **Step 4: Trocar os campos nas três entidades**

Em `Pessoa.java`, remova `private String foto;` e coloque:

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "foto_id")
    private com.domus.api.modules.foto.Foto foto;
```

Em `Evento.java`, o mesmo (`foto_id`). Em `Igreja.java`, remova `logoUrl` e coloque o campo
`logoFoto` mapeado em `logo_foto_id`.

- [ ] **Step 5: Compilar e ver o que quebrou**

Run: `mvn -q compile 2>&1 | grep -E "\.java:\[" | head -20; echo "EXIT=${PIPESTATUS[0]}"`

Espere **muitos** erros: todo lugar que lia `pessoa.getFoto()` como `String` agora recebe `Foto`.
Corrija cada um devolvendo o **id** (`p.getFoto() != null ? p.getFoto().getId() : null`) nos DTOs.
Os DTOs passam a expor `UUID fotoId` no lugar de `String foto`.

- [ ] **Step 6: Suíte**

Run: `mvn -q test; echo "EXIT=$?"` → `EXIT=0`. Ajuste os testes que construíam `foto("url")`.

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/resources/db/migration src/main/java src/test
git commit -m "feat(foto): schema, entidade e FK nas tres tabelas"
```

---

### Task 2: Armazenamento atrás de interface

**Files:**
- Create: `shared/armazenamento/ArmazenamentoFotos.java`
- Create: `shared/armazenamento/ArmazenamentoR2.java`
- Create: `shared/armazenamento/ArmazenamentoEmMemoria.java`
- Modify: `src/main/resources/application.properties`

**Por que interface:** é exatamente o caso que o `CLAUDE.md` chama de troca prevista — o mesmo
padrão do `EmailService` (`Log` em dev, `Resend` em produção). E sem uma implementação de
memória, todo teste de foto precisaria de rede.

**Interfaces:**
- Produces: `ArmazenamentoFotos.guardar(String chave, byte[] conteudo, String tipo)`, `.ler(String chave) → byte[]`, `.remover(String prefixo)`.

- [ ] **Step 1: A interface**

```java
package com.domus.api.shared.armazenamento;

/**
 * Onde os bytes da foto ficam. Trocável por desenho (mesmo motivo do EmailService):
 * hoje R2, amanhã outro provedor, e nos testes memória.
 *
 * <p>O banco guarda apenas a CHAVE — nunca uma URL. Guardar URL acoplaria o dado ao
 * provedor, e trocar de provedor viraria migration.
 */
public interface ArmazenamentoFotos {

    void guardar(String chave, byte[] conteudo, String tipo);

    /** @throws ArmazenamentoException se a chave não existe ou o provedor falhou. */
    byte[] ler(String chave);

    /** Remove tudo sob o prefixo (as três versões de uma foto). Idempotente. */
    void remover(String prefixo);
}
```

```java
package com.domus.api.shared.armazenamento;

public class ArmazenamentoException extends RuntimeException {
    public ArmazenamentoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
```

- [ ] **Step 2: Implementação de memória (usada pelos testes)**

```java
package com.domus.api.shared.armazenamento;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Guarda em memória. Existe para o teste não depender de rede nem de credencial. */
@Component
@Profile("test")
public class ArmazenamentoEmMemoria implements ArmazenamentoFotos {

    private final Map<String, byte[]> arquivos = new ConcurrentHashMap<>();

    @Override
    public void guardar(String chave, byte[] conteudo, String tipo) {
        arquivos.put(chave, conteudo);
    }

    @Override
    public byte[] ler(String chave) {
        byte[] b = arquivos.get(chave);
        if (b == null) throw new ArmazenamentoException("Chave não encontrada: " + chave, null);
        return b;
    }

    @Override
    public void remover(String prefixo) {
        arquivos.keySet().removeIf(k -> k.startsWith(prefixo));
    }

    /** Só para teste: quantos arquivos existem sob um prefixo. */
    public long contar(String prefixo) {
        return arquivos.keySet().stream().filter(k -> k.startsWith(prefixo)).count();
    }
}
```

- [ ] **Step 3: Implementação R2**

```java
package com.domus.api.shared.armazenamento;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
@Profile("!test")
@Slf4j
public class ArmazenamentoR2 implements ArmazenamentoFotos {

    @Value("${app.fotos.r2.endpoint}")   private String endpoint;
    @Value("${app.fotos.r2.bucket}")     private String bucket;
    @Value("${app.fotos.r2.access-key}") private String accessKey;
    @Value("${app.fotos.r2.secret-key}") private String secretKey;

    private S3Client cliente;

    @PostConstruct
    void iniciar() {
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
        try {
            return cliente.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(chave).build()).asByteArray();
        } catch (S3Exception e) {
            throw new ArmazenamentoException("Falha ao ler foto: " + chave, e);
        }
    }

    @Override
    public void remover(String prefixo) {
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
}
```

- [ ] **Step 4: Configuração**

Em `application.properties`:

```properties
# ─── Fotos ───
# O multipart do Spring recusa ANTES de ler o arquivo inteiro — é a primeira barreira.
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=6MB

app.fotos.r2.endpoint=${R2_FOTOS_ENDPOINT:}
app.fotos.r2.bucket=${R2_FOTOS_BUCKET:}
app.fotos.r2.access-key=${R2_FOTOS_ACCESS_KEY:}
app.fotos.r2.secret-key=${R2_FOTOS_SECRET_KEY:}

# Prazos de limpeza. Configuráveis porque são números que só o uso real ajusta.
app.fotos.orfa-horas=${FOTOS_ORFA_HORAS:24}
app.fotos.arquivada-meses=${FOTOS_ARQUIVADA_MESES:6}
```

Acrescente as quatro variáveis de R2 ao `.env.example` e ao `deploy/.env.prod.example`.

- [ ] **Step 5: Compilar e commitar**

Run: `mvn -q compile; echo "EXIT=$?"` → `EXIT=0`

```bash
git add src/main/java/com/domus/api/shared/armazenamento src/main/resources/application.properties .env.example deploy
git commit -m "feat(foto): armazenamento atras de interface (R2 e memoria)"
```

---

### Task 3: Processamento de imagem — onde mora o risco

**Files:**
- Create: `modules/foto/ProcessadorImagem.java`
- Test: `src/test/java/com/domus/api/modules/foto/ProcessadorImagemTest.java`
- Test fixture: `src/test/resources/fotos/com-exif-girada.jpg`

**Esta é a task mais perigosa do plano.** Três coisas podem dar errado em silêncio: foto
deitada, GPS preservado, e memória estourada por imagem pequena.

**Interfaces:**
- Produces: `ProcessadorImagem.validarEProcessar(byte[] entrada) → ImagemProcessada` com campos `original`, `display`, `thumb` (todos `byte[]`) e `tipoOriginal` (String).

- [ ] **Step 1: Escrever os testes que falham**

```java
package com.domus.api.modules.foto;

import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessadorImagemTest {

    private final ProcessadorImagem processador = new ProcessadorImagem();

    private byte[] jpegDe(int largura, int altura) throws IOException {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    @Test
    void geraDisplayEThumbNosTamanhosCertos() throws IOException {
        var r = processador.validarEProcessar(jpegDe(3000, 2000));

        BufferedImage display = ImageIO.read(new ByteArrayInputStream(r.display()));
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(r.thumb()));

        assertThat(display.getWidth()).isEqualTo(1200);
        assertThat(thumb.getWidth()).isEqualTo(200);
    }

    @Test
    void naoAumentaImagemMenorQueOAlvo() throws IOException {
        // Esticar uma foto de 100px para 1200px só produz borrão e ocupa espaço.
        var r = processador.validarEProcessar(jpegDe(100, 80));
        BufferedImage display = ImageIO.read(new ByteArrayInputStream(r.display()));
        assertThat(display.getWidth()).isEqualTo(100);
    }

    @Test
    void recusaArquivoQueNaoEImagem() {
        byte[] lixo = "isto nao e uma imagem, e um .jpg mentiroso".getBytes();

        assertThatThrownBy(() -> processador.validarEProcessar(lixo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não é uma imagem");
    }

    @Test
    void recusaImagemAcimaDoLimiteDePixels() throws IOException {
        // Bomba de descompressão: arquivo pequeno, bitmap gigante. O limite de 5MB do
        // multipart não protege contra isto — é preciso olhar a DIMENSÃO antes de decodificar.
        assertThatThrownBy(() -> processador.validarEProcessar(jpegDe(9000, 9000)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("grande demais");
    }

    @Test
    void aplicaOrientacaoDoExifEDescartaOsMetadados() throws IOException {
        // Foto de celular tirada em pé: os pixels vêm deitados e o EXIF diz "gire".
        // Se apagarmos o EXIF sem aplicar a rotação, ela aparece deitada para sempre.
        try (InputStream in = getClass().getResourceAsStream("/fotos/com-exif-girada.jpg")) {
            assertThat(in).as("fixture com EXIF precisa existir").isNotNull();
            var r = processador.validarEProcessar(in.readAllBytes());

            BufferedImage display = ImageIO.read(new ByteArrayInputStream(r.display()));
            // A fixture é mais alta que larga DEPOIS de aplicada a rotação.
            assertThat(display.getHeight()).isGreaterThan(display.getWidth());

            // E o EXIF não sobrevive: procuramos o marcador APP1, onde ele mora num JPEG.
            assertThat(contemMarcadorExif(r.display())).isFalse();
        }
    }

    /** APP1 (0xFFE1) é o segmento onde o JPEG guarda EXIF — incluindo GPS. */
    private boolean contemMarcadorExif(byte[] jpeg) {
        for (int i = 0; i < jpeg.length - 1; i++) {
            if ((jpeg[i] & 0xFF) == 0xFF && (jpeg[i + 1] & 0xFF) == 0xE1) return true;
        }
        return false;
    }
}
```

- [ ] **Step 2: Criar a fixture com EXIF**

O teste precisa de um JPEG **real** com tag de orientação — imagem gerada por código nunca tem
EXIF, e sem a fixture esse teste passaria sem provar nada.

```bash
mkdir -p src/test/resources/fotos
# Gera uma imagem deitada (larga) e marca "girar 90°" no EXIF.
docker run --rm -v "$PWD/src/test/resources/fotos:/out" dpokidov/imagemagick \
  -size 400x200 xc:navy /out/com-exif-girada.jpg
docker run --rm -v "$PWD/src/test/resources/fotos:/out" --entrypoint exiftool \
  dpokidov/imagemagick -Orientation=6 -n -overwrite_original /out/com-exif-girada.jpg
```

Se as imagens Docker não estiverem disponíveis, gere de outra forma e **confirme no relatório**
que a fixture tem `Orientation=6` — sem isso o teste é decorativo.

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q test -Dtest=ProcessadorImagemTest`
Expected: FAIL — `ProcessadorImagem` não existe.

- [ ] **Step 4: Implementar**

```java
package com.domus.api.modules.foto;

import com.domus.api.shared.exception.BusinessException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Valida e transforma a imagem enviada. É aqui que mora o risco desta feature.
 *
 * <p>Três perigos, na ordem em que aparecem:
 * <ol>
 *   <li><b>Arquivo que não é imagem.</b> Validar pela extensão é confiar em quem envia;
 *       validamos tentando LER o conteúdo.</li>
 *   <li><b>Bomba de descompressão.</b> Um PNG de 5 MB pode virar gigabytes de bitmap. O
 *       limite do multipart não protege — é preciso olhar a DIMENSÃO no cabeçalho antes
 *       de decodificar os pixels.</li>
 *   <li><b>EXIF.</b> Guarda a orientação e, pior, a COORDENADA DE GPS de onde a foto foi
 *       tirada. Descartar é obrigatório; aplicar a rotação ANTES de descartar também,
 *       senão toda foto de celular tirada em pé aparece deitada.</li>
 * </ol>
 */
@Component
public class ProcessadorImagem {

    private static final Set<String> TIPOS_ACEITOS = Set.of("image/jpeg", "image/png");
    /** ~50 megapixels. Acima disso o bitmap descomprimido passa de 200 MB. */
    private static final long MAX_PIXELS = 50_000_000L;

    public record ImagemProcessada(byte[] original, byte[] display, byte[] thumb, String tipoOriginal) {}

    public ImagemProcessada validarEProcessar(byte[] entrada) {
        String tipo = detectarTipo(entrada);
        if (!TIPOS_ACEITOS.contains(tipo)) {
            throw new BusinessException("FORMATO_NAO_ACEITO",
                    "Envie uma imagem JPEG ou PNG.");
        }

        validarDimensoes(entrada);

        return new ImagemProcessada(
                entrada,
                redimensionar(entrada, TamanhoFoto.DISPLAY),
                redimensionar(entrada, TamanhoFoto.THUMB),
                tipo);
    }

    /** Lê o tipo do CONTEÚDO. Um ".jpg" pode ser qualquer coisa. */
    private String detectarTipo(byte[] entrada) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(entrada))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(in);
            if (!leitores.hasNext()) {
                throw new BusinessException("ARQUIVO_INVALIDO",
                        "Este arquivo não é uma imagem.");
            }
            String formato = leitores.next().getFormatName().toLowerCase();
            return switch (formato) {
                case "jpeg", "jpg" -> "image/jpeg";
                case "png" -> "image/png";
                default -> "image/" + formato;
            };
        } catch (IOException e) {
            throw new BusinessException("ARQUIVO_INVALIDO", "Este arquivo não é uma imagem.");
        }
    }

    /**
     * Confere a dimensão SEM decodificar os pixels — é o que impede a bomba de
     * descompressão de estourar a memória antes de qualquer checagem.
     */
    private void validarDimensoes(byte[] entrada) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(entrada))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(in);
            ImageReader leitor = leitores.next();
            leitor.setInput(in);
            long pixels = (long) leitor.getWidth(0) * leitor.getHeight(0);
            leitor.dispose();
            if (pixels > MAX_PIXELS) {
                throw new BusinessException("IMAGEM_GRANDE_DEMAIS",
                        "Esta imagem é grande demais. Envie uma com no máximo 50 megapixels.");
            }
        } catch (IOException e) {
            throw new BusinessException("ARQUIVO_INVALIDO", "Este arquivo não é uma imagem.");
        }
    }

    private byte[] redimensionar(byte[] entrada, TamanhoFoto tamanho) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(entrada))
                    // Aplica a rotação do EXIF ANTES de regravar — sem isto a foto sai deitada.
                    .useExifOrientation(true)
                    // Cabe DENTRO da caixa e nunca AUMENTA: esticar 100px para 1200px só borra.
                    .size(tamanho.getLadoMaximo(), tamanho.getLadoMaximo())
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .outputQuality(0.85)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("FALHA_PROCESSAMENTO",
                    "Não foi possível processar esta imagem. Tente outra.");
        }
    }
}
```

> Se o Thumbnailator ampliar imagens menores que o alvo (comportamento depende da versão),
> acrescente uma checagem que devolve a imagem regravada sem redimensionar quando ela já é
> menor. O teste `naoAumentaImagemMenorQueOAlvo` é quem cobra isso.

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn -q test -Dtest=ProcessadorImagemTest`
Expected: PASS — 5 testes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/foto/ProcessadorImagem.java \
        src/test/java/com/domus/api/modules/foto src/test/resources/fotos
git commit -m "feat(foto): validacao, redimensionamento e descarte de EXIF"
```

---

### Task 4: Service e endpoints

**Files:**
- Create: `modules/foto/FotoService.java`, `FotoController.java`, `DTOs/FotoResponse.java`
- Modify: `config/SecurityConfig.java`
- Test: `src/test/java/com/domus/api/modules/foto/FotoServiceTest.java`

**Interfaces:**
- Consumes: `ProcessadorImagem` (Task 3), `ArmazenamentoFotos` (Task 2), `FotoRepository` (Task 1)
- Produces: `FotoService.enviar(MultipartFile, UUID igrejaId) → FotoResponse`, `.ler(UUID id, TamanhoFoto, UUID igrejaId) → byte[]`, `.remover(UUID id)`

- [ ] **Step 1: Testes**

```java
    @Test
    void enviarGuardaAsTresVersoes() {
        var resposta = service.enviar(arquivoJpeg(), igrejaId);

        assertThat(resposta.id()).isNotNull();
        // original + display + thumb
        assertThat(armazenamento.contar(chaveDe(resposta.id()))).isEqualTo(3);
    }

    @Test
    void lerFotoDeOutraIgrejaDaNaoEncontrado() {
        var resposta = service.enviar(arquivoJpeg(), igrejaId);

        assertThatThrownBy(() -> service.ler(resposta.id(), TamanhoFoto.THUMB, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removerApagaTodasAsVersoesDoBucket() {
        var resposta = service.enviar(arquivoJpeg(), igrejaId);
        service.remover(resposta.id());

        assertThat(armazenamento.contar(chaveDe(resposta.id()))).isZero();
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=FotoServiceTest` → FAIL (classe não existe).

- [ ] **Step 3: O service**

Pontos obrigatórios da implementação:

- A **chave é aleatória** (`UUID.randomUUID()`), nunca derivada do nome enviado. O nome do
  arquivo do usuário jamais vira caminho no bucket.
- `enviar` guarda as três versões sob `fotos/{igrejaId}/{chaveAleatoria}/` e só então grava a
  linha em `foto` — se o storage falhar, não fica linha órfã apontando para nada.
- `ler` busca por `findByIdAndIgrejaId`: foto de outra igreja é **404**, não 403. Dizer
  "existe mas você não pode" já entrega a informação de que existe.
- `remover` apaga do bucket **e** a linha.

- [ ] **Step 4: O controller**

```java
    /** O tenant vem do JWT; não há como pedir a foto de outra igreja. */
    @PostMapping
    public ResponseEntity<FotoResponse> enviar(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fotoService.enviar(arquivo, usuarioAutenticado.getIgrejaId()));
    }

    /**
     * O id de uma foto NUNCA é reaproveitado — trocar a foto cria outro. Por isso a resposta
     * pode ser marcada como imutável, e o navegador busca cada imagem UMA vez.
     *
     * <p>É o que torna viável servir imagem pela API: sem isto, uma lista com 20 avatares
     * bateria no servidor a cada visita.
     */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> ler(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "DISPLAY") TamanhoFoto tamanho) {
        byte[] bytes = fotoService.ler(id, tamanho, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(bytes);
    }
```

- [ ] **Step 5: SecurityConfig**

⚠️ **Matchers são ordenados e o primeiro vence** — este projeto já foi mordido por isso duas
vezes. Acrescente antes do `anyRequest()`:

```java
                        //Fotos: qualquer perfil VÊ (avatar aparece em toda tela);
                        //ENVIAR é de quem gerencia o que a foto ilustra.
                        .requestMatchers(HttpMethod.GET, "/fotos/*")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.POST, "/fotos")
                        .hasAnyRole(ADMIN, LIDER)
```

- [ ] **Step 6: Rodar e commitar**

Run: `mvn -q test; echo "EXIT=$?"` → `EXIT=0`

```bash
git add src/main/java/com/domus/api/modules/foto src/main/java/com/domus/api/config/SecurityConfig.java src/test
git commit -m "feat(foto): endpoints de envio e leitura com cache imutavel"
```

---

### Task 5: Rotinas de limpeza

**Files:**
- Create: `modules/foto/LimpezaFotosJob.java`
- Test: `src/test/java/com/domus/api/modules/foto/LimpezaFotosJobTest.java`

**Interfaces:**
- Consumes: `FotoRepository.buscarOrfas(LocalDateTime)` (Task 1), `FotoService.remover(UUID)` (Task 4)

- [ ] **Step 1: O teste que mais importa**

```java
    @Test
    void naoRemoveFotoQueEstaEmUso() {
        // A rotina decide por AUSÊNCIA de referência. Um erro aqui apaga a foto de alguém
        // para sempre — este é o teste que impede isso.
        Foto usada = fotoDe(agora().minusDays(30));
        pessoaComFoto(usada);

        job.limparOrfas();

        verify(fotoService, never()).remover(usada.getId());
    }

    @Test
    void removeOrfaMaisVelhaQueOCorte() {
        Foto orfa = fotoDe(agora().minusHours(25));
        when(fotoRepository.buscarOrfas(any())).thenReturn(List.of(orfa));

        job.limparOrfas();

        verify(fotoService).remover(orfa.getId());
    }

    @Test
    void naoRemoveOrfaRECEM_ENVIADA() {
        // Alguém acabou de enviar e ainda está preenchendo o formulário.
        when(fotoRepository.buscarOrfas(any())).thenReturn(List.of());

        job.limparOrfas();

        verify(fotoService, never()).remover(any());
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=LimpezaFotosJobTest`

- [ ] **Step 3: Implementar**

Duas rotinas, ambas com `@Scheduled` (o projeto já usa em `OutboxProcessador`), rodando de
madrugada e **registrando quantas removeram** — uma limpeza silenciosa é uma limpeza que
ninguém percebe estar errada:

```java
    /** Órfãs: enviadas e nunca vinculadas. Roda de hora em hora. */
    @Scheduled(fixedDelayString = "PT1H")
    public void limparOrfas() { ... }

    /**
     * Fotos de pessoas arquivadas há mais que o corte.
     *
     * <p>NÃO removemos no arquivamento: arquivar é soft delete e a Fase 3 prevê desarquivar.
     * Apagar a foto na hora tornaria o desarquivamento parcial — a pessoa voltaria sem rosto,
     * sem recuperação, por causa de centavos de armazenamento.
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void limparDeArquivadas() { ... }
```

- [ ] **Step 4: Rodar, conferir e commitar**

Run: `mvn -q test; echo "EXIT=$?"` → `EXIT=0`

```bash
git add src/main/java/com/domus/api/modules/foto/LimpezaFotosJob.java src/test
git commit -m "feat(foto): rotinas de limpeza de orfas e de arquivadas"
```

---

### Task 6: Componente de upload no front

**Files:**
- Create: `src/components/common/UploadFoto/UploadFoto.tsx` (+ `.module.css`)
- Create: `src/hooks/foto/useUploadFoto.ts`
- Create: `src/lib/urlFoto.ts`

**Interfaces:**
- Produces: `<UploadFoto valor={fotoId} onChange={(id)=>...} formato="circulo"|"banner" />`, `urlFoto(id, 'THUMB'|'DISPLAY')`

- [ ] **Step 1: A URL**

```ts
/**
 * URL da foto servida pelo próprio Domus.
 *
 * <p>Não é uma URL de storage: o bucket é privado e a imagem passa pela API, que valida
 * sessão e igreja. Por isso `img-src 'self'` da CSP cobre — nenhum domínio novo a liberar.
 */
export function urlFoto(id: string | null | undefined, tamanho: 'THUMB' | 'DISPLAY' = 'DISPLAY') {
  return id ? `/api/fotos/${id}?tamanho=${tamanho}` : null
}
```

- [ ] **Step 2: O hook de envio**

`useUploadFoto()` envia `FormData` com o campo `arquivo`, devolve o `id`, e usa
`notificar.erro` com a mensagem do servidor — os códigos possíveis são `FORMATO_NAO_ACEITO`,
`ARQUIVO_INVALIDO`, `IMAGEM_GRANDE_DEMAIS` e `FALHA_PROCESSAMENTO`, e todos já vêm com texto
pronto para o usuário.

⚠️ Não defina `Content-Type` manualmente: o navegador precisa gerar o *boundary* do multipart.

- [ ] **Step 3: O componente**

Requisitos:
- Área clicável e com *drag & drop*, mostrando a foto atual quando houver.
- **Prévia local antes de enviar** (`URL.createObjectURL`) — a pessoa vê o que escolheu na hora,
  sem esperar a rede.
- **Recorte obrigatório** em `formato="circulo"` (pessoa e logo); opcional em `formato="banner"`.
- Estado de envio e botão de remover.
- Limite de 5 MB conferido **também** no cliente — não como segurança (o servidor decide), mas
  para não fazer alguém no 4G subir 20 MB para ouvir "não".
- Mobile: área com altura confortável para toque, sem overflow.

- [ ] **Step 4: Verificar**

Run: `cd frontend && npx tsc --noEmit && npx eslint src && npx next build; echo "EXIT=$?"`
Expected: `tsc` limpo; eslint com os **5 warnings pré-existentes**; build ok.

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "feat(foto): componente de upload com previa e recorte"
```

---

### Task 7: Ligar nas três telas

**Files:**
- Modify: `src/components/module/pessoas/PessoaForm.tsx`
- Modify: `src/components/module/eventos/EventoForm.tsx`
- Modify: a tela de configurações da igreja
- Modify: todo lugar que exibe foto (listas, drawers, modais)

- [ ] **Step 1: Formulários**

Em `EventoForm.tsx` existe hoje uma área com `<span>Em breve</span>` — substitua pelo
`<UploadFoto formato="banner">`. Em `PessoaForm.tsx` e nas configurações da igreja, use
`formato="circulo"`.

- [ ] **Step 2: Exibição**

Os lugares que hoje fazem `<img src={p.foto}>` passam a `<img src={urlFoto(p.fotoId, 'THUMB')}>`.
O fallback continua sendo `iniciais()` — regra geral do sistema, nunca silhueta genérica.

Use `THUMB` em lista e `DISPLAY` em detalhe: servir 1200px para desenhar um círculo de 40px
gasta banda de quem está no 4G.

- [ ] **Step 3: Tipos**

`PessoaResponse`, `EventoResponse` e o DTO da igreja passam a expor `fotoId: string | null`
no lugar do antigo `foto: string | null`. Ajuste todos os consumidores — o TypeScript aponta.

- [ ] **Step 4: Verificar de ponta a ponta**

Run: `cd frontend && npx tsc --noEmit && npx next build; echo "EXIT=$?"` → `EXIT=0`

Depois, **com a aplicação no ar**: envie uma foto de pessoa, recarregue a lista e confirme que
o avatar aparece. Confira no DevTools que a resposta traz `Cache-Control: immutable` e que a
segunda visita **não** refaz a requisição.

- [ ] **Step 5: Commit**

```bash
git add frontend/src backend/api/src
git commit -m "feat(foto): upload ligado em pessoa, evento e igreja"
```

---

### Task 8: Documentação e configuração de produção

- [ ] **Step 1: Diagrama ER no `CLAUDE.md`**

Acrescente `FOTO` com suas colunas e as relações `PESSOA }o--o| FOTO`, `EVENTO }o--o| FOTO`,
`IGREJA }o--o| FOTO`. Estado atual passa a **V2**.

- [ ] **Step 2: Marcar o item da Fase 2**

"Upload de foto (membro e evento)" vira **FEITO**, no estilo dos demais, citando o que ficou de
fora (galeria, vídeo, CDN de borda).

- [ ] **Step 3: BACKLOG**

Registre: WebP como formato de entrada (fora por dependência), revisão do `next/image` agora que
a URL é `'self'`, e CDN de borda se o volume crescer.

- [ ] **Step 4: Instruções de produção**

No `deploy/README.md`, o que o autor precisa fazer **antes** do deploy: criar o bucket R2
**privado** (separado do de backup), gerar as credenciais e preencher as quatro variáveis
`R2_FOTOS_*` no `.env.prod`. Sem isso a aplicação sobe e o envio falha na primeira tentativa.

- [ ] **Step 5: Commit**

```bash
git add backend/api/CLAUDE.md backend/api/docs deploy/README.md
git commit -m "docs: upload de foto no diagrama ER e no roadmap"
```

---

## Self-Review

**Cobertura da spec:** bucket privado + serviço pela API → Tasks 2, 4; três versões → Tasks 1, 3;
validação e EXIF → Task 3; ciclo de vida → Tasks 4, 5; FK `RESTRICT` → Task 1; componente único →
Tasks 6, 7; cache imutável → Task 4; docs → Task 8.

**Desvio consciente registrado:** WebP não entra como formato de entrada (Task 3, nota no topo).

**Riscos, em ordem:**

1. **A fixture com EXIF.** Sem um JPEG real com `Orientation`, o teste mais importante da Task 3
   passa sem provar nada — e o defeito (foto deitada) só aparece para o usuário.
2. **Ampliação indevida.** Se o Thumbnailator esticar imagem menor que o alvo, avatares de fotos
   pequenas ficam borrados. O teste cobre; a implementação pode precisar do ajuste anotado.
3. **`DROP COLUMN` na V2.** Seguro hoje porque as colunas estão vazias. Se alguém tiver populado
   `pessoa.foto` manualmente antes do deploy, o dado se perde — conferir antes de rodar em produção.
4. **Ordem dos matchers** (Task 4). Já mordeu este projeto duas vezes.
5. **Credenciais do R2 ausentes** derrubam o envio, não a aplicação — o erro aparece só no
   primeiro upload. Por isso a Task 8 põe a configuração no README de deploy.
