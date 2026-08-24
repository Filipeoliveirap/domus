# WebP + Banner 16:9 — Plano de Implementação

> **Para workers agênticos:** USE superpowers-subagent-driven-development (recomendado) ou superpowers-executing-plans para implementar este plano task-by-task. Steps usam checkbox (`- [ ]`) para tracking.

**Goal:** Adicionar suporte a WebP (upload + serve com fallback JPEG) e mudar banner de evento para 16:9, reduzindo banda em ~30% sem perda de qualidade.

**Architecture:** Backend processa uploads em WebP (substituindo Thumbnailator por pipeline customizado com Graphics2D). GET /fotos faz content negotiation via Accept header — serve WebP direto se cliente aceita, senão converte sob demanda com cache in-memory de 5 min. Frontend usa canvas.toBlob('image/webp'). Banners antigos 3:1 continuam funcionando via object-fit: cover.

**Tech Stack:** Java 21, Spring Boot, webp-imageio 0.1.6, Next.js/TypeScript

**Spec:** `backend/api/docs/superpowers/specs/2026-08-24-foto-webp-e-banner-16-9-design.md`

---

## File Structure

**Backend (5 modificações, 1 novo):**
- `backend/api/pom.xml` — adicionar dependência webp-imageio
- `backend/api/src/main/java/com/domus/api/modules/foto/ProcessadorImagem.java` — substituir Thumbnailator por pipeline customizado, aceitar WebP
- `backend/api/src/main/java/com/domus/api/modules/foto/TamanhoFoto.java` — sufixo `.jpg` → `.webp`
- `backend/api/src/main/java/com/domus/api/modules/foto/FotoService.java` — salvar com content-type image/webp
- `backend/api/src/main/java/com/domus/api/modules/foto/FotoController.java` — Accept header + fallback .webp/.jpg
- `backend/api/src/main/java/com/domus/api/modules/foto/CacheFallbackWebP.java` — NOVO: cache in-memory

**Frontend (3 modificações):**
- `frontend/src/components/common/UploadFoto/UploadFoto.tsx` — TIPOS_ACEITOS aceita WebP
- `frontend/src/components/common/UploadFoto/CropperFoto.tsx` — aspect 16/9, SAIDA 1200×675, toBlob WebP
- `frontend/src/components/common/UploadFoto/UploadFoto.module.css` — aspect-ratio 16/9

---

## Task 1: Adicionar webp-imageio e validar que ImageIO lê WebP

**Files:**
- Modify: `backend/api/pom.xml`
- Test: validar manualmente com `mvn dependency:tree`

- [ ] **Step 1: Adicionar dependência no pom.xml**

Localizar a seção `<dependencies>` e adicionar:

```xml
<!-- WebP support via ImageIO plugin. Registrado automaticamente no SPI do ImageIO. -->
<dependency>
    <groupId>org.sejda.imageio</groupId>
    <artifactId>webp-imageio</artifactId>
    <version>0.1.6</version>
</dependency>
```

- [ ] **Step 2: Validar que a dependência foi adicionada**

```bash
cd backend/api && mvn dependency:tree -Dincludes=org.sejda.imageio:webp-imageio
```

Expected: mostrar `org.sejda.imageio:webp-imageio:jar:0.1.6:compile`

- [ ] **Step 3: Commit**

```bash
cd backend/api
git add pom.xml
git commit -m "chore(deps): adicionar webp-imageio para suporte a WebP"
```

---

## Task 2: Reescrever ProcessadorImagem sem Thumbnailator

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/modules/foto/ProcessadorImagem.java` (full rewrite)
- Modify: `backend/api/pom.xml` (adicionar metadata-extractor, remover thumbnailator)
- Test: `backend/api/src/test/java/com/domus/api/modules/foto/ProcessadorImagemTest.java` (existing)

**Rationale:** Thumbnailator não suporta WebP via plugin ImageIO. Precisamos de pipeline customizado usando Graphics2D diretamente, que funciona com qualquer formato suportado pelo ImageIO (JPEG, PNG, WebP).

- [ ] **Step 1: Ler os testes atuais para entender os contratos**

```bash
cat backend/api/src/test/java/com/domus/api/modules/foto/ProcessadorImagemTest.java
```

Identificar os 5 cenários que precisam continuar passando:
1. Gera display e thumb nos tamanhos certos (1200 e 200)
2. Não aumenta imagem menor que o alvo
3. Recusa arquivo que não é imagem
4. Recusa imagem acima do limite de pixels (50MP)
5. Aplica orientação do EXIF e descarta metadados

- [ ] **Step 2: Verificar dependências necessárias**

Verificar se `metadata-extractor` está no pom.xml:

```bash
grep -A2 "metadata-extractor" backend/api/pom.xml
```

Se **não estiver**, adicionar no pom.xml junto com webp-imageio (que foi adicionado na Task 1):

```xml
<!-- Leitura de metadados EXIF/IPTC (rotação, GPS, etc). -->
<dependency>
    <groupId>com.drewnoakes</groupId>
    <artifactId>metadata-extractor</artifactId>
    <version>2.19.0</version>
</dependency>
```

- [ ] **Step 3: Remover Thumbnailator do pom.xml**

Remover a dependência:

```xml
<dependency>
    <groupId>net.coobird</groupId>
    <artifactId>thumbnailator</artifactId>
    <version>0.4.20</version>
</dependency>
```

Razão: não é mais usado após a reescrita.

- [ ] **Step 4: Substituir ProcessadorImagem.java**

Ver implementação completa na Task — substituir todo o conteúdo do arquivo pela nova versão com `Graphics2D` + `metadata-extractor` + `webp-imageio`.

```java
package com.domus.api.modules.foto;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.domus.api.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Valida por conteúdo (nunca extensão), lê dimensão do cabeçalho antes de decodificar pixels
 * (evita bomba de descompressão) e descarta EXIF/GPS. Saída sempre WebP com qualidade 0.85.
 *
 * Substituiu Thumbnailator porque este não suporta WebP via plugin ImageIO.
 * EXIF rotation é aplicada manualmente via metadata-extractor + AffineTransform.
 */
@Component
public class ProcessadorImagem {

    private static final Set<String> TIPOS_ACEITOS = Set.of("image/jpeg", "image/png", "image/webp");

    /** ~50 megapixels. Acima disso o bitmap descomprimido passa de 200 MB. */
    private static final long MAX_PIXELS = 50_000_000L;

    private static final float QUALIDADE_WEBP = 0.85f;

    public record ImagemProcessada(byte[] original, byte[] display, byte[] thumb, String tipoOriginal) {}

    public ImagemProcessada validarEProcessar(byte[] entrada) {
        String tipo = detectarTipo(entrada);
        if (!TIPOS_ACEITOS.contains(tipo)) {
            throw new BusinessException("FORMATO_NAO_ACEITO", "Envie uma imagem JPEG, PNG ou WebP.");
        }

        validarDimensoes(entrada);

        int rotacaoExif = lerRotacaoExif(entrada);

        return new ImagemProcessada(
                entrada,
                redimensionar(entrada, TamanhoFoto.DISPLAY, rotacaoExif),
                redimensionar(entrada, TamanhoFoto.THUMB, rotacaoExif),
                tipo);
    }

    /** Lê o tipo do conteúdo. A extensão do arquivo não participa da decisão. */
    private String detectarTipo(byte[] entrada) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(entrada))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(in);
            if (!leitores.hasNext()) {
                throw new BusinessException("ARQUIVO_INVALIDO", "Este arquivo não é uma imagem.");
            }
            String formato = leitores.next().getFormatName().toLowerCase();
            return switch (formato) {
                case "jpeg", "jpg" -> "image/jpeg";
                case "png" -> "image/png";
                case "webp" -> "image/webp";
                default -> "image/" + formato;
            };
        } catch (IOException e) {
            throw new BusinessException("ARQUIVO_INVALIDO", "Este arquivo não é uma imagem.");
        }
    }

    /** Lê a dimensão do cabeçalho sem decodificar pixels — é o que impede a bomba de descompressão. */
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

    /**
     * Lê a orientação do EXIF (tag 0x0112). Retorna o ângulo de rotação em graus (0, 90, 180 ou 270).
     * Retorna 0 se não houver EXIF ou se a leitura falhar.
     */
    private int lerRotacaoExif(byte[] entrada) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(entrada));
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 == null || !ifd0.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return 0;
            }
            int orientacao = ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            return switch (orientacao) {
                case 3 -> 180;  // Rotated 180°
                case 6 -> 90;   // Rotated 90° CW
                case 8 -> 270;  // Rotated 90° CCW
                default -> 0;
            };
        } catch (Exception e) {
            return 0;
        }
    }

    private byte[] redimensionar(byte[] entrada, TamanhoFoto tamanho, int rotacaoExif) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(entrada));
            if (original == null) {
                throw new BusinessException("FALHA_PROCESSAMENTO", "Não foi possível ler a imagem.");
            }

            BufferedImage rotacionado = aplicarRotacao(original, rotacaoExif);

            int ladoAlvo = tamanho.getLadoMaximo();
            BufferedImage redimensionado = redimensionarMantendoProporcao(rotacionado, ladoAlvo);

            return codificarWebp(redimensionado);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("FALHA_PROCESSAMENTO",
                    "Não foi possível processar esta imagem. Tente outra.");
        }
    }

    /**
     * Aplica rotação via AffineTransform. Retorna a imagem original se rotação for 0.
     */
    private BufferedImage aplicarRotacao(BufferedImage img, int graus) {
        if (graus == 0) return img;

        double radians = Math.toRadians(graus);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int novaLargura = (int) Math.round(img.getWidth() * cos + img.getHeight() * sin);
        int novaAltura = (int) Math.round(img.getHeight() * cos + img.getWidth() * sin);

        BufferedImage rotacionada = new BufferedImage(novaLargura, novaAltura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rotacionada.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        AffineTransform transform = new AffineTransform();
        transform.translate((novaLargura - img.getWidth()) / 2.0, (novaAltura - img.getHeight()) / 2.0);
        transform.rotate(radians, img.getWidth() / 2.0, img.getHeight() / 2.0);
        g2d.drawImage(img, transform, null);
        g2d.dispose();

        return rotacionada;
    }

    /**
     * Redimensiona mantendo aspect ratio. Se a imagem já cabe no alvo, retorna como está
     * (mas regravada para descartar EXIF).
     */
    private BufferedImage redimensionarMantendoProporcao(BufferedImage img, int ladoAlvo) {
        int larguraOriginal = img.getWidth();
        int alturaOriginal = img.getHeight();
        int maiorLado = Math.max(larguraOriginal, alturaOriginal);

        if (maiorLado <= ladoAlvo) {
            return img;
        }

        double fator = (double) ladoAlvo / maiorLado;
        int novaLargura = (int) Math.round(larguraOriginal * fator);
        int novaAltura = (int) Math.round(alturaOriginal * fator);

        BufferedImage redimensionada = new BufferedImage(novaLargura, novaAltura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = redimensionada.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(img, 0, 0, novaLargura, novaAltura, null);
        g2d.dispose();

        return redimensionada;
    }

    /**
     * Codifica BufferedImage em WebP com qualidade 0.85.
     * Usa ImageWriter registrado pelo plugin webp-imageio.
     */
    private byte[] codificarWebp(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) {
            throw new IOException("Nenhum writer WebP disponível. Verifique webp-imageio no classpath.");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALIDADE_WEBP);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
```

**Nota:** A dependência `metadata-extractor` (usada para ler EXIF) já está no pom.xml? Se não estiver, adicionar:

```xml
<dependency>
    <groupId>com.drewnoakes</groupId>
    <artifactId>metadata-extractor</artifactId>
    <version>2.19.0</version>
</dependency>
```

Verificar com: `grep -A2 "metadata-extractor" pom.xml`

- [ ] **Step 3: Rodar os testes existentes**

```bash
cd backend/api && mvn test -Dtest=ProcessadorImagemTest
```

Expected: todos os 5 testes existentes passam. Se falhar, verificar:
- `aplicaOrientacaoDoExifEDescartaOsMetadados`: se a fixture tem EXIF com orientation tag, o código deve aplicar. Se não, pode falhar.
- `geraDisplayEThumbNosTamanhosCertos`: se as dimensões estão corretas (1200 e 200).

- [ ] **Step 4: Commit**

```bash
cd backend/api
git add src/main/java/com/domus/api/modules/foto/ProcessadorImagem.java pom.xml
git commit -m "refactor(foto): substituir Thumbnailator por pipeline customizado

ProcessadorImagem agora usa Graphics2D diretamente, com suporte a WebP
via plugin ImageIO. EXIF rotation é aplicada manualmente via
metadata-extractor + AffineTransform. Saída sempre em WebP 0.85."
```

---

## Task 3: Adicionar teste para WebP no ProcessadorImagemTest

**Files:**
- Modify: `backend/api/src/test/java/com/domus/api/modules/foto/ProcessadorImagemTest.java`

- [ ] **Step 1: Adicionar teste para WebP**

Adicionar no final do arquivo:

```java
@Test
void aceitaWebpComoEntrada() throws IOException {
    // Gera um WebP válido para teste
    byte[] webpBytes = gerarWebpDe(3000, 2000);

    var r = processador.validarEProcessar(webpBytes);

    assertThat(r.original()).isEqualTo(webpBytes);
    assertThat(r.display()).isNotEmpty();
    assertThat(r.thumb()).isNotEmpty();
    assertThat(r.tipoOriginal()).isEqualTo("image/webp");
}

@Test
void saidaESempreWebp() throws IOException {
    byte[] jpegBytes = jpegDe(1500, 1000);
    var r = processador.validarEProcessar(jpegBytes);

    // Verifica magic bytes do WebP: "RIFF....WEBP" (bytes 0-3 e 8-11)
    assertThat(ehWebp(r.display()))
            .describedAs("display deveria ser WebP independente da entrada")
            .isTrue();
    assertThat(ehWebp(r.thumb()))
            .describedAs("thumb deveria ser WebP independente da entrada")
            .isTrue();
}

private byte[] gerarWebpDe(int largura, int altura) throws IOException {
    BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
    ImageWriter writer = writers.next();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
        writer.setOutput(ios);
        writer.write(img);
    } finally {
        writer.dispose();
    }
    return out.toByteArray();
}

private boolean ehWebp(byte[] bytes) {
    if (bytes == null || bytes.length < 12) return false;
    return bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
            && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
}
```

- [ ] **Step 2: Rodar os testes**

```bash
cd backend/api && mvn test -Dtest=ProcessadorImagemTest
```

Expected: 7 testes passam (5 originais + 2 novos)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/domus/api/modules/foto/ProcessadorImagemTest.java
git commit -m "test(foto): adicionar testes para entrada WebP e saída sempre WebP"
```

---

## Task 4: Mudar sufixo no TamanhoFoto

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/modules/foto/TamanhoFoto.java`

- [ ] **Step 1: Atualizar sufixo() para .webp**

```java
/** Sufixo da chave no bucket: `{chave}/display.webp`. */
public String sufixo() { return name().toLowerCase() + ".webp"; }
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/domus/api/modules/foto/TamanhoFoto.java
git commit -m "refactor(foto): TamanhoFoto usa sufixo .webp"
```

---

## Task 5: Atualizar FotoService para salvar WebP

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/modules/foto/FotoService.java`

- [ ] **Step 1: Atualizar os tipos MIME no salvar**

Linhas 39-40:

ANTES:
```java
armazenamentoFotos.guardar(chave + "/" + TamanhoFoto.DISPLAY.sufixo(), imagem.display(), "image/jpeg");
armazenamentoFotos.guardar(chave + "/" + TamanhoFoto.THUMB.sufixo(), imagem.thumb(), "image/jpeg");
```

DEPOIS:
```java
armazenamentoFotos.guardar(chave + "/" + TamanhoFoto.DISPLAY.sufixo(), imagem.display(), "image/webp");
armazenamentoFotos.guardar(chave + "/" + TamanhoFoto.THUMB.sufixo(), imagem.thumb(), "image/webp");
```

- [ ] **Step 2: Rodar testes do FotoService**

```bash
cd backend/api && mvn test -Dtest=FotoServiceTest
```

Expected: todos passam

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/domus/api/modules/foto/FotoService.java
git commit -m "refactor(foto): FotoService salva display e thumb com content-type image/webp"
```

---

## Task 6: Criar CacheFallbackWebP

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/foto/CacheFallbackWebP.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/foto/CacheFallbackWebPTest.java`

- [ ] **Step 1: Criar a classe de cache**

```java
package com.domus.api.modules.foto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cache in-memory para conversões WebP → JPEG sob demanda (fallback para clientes
 * que não aceitam WebP). Limitado a 100 entradas e TTL de 5 minutos para evitar
 * consumo excessivo de memória.
 */
@Component
@Slf4j
public class CacheFallbackWebP {

    private static final int MAX_ENTRIES = 100;
    private static final long TTL_MS = 5 * 60 * 1000L; // 5 minutos

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /**
     * Retorna os bytes do cache se disponíveis e não expirados.
     * Caso contrário, executa o supplier, cacheia e retorna.
     */
    public byte[] obter(String chave, Supplier<byte[]> supplier) {
        Entry entry = cache.get(chave);
        if (entry != null && !entry.expirou()) {
            log.debug("Cache hit: {}", chave);
            return entry.bytes;
        }

        log.debug("Cache miss: {}", chave);
        byte[] bytes = supplier.get();
        cache.put(chave, new Entry(bytes, System.currentTimeMillis()));
        limparExpirados();
        return bytes;
    }

    private void limparExpirados() {
        if (cache.size() > MAX_ENTRIES) {
            int removidos = 0;
            for (var it = cache.entrySet().iterator(); it.hasNext(); ) {
                if (it.next().getValue().expirou()) {
                    it.remove();
                    removidos++;
                }
            }
            if (removidos > 0) {
                log.debug("Cache: removidas {} entradas expiradas, restam {}", removidos, cache.size());
            }
        }
    }

    private record Entry(byte[] bytes, long criadoEm) {
        boolean expirou() {
            return System.currentTimeMillis() - criadoEm > TTL_MS;
        }
    }
}
```

- [ ] **Step 2: Criar teste unitário**

```java
package com.domus.api.modules.foto;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CacheFallbackWebPTest {

    private final CacheFallbackWebP cache = new CacheFallbackWebP();

    @Test
    void retornaValorDoSupplierNaPrimeiraChamada() {
        byte[] resultado = cache.obter("chave-1", () -> new byte[]{1, 2, 3});
        assertThat(resultado).containsExactly(1, 2, 3);
    }

    @Test
    void retornaValorDoCacheEmChamadasSubsequentes() {
        AtomicInteger chamadasSupplier = new AtomicInteger(0);

        cache.obter("chave-2", () -> {
            chamadasSupplier.incrementAndGet();
            return new byte[]{1, 2, 3};
        });
        cache.obter("chave-2", () -> {
            chamadasSupplier.incrementAndGet();
            return new byte[]{4, 5, 6};
        });

        assertThat(chamadasSupplier.get()).isEqualTo(1);
    }
}
```

- [ ] **Step 3: Rodar o teste**

```bash
cd backend/api && mvn test -Dtest=CacheFallbackWebPTest
```

Expected: 2 testes passam

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/domus/api/modules/foto/CacheFallbackWebP.java \
        src/test/java/com/domus/api/modules/foto/CacheFallbackWebPTest.java
git commit -m "feat(foto): adicionar CacheFallbackWebP para conversões sob demanda"
```

---

## Task 7: Atualizar FotoController para Accept header e fallback .webp/.jpg

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/modules/foto/FotoController.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/foto/FotoService.java` (adicionar método)

- [ ] **Step 1: Atualizar FotoService com novo método de leitura com fallback**

Adicionar novo método em FotoService.java:

```java
/**
 * Lê a foto tentando .webp primeiro (novo), cai pra .jpg (fotos antigas pré-mudança).
 * Retorna null se nenhum dos dois existir.
 */
public byte[] lerComFallback(UUID id, TamanhoFoto tamanho, UUID igrejaId) {
    Foto foto = fotoRepository.findByIdAndIgrejaId(id, igrejaId)
            .orElseThrow(() -> new ResourceNotFoundException("Foto não encontrada."));

    String chaveWebp = foto.getChave() + "/" + tamanho.sufixo();
    String chaveJpg = foto.getChave() + "/" + tamanho.name().toLowerCase() + ".jpg";

    try {
        return armazenamentoFotos.ler(chaveWebp);
    } catch (Exception e) {
        log.debug("WebP não encontrado, tentando JPEG: {}", chaveWebp);
        try {
            return armazenamentoFotos.ler(chaveJpg);
        } catch (Exception e2) {
            throw new ResourceNotFoundException("Foto não encontrada.");
        }
    }
}
```

- [ ] **Step 2: Atualizar FotoController**

Substituir o endpoint `GET /{id}`:

```java
package com.domus.api.modules.foto;

import com.domus.api.modules.foto.DTOs.FotoResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/fotos")
@RequiredArgsConstructor
public class FotoController {

    private final FotoService fotoService;
    private final UsuarioAutenticado usuarioAutenticado;
    private final CacheFallbackWebP cacheFallbackWebP;

    /** O tenant vem do JWT; não há como pedir a foto de outra igreja. */
    @PostMapping
    public ResponseEntity<FotoResponse> enviar(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fotoService.enviar(arquivo, usuarioAutenticado.getIgrejaId()));
    }

    /**
     * Lê a foto com content negotiation via Accept header.
     * Cliente que aceita WebP recebe WebP; senão, recebe JPEG (convertido sob demanda com cache).
     * Compatível com fotos antigas (.jpg no bucket).
     */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> ler(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "DISPLAY") TamanhoFoto tamanho,
            @RequestHeader(value = "Accept", defaultValue = "*/*") String accept) {

        byte[] bytes = fotoService.lerComFallback(id, tamanho, usuarioAutenticado.getIgrejaId());
        boolean clienteAceitaWebp = accept.contains("image/webp") || accept.contains("*/*");
        boolean bytesSaoWebp = ehWebp(bytes);

        byte[] resposta;
        MediaType contentType;

        if (bytesSaoWebp && !clienteAceitaWebp) {
            // Converter WebP → JPEG sob demanda (com cache)
            String chaveCache = id + "-" + tamanho.name();
            resposta = cacheFallbackWebP.obter(chaveCache, () -> converterWebpParaJpeg(bytes));
            contentType = MediaType.IMAGE_JPEG;
        } else if (bytesSaoWebp) {
            resposta = bytes;
            contentType = MediaType.valueOf("image/webp");
        } else {
            // Foto antiga em JPEG — serve direto independente do Accept
            resposta = bytes;
            contentType = MediaType.IMAGE_JPEG;
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(resposta);
    }

    private boolean ehWebp(byte[] bytes) {
        // Magic bytes do WebP: "RIFF....WEBP" (offsets 0-3, 8-11)
        if (bytes.length < 12) return false;
        return bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private byte[] converterWebpParaJpeg(byte[] webpBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(webpBytes));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Falha ao converter WebP para JPEG", e);
        }
    }
}
```

- [ ] **Step 3: Rodar todos os testes do módulo foto**

```bash
cd backend/api && mvn test -Dtest='*Foto*Test'
```

Expected: todos os testes do módulo foto passam

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/domus/api/modules/foto/FotoController.java \
        src/main/java/com/domus/api/modules/foto/FotoService.java
git commit -m "feat(foto): content negotiation WebP/JPEG com fallback automático

GET /fotos/{id} verifica Accept header:
- Cliente aceita WebP → serve WebP direto
- Cliente não aceita → converte WebP→JPEG sob demanda (cache 5min)
- Foto antiga (.jpg no bucket) → serve JPEG direto (compatibilidade)"
```

---

## Task 8: Frontend - UploadFoto aceitar WebP

**Files:**
- Modify: `frontend/src/components/common/UploadFoto/UploadFoto.tsx`

- [ ] **Step 1: Atualizar TIPOS_ACEITOS**

Linha 12:

ANTES:
```ts
const TIPOS_ACEITOS = ['image/jpeg', 'image/png']
```

DEPOIS:
```ts
const TIPOS_ACEITOS = ['image/jpeg', 'image/png', 'image/webp']
```

- [ ] **Step 2: Atualizar mensagem de erro**

Linha 45:

ANTES:
```ts
notificar.erro('Formato não aceito', 'Envie uma imagem JPEG ou PNG.')
```

DEPOIS:
```ts
notificar.erro('Formato não aceito', 'Envie uma imagem JPEG, PNG ou WebP.')
```

- [ ] **Step 3: Atualizar placeholder**

Linha 154:

ANTES:
```ts
<span className={styles.ajuda}>JPEG ou PNG, até 15 MB</span>
```

DEPOIS:
```ts
<span className={styles.ajuda}>JPEG, PNG ou WebP, até 15 MB</span>
```

- [ ] **Step 4: Commit**

```bash
cd frontend
git add src/components/common/UploadFoto/UploadFoto.tsx
git commit -m "feat(foto): UploadFoto aceita WebP como formato de entrada"
```

---

## Task 9: Frontend - CropperFoto 16:9 + toBlob WebP

**Files:**
- Modify: `frontend/src/components/common/UploadFoto/CropperFoto.tsx`

- [ ] **Step 1: Atualizar aspect ratio para banner**

Linha ~173 (dentro do componente):

ANTES:
```ts
const aspect = formato === 'circulo' ? 1 : 3 / 1
```

DEPOIS:
```ts
const aspect = formato === 'circulo' ? 1 : 16 / 9
```

- [ ] **Step 2: Atualizar SAIDA.banner**

Linha ~20:

ANTES:
```ts
const SAIDA: Record<Props['formato'], { largura: number; altura: number }> = {
  circulo: { largura: 480, altura: 480 },
  banner: { largura: 1200, altura: 400 },
}
```

DEPOIS:
```ts
const SAIDA: Record<Props['formato'], { largura: number; altura: number }> = {
  circulo: { largura: 480, altura: 480 },
  banner: { largura: 1200, altura: 675 },
}
```

- [ ] **Step 3: Atualizar toBlob na função getCroppedImg**

Localizar o `canvas.toBlob(...)` e mudar:

ANTES:
```ts
return new Promise((resolve, reject) => {
  canvas.toBlob(
    (blob) => { ... },
    'image/jpeg',
    0.92,
  )
})
```

DEPOIS:
```ts
return new Promise((resolve, reject) => {
  canvas.toBlob(
    (blob) => { ... },
    'image/webp',
    0.85,
  )
})
```

- [ ] **Step 4: Atualizar nome do arquivo gerado**

Na mesma função, dentro do callback do toBlob:

ANTES:
```ts
const name = imageUrl.replace(/^.*[\\/]/, '').replace(/\.\w+$/, '.jpg') || 'foto.jpg'
resolve(new File([blob], name, { type: 'image/jpeg' }))
```

DEPOIS:
```ts
const name = imageUrl.replace(/^.*[\\/]/, '').replace(/\.\w+$/, '.webp') || 'foto.webp'
resolve(new File([blob], name, { type: 'image/webp' }))
```

- [ ] **Step 5: Build do frontend**

```bash
cd frontend && npm run build
```

Expected: sem erros

- [ ] **Step 6: Commit**

```bash
git add src/components/common/UploadFoto/CropperFoto.tsx
git commit -m "feat(foto): CropperFoto usa aspect 16:9 e saída WebP

- Banner aspect ratio: 3/1 → 16/9
- SAIDA.banner: 1200×400 → 1200×675
- toBlob: image/jpeg 0.92 → image/webp 0.85"
```

---

## Task 10: Frontend - UploadFoto.module.css aspect-ratio 16/9

**Files:**
- Modify: `frontend/src/components/common/UploadFoto/UploadFoto.module.css`

- [ ] **Step 1: Atualizar aspect-ratio**

Localizar `.areaBanner`:

ANTES:
```css
.areaBanner {
  width: 100%;
  max-width: 420px;
  aspect-ratio: 3 / 1;
  border-radius: var(--radius-lg);
}
```

DEPOIS:
```css
.areaBanner {
  width: 100%;
  max-width: 420px;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-lg);
}
```

- [ ] **Step 2: Build**

```bash
cd frontend && npm run build
```

Expected: sem erros

- [ ] **Step 3: Commit**

```bash
git add src/components/common/UploadFoto/UploadFoto.module.css
git commit -m "style(foto): UploadFoto banner usa aspect-ratio 16/9"
```

---

## Task 11: Testes manuais de integração

- [ ] **Step 1: Subir backend e frontend**

```bash
# Terminal 1
cd backend/api && mvn spring-boot:run

# Terminal 2
cd frontend && npm run dev
```

- [ ] **Step 2: Testar upload de WebP**

1. Abrir `/perfil`
2. Clicar "Editar foto"
3. Selecionar um arquivo .webp
4. Verificar que aceita e mostra preview
5. Ajustar recorte e aplicar
6. Verificar que a foto é salva e exibida corretamente

- [ ] **Step 3: Testar banner 16:9**

1. Abrir `/eventos/cadastrar`
2. Selecionar uma imagem para o banner
3. Verificar que o recorte é 16:9 (não 3:1)
4. Salvar e verificar no card da listagem

- [ ] **Step 4: Testar compatibilidade com banners antigos**

1. Abrir listagem de eventos com um evento antigo (banner 3:1)
2. Verificar que aparece corretamente (cortado via object-fit: cover)

- [ ] **Step 5: Testar Accept header (devtools)**

1. Abrir `/perfil` com foto
2. DevTools → Network → filtrar por `/api/fotos/`
3. Verificar que a requisição manda `Accept: image/webp`
4. Verificar que a resposta é `Content-Type: image/webp`
5. Testar com curl sem Accept header:
   ```bash
   curl -H "Cookie: ..." http://localhost:8080/fotos/{id}?tamanho=DISPLAY -v
   ```
   Verificar que retorna `Content-Type: image/jpeg`

- [ ] **Step 6: Commit final (se tudo ok)**

```bash
# Backend
cd backend/api
git push

# Frontend
cd frontend
git push
```

---

## Riscos e Mitigações

**1. Thumbnailator → Graphics2D é mudança grande**
- Mitigação: testes existentes do ProcessadorImagemTest cobrem os 5 cenários críticos
- Plano B: se falhar, reverter Task 2 e manter Thumbnailator + aceitar só JPEG/PNG

**2. metadata-extractor pode não estar no pom.xml**
- Mitigação: verificar com `grep metadata-extractor pom.xml` antes de começar
- Se não estiver, adicionar na Task 2

**3. webp-imageio pode ter comportamento inesperado com certos inputs**
- Mitigação: testar com diferentes tamanhos de imagem (pequena, grande, com EXIF, sem EXIF)
- Fallback: se der erro específico, logar e retornar JPEG

**4. Safari <14 não suporta WebP**
- Mitigação: fallback JPEG automático via Accept header
- Impacto muito baixo (set/2020)

**5. Banners antigos (3:1) podem parecer "cortados"**
- Mitigação: object-fit: cover já corta de forma inteligente
- Usuário pode re-enviar se quiser controle total
