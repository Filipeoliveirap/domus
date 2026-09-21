# WebP e Banner 16:9 — design

**Data:** 2026-08-24
**Natureza:** melhoria de performance e UX
**Fase do roadmap:** 2 (refinamento pós-piloto)
**Spec anterior:** `2026-07-22-upload-foto-design.md`

## Contexto

Após o piloto, identificamos oportunidades de otimização:

1. **WebP como formato de saída**: 25-35% menor que JPEG na mesma qualidade, reduzindo banda e tempo de carregamento
2. **Banner 16:9**: proporção mais moderna e alinhada com padrões de redes sociais (Facebook, Instagram usam 16:9 ou 4:5)
3. **Aceitar WebP como entrada**: celulares Android/iOS modernos salvam fotos em WebP por padrão

## Decisões principais

### 1. WebP: entrada + saída com fallback JPEG

**Abordagem escolhida:** WebP nativo + fallback JPEG sob demanda

```
Upload: aceita JPEG/PNG/WebP → processa → salva display/thumb em WebP
GET /fotos/{id}: verifica Accept header
  - Aceita WebP → serve WebP direto do R2
  - Não aceita → converte WebP→JPEG sob demanda (cache in-memory 5min)
```

**Por que essa abordagem:**
- Economia real de espaço (1 versão WebP por foto, não 2)
- Clientes modernos (99%+ em 2026) pegam WebP
- Clientes antigos (Safari <14, set/2020) funcionam com fallback
- Cache simples em memória, sem infraestrutura nova

**Alternativa considerada (rejeitada):**
Salvar ambas versões (WebP + JPEG) no bucket. Mais simples mas dobro de espaço.

### 2. Proporção 16:9 para banners (novos apenas)

**Mudança:**
- `CropperFoto.tsx`: `aspect` muda de `3/1` para `16/9`
- `UploadFoto.module.css`: `aspect-ratio` muda de `3/1` para `16/9`
- `SAIDA.banner`: `1200×400` → `1200×675` (mantém 1200px de largura)

**Migração:**
- Banners existentes (3:1) continuam funcionando via `object-fit: cover`
- Apenas novos uploads usam 16:9
- Sem necessidade de re-envio ou script de migração

### 3. Manter original no bucket (status quo)

**Decisão:** Manter o arquivo original no R2 (não deletar após processamento)

**Razão:** Custo desprezível (US$ 0.03/mês para 500 fotos de 4MB), flexibilidade para gerar novos tamanhos no futuro sem pedir foto de novo ao usuário.

### 4. Manter resolução 480×480 para perfil

**Decisão:** Não reduzir para 256×256

**Razão:** Objetivo era reduzir banda, não resolução. WebP já resolve isso (30% menor) sem perder qualidade em telas retina.

## Arquitetura

### Pipeline de upload

```
[Upload: JPEG/PNG/WebP até 15MB]
        ↓
[ProcessadorImagem.java]
  1. Valida por conteúdo (magic bytes)
  2. Aplica EXIF rotation, descarta metadados
  3. Gera 3 versões:
     - original (como veio)
     - display.webp (1200px no maior lado, qualidade 0.85)
     - thumb.webp (200px no maior lado, qualidade 0.85)
  4. Envia pro R2
        ↓
[R2 Bucket: fotos/{igreja_id}/{uuid}/]
  - original (JPEG/PNG/WebP)
  - display.webp
  - thumb.webp
        ↓
[GET /fotos/{id}?tamanho=DISPLAY|THUMB]
  - Lê Accept header do cliente
  - Se "image/webp" → lê .webp do R2 e serve
  - Senão → converte .webp → JPEG sob demanda (cache in-memory 5min)
        ↓
[Cliente recebe imagem]
```

### Cache do fallback JPEG

- `ConcurrentHashMap<chave, byte[]>` com expiração de 5 minutos
- LRU simples: máximo 100 entradas (~50MB em memória)
- Se cache cheio, remove o mais antigo

## Backend

### Mudanças específicas

**`pom.xml`**
```xml
<dependency>
    <groupId>org.sejda.imageio</groupId>
    <artifactId>webp-imageio</artifactId>
    <version>0.1.6</version>
</dependency>
```

**`ProcessadorImagem.java`**
- `TIPOS_ACEITOS`: adicionar `"image/webp"`
- Renomear `QUALIDADE_JPEG` para `QUALIDADE_WEBP = 0.85`
- Substituir Thumbnailator por pipeline customizado:
  1. `ImageIO.read()` para ler (agora suporta WebP via `webp-imageio`)
  2. Aplicar EXIF rotation com `AffineTransform`
  3. Redimensionar com `BufferedImage.getScaledInstance()` ou `Graphics2D.drawImage()`
  4. `ImageIO.write(bufferedImage, "webp", outputStream)` para gerar WebP
- Sufixo dos arquivos muda para `.webp`

**Nota técnica:** Thumbnailator não suporta WebP via plugin ImageIO. Precisamos substituir o pipeline de redimensionamento por um customizado usando `Graphics2D` diretamente. A lógica de EXIF rotation já existe (Thumbnailator aplica automaticamente); precisamos reimplementar isso manualmente usando `ExifParser` (ou equivalente) + `AffineTransform`.

**`TamanhoFoto.java`**
- `sufixo()`: retorna `"webp"` em vez de `"jpg"`

**`FotoService.java`**
- Salva com sufixo `.webp` (ex: `fotos/uuid/display.webp`)
- `original` mantém formato original (não é convertido)

**`FotoController.java`**
- Endpoint `GET /fotos/{id}` agora lê o `Accept` header
- Lógica de leitura (compatível com fotos antigas):
  1. Tenta ler `{chave}/{tamanho}.webp` do R2
  2. Se não encontrar, tenta ler `{chave}/{tamanho}.jpg` (foto antiga, pré-mudança)
  3. Se encontrou WebP:
     - Cliente aceita WebP → serve WebP (`Content-Type: image/webp`)
     - Cliente não aceita WebP → converte WebP→JPEG sob demanda (cache 5 min) e serve JPEG
  4. Se encontrou JPEG (foto antiga):
     - Serve JPEG direto (`Content-Type: image/jpeg`), independente do Accept header
- Resposta continua com `Cache-Control: public, max-age=31536000, immutable`

**Novo: `CacheFallbackWebP.java`**
```java
public class CacheFallbackWebP {
    private static final int MAX_ENTRIES = 100;
    private static final long TTL_MS = 5 * 60 * 1000; // 5 minutos
    
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    
    public byte[] obter(String chave, Supplier<byte[]> supplier) {
        Entry entry = cache.get(chave);
        if (entry != null && !entry.expirou()) {
            return entry.bytes;
        }
        byte[] bytes = supplier.get();
        cache.put(chave, new Entry(bytes, System.currentTimeMillis()));
        limparExpirados();
        return bytes;
    }
    
    private void limparExpirados() {
        if (cache.size() > MAX_ENTRIES) {
            cache.entrySet().removeIf(e -> e.getValue().expirou());
        }
    }
    
    private record Entry(byte[] bytes, long criadoEm) {
        boolean expirou() {
            return System.currentTimeMillis() - criadoEm > TTL_MS;
        }
    }
}
```

**Conversão WebP → JPEG (sob demanda):**
```java
private byte[] converterWebpParaJpeg(byte[] webpBytes) {
    BufferedImage img = ImageIO.read(new ByteArrayInputStream(webpBytes));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "jpg", out);  // write usa quality default ~0.75
    return out.toByteArray();
}
```

## Frontend

### Mudanças específicas

**`UploadFoto.tsx`**
- `TIPOS_ACEITOS`: adicionar `"image/webp"`
- Placeholder: "até 15 MB" (já está ok)
- Validação de tipo: aceita `image/webp`

**`CropperFoto.tsx`**
- `aspect`: `formato === 'circulo' ? 1 : 16/9` (era `3/1`)
- `SAIDA.banner`: `{ largura: 1200, altura: 675 }` (era `1200×400`)
- `toBlob('image/jpeg', 0.92)` → `toBlob('image/webp', 0.85)`
- Nome do arquivo: `.jpg` → `.webp`

**`UploadFoto.module.css`**
- `.areaBanner`: `aspect-ratio: 16/9` (era `3/1`)

**Sem mudanças:**
- `urlFoto.ts` (backend decide formato via Accept header)
- `FotoService.ts` (upload continua o mesmo)
- `VisualizadorFoto.tsx` (só usa `<img>`, browser decide)

## Testes

### Backend (`ProcessadorImagemTest.java`)

- WebP é aceito como entrada
- WebP é gerado corretamente (display + thumb)
- Qualidade WebP 0.85 produz arquivo ~30% menor que JPEG 0.90
- EXIF/GPS descartado em WebP

### Backend (`FotoControllerTest.java` — novo)

- `GET /fotos/{id}` com `Accept: image/webp` → recebe WebP
- `GET /fotos/{id}` com `Accept: image/jpeg` → recebe JPEG (fallback)
- Cache do fallback funciona (segunda requisição é rápida)
- Banners existentes (3:1) continuam acessíveis

### Frontend (manual)

- Upload de WebP funciona
- Upload de JPEG/PNG continua funcionando
- Novos banners aparecem em 16:9
- Banners antigos (3:1) aparecem cortados (object-fit: cover)
- Mobile funciona (Safari iOS suporta WebP desde v14, set/2020)

## Riscos

**1. `webp-imageio` pode ter bugs**
- Mitigação: versão 0.1.6 é estável, usada em produção por muitos projetos
- Plano B: se der problema, reverter para JPEG

**2. Safari <14 não suporta WebP**
- Mitigação: fallback JPEG automático via content negotiation
- Impacto: muito baixo (Safari 14 é de set/2020, 99%+ dos usuários já atualizaram)

**3. Cache do fallback pode consumir memória**
- Mitigação: limite de 100 entradas (~50MB), expiração de 5 min
- Monitoramento: logar tamanho do cache a cada 1h

**4. Banners antigos (3:1) podem parecer "cortados"**
- Mitigação: `object-fit: cover` já corta de forma inteligente (centro da imagem)
- Usuário pode re-enviar se quiser controle total

## Fora de escopo

- CDN ou proxy reverso para cache de imagens (Cloudflare na frente)
- Geração de múltiplos tamanhos (só display + thumb)
- Galeria de fotos (múltiplas fotos por evento/pessoa)
- Edição avançada (filtros, ajustes de cor)
- Suporte a formatos exóticos (HEIC, AVIF)

## Migração de dados

**Nenhuma migração necessária:**
- Banners existentes (3:1) continuam funcionando via `object-fit: cover`
- Fotos existentes (JPEG no R2, com sufixo `.jpg`) continuam funcionando:
  - Backend tenta ler `.webp` primeiro, se não encontrar lê `.jpg`
  - Cliente recebe JPEG independente do Accept header (compatibilidade total)
- Apenas novos uploads usam WebP e 16:9

**Opcional (futuro):**
- Job para converter fotos antigas de JPEG para WebP (economia de espaço)
- Não é prioridade, custo-benefício baixo
- Pode ser feito aos poucos (ex: converte as 100 mais recentes por dia)
