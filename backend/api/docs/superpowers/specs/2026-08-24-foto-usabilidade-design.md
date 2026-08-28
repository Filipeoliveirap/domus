# Usabilidade da feature de foto — design

**Data:** 2026-08-24
**Natureza:** correção + polimento. Não é feature nova; a infra está pronta (ver
`2026-07-22-upload-foto-design.md`).
**Fase do roadmap:** 2 (manutenção/qualidade)
**Escopo:** problemas de UX e bugs de renderização encontrados no uso real após o piloto.

## Problema

A feature de foto funciona — pipeline back→bucket→cache→tela está sólido — mas quatro
problemas reais apareceram no uso:

### A. Foto preta após salvar (perfil, mobile **e** desktop)

Selecionar uma foto de perfil, recortar, aplicar e salvar gera uma imagem **completamente
preta** ao exibir de volta. Ocorre no celular **e** no computador. Em alguns casos é uma
tarja preta no topo, em outros é a foto inteira.

A causa raiz está em `CropperFoto.tsx` → função `getCroppedImg`:

```js
const img = new Image()
img.src = imageUrl
return new Promise((resolve) => {
  img.onload = () => {
    ctx.drawImage(img, ...)
    canvas.toBlob((blob) => { resolve(new File(...)) }, 'image/jpeg', 0.92)
  }
})
```

O `img.onload` dispara quando os **metadados** da imagem carregaram, mas em vários
navegadores os pixels podem não estar totalmente decodificados quando isso acontece.
Quando `drawImage` roda com dados incompletos, ele desenha **nada** no canvas. O canvas
fica transparente. E `canvas.toBlob('image/jpeg', ...)` converte transparência em
**preto** — JPEG não tem canal alpha.

Pesquisa confirmou:
- DEV Community (junho/2026) — *"Canvas drawImage Took Me 3 Days to Get Right"*:
  canvas padrão é transparente; `toBlob('image/jpeg')` sobre canvas vazio gera imagem preta.
- Documentação oficial do `react-easy-crop` recomenda o padrão `createImage()` com Promise +
  `img.decode()` antes do `drawImage`.

Não é só mobile: `drawImage` silenciosamente desenhando nada acontece em qualquer
navegador, em condições de carga. O mobile só torna o problema mais frequente porque a
decodificação é mais lenta.

### B. Preview não aparece ao selecionar foto de perfil

Quando `formato === 'circulo'` (foto de perfil, logo), `selecionarArquivo()` no
`UploadFoto.tsx` abre o cropper imediatamente:

```ts
function selecionarArquivo(arquivo: File) {
  if (!validarArquivo(arquivo)) return
  setArquivoBruto(arquivo)
  if (formato === 'circulo') setRecortando(true)   // ← abre modal fullscreen
}
```

O cropper é um modal fullscreen que cobre a área de upload. A prévia local
(`previaLocal` = blob URL da foto selecionada) **fica renderizada atrás do modal**,
invisível. O usuário nunca vê o que selecionou antes do cropper aparecer.

Para `formato === 'banner'` (evento), o cropper **não** abre sozinho — o usuário vê a
prévia, tem botão "Ajustar recorte" e botão "Usar esta foto". É a UX boa; o círculo
deveria funcionar igual.

### C. Qualidade baixa no banner no formulário de evento

No `UploadFoto.tsx`, a foto atual é sempre carregada via THUMB:

```ts
const urlAtual = urlFoto(valor, 'THUMB')
```

Para formato `banner` (aspect ratio 3:1), o THUMB tem **200×67 pixels**. O container do
banner no formulário de evento tem ~420×140px. O navegador amplia ~2x → imagem borrada.

O modal de visualização (`VisualizadorFoto`) e o banner do drawer (`DrawerDetalheEvento`)
usam `DISPLAY` (1200×400), que é nítido. Daí a comparação: "no formulário fica feio,
quando clico fica bonito".

Por que alguém usou THUMB aqui? Provavelmente por hábito — a `urlFoto` aceita tamanho, e o
THUMB serve para avatar (40px na lista). Para o banner (que já tem 140px de altura no
formulário), THUMB é 5x menor que o necessário.

### D. Foto de evento com qualidade melhor

O limite de upload de 5MB foi pensado para foto de perfil (JPEG de câmera compactado).
Para banner de evento (paisagem, mais detalhe, eventualmente com texto), 5MB é pouco — o
usuário perde qualidade escolhendo fotos menores.

A qualidade JPEG no `ProcessadorImagem` está em 0.85. Para banners com texto e gradientes,
sobe visivelmente para 0.90 com impacto desprezível em tamanho.

## Decisões de escopo

**Não vamos:**

- Reescrever o pipeline de processamento de imagem (Thumbnailator continua).
- Adicionar novos tamanhos no backend (só `display` e `thumb`, sem `xlarge` etc.).
- Suportar WebP no upload (continua JPEG/PNG, como a infra decidiu).
- Mudar a forma de servir fotos (sempre pelo Domus, nunca URL pública).

**Vamos:**

- Trocar o padrão `img.onload` por `img.decode()` com fallback — bug do canvas preto.
- Igualar o fluxo do formato `circulo` ao do `banner` — prévia local aparece, cropper é
  opcional.
- Trocar THUMB por DISPLAY na área do UploadFoto (sempre nítido).
- Aumentar limite de upload para 10MB no front **e** no backend
  (`application.properties` hoje limita a 5MB; ver seção 2).
- Aumentar qualidade JPEG do backend de 0.85 para 0.90.
- Ajustes de mobile no cropper e na área de upload.

## Mudanças detalhadas

### 1. `CropperFoto.tsx` — corrigir `getCroppedImg`

Substituir o padrão atual por:

```ts
function createImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.addEventListener('load', () => resolve(img))
    img.addEventListener('error', (e) => reject(e))
    img.src = url
  })
}

async function getCroppedImg(
  imageUrl: string,
  pixelCrop: Area,
  formato: Props['formato'],
): Promise<File> {
  if (pixelCrop.width <= 0 || pixelCrop.height <= 0) {
    throw new Error('Área de recorte inválida.')
  }

  const img = await createImage(imageUrl)
  // decode() garante que os pixels estão prontos antes do drawImage.
  // Sem isto, drawImage pode desenhar nada → canvas preto (JPEG não tem alpha).
  try { await img.decode() } catch { /* fallback: já carregou */ }

  const { largura, altura } = SAIDA[formato]
  const canvas = document.createElement('canvas')
  canvas.width = largura
  canvas.height = altura
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas não suportado.')

  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(
    img,
    pixelCrop.x, pixelCrop.y, pixelCrop.width, pixelCrop.height,
    0, 0, largura, altura,
  )

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) return reject(new Error('Falha ao gerar imagem recortada.'))
        const name = imageUrl.replace(/^.*[\\/]/, '').replace(/\.\w+$/, '.jpg') || 'foto.jpg'
        resolve(new File([blob], name, { type: 'image/jpeg' }))
      },
      'image/jpeg',
      0.92,
    )
  })
}
```

`confirmar()` precisa virar `async` para propagar erro. Se o `decode()` falhar,
mostrar `notificar.erro` com mensagem útil ("Não foi possível processar a foto").

### 2. `UploadFoto.tsx` — não abrir cropper direto no círculo

Em `selecionarArquivo`, **remover** o `if (formato === 'circulo') setRecortando(true)`.
A prévia local aparece naturalmente porque `arquivoBruto` foi setada — basta não abrir o
cropper automaticamente. O cropper continua acessível pelo botão "Ajustar recorte".

No bloco `.acoes`, a condição `formato === 'banner' && arquivoBruto && !recortando` vira
`arquivoBruto && !recortando` — assim os botões "Ajustar recorte" e "Usar esta foto"
aparecem para os dois formatos quando há arquivo selecionado e o cropper não está aberto.

Trocar `urlFoto(valor, 'THUMB')` por `urlFoto(valor, 'DISPLAY')` na linha 108.

Aumentar `TAMANHO_MAXIMO_BYTES` de 5 para 10 MB.

E em `application.properties` (backend), subir `spring.servlet.multipart.max-file-size`
e `max-request-size` para acompanhar (hoje ambos estão em 5MB/6MB; o request precisa ser
ligeiramente maior que o file para cobrir overhead):

```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=11MB
```

### 3. `ProcessadorImagem.java` — qualidade 0.90

```java
private static final double QUALIDADE_JPEG = 0.90;
```

### 4. Mobile — ajustes de CSS

**`UploadFoto.module.css`**, dentro do bloco `@media (max-width: 767px)`:
- Aumentar `.areaCirculo` de 120px para 140px (área de toque maior no avatar).
- Reduzir padding do `.placeholder` de 12px para 8px (texto cabe melhor no círculo menor).

**`CropperFoto.module.css`**, dentro do mesmo bloco:
- Aumentar `.viewportWrap` de 280px para 320px de altura.
- Aumentar `max-height` do `.modal` de `100vh` para `calc(100vh - env(safe-area-inset-top) - env(safe-area-inset-bottom))` (respeita a área segura do iOS/Android com notch).

## Modelo de dados

Nenhuma mudança. O schema `V1__schema_inicial.sql` continua válido.

## Testes

| Camada | O que provar |
|---|---|
| Front (manual) | Foto de perfil salva aparece corretamente no mobile e desktop |
| Front (manual) | Banner do evento aparece nítido no formulário |
| Front (manual) | Cropper funciona igual nos dois formatos (circulo e banner) |
| Back (`ProcessadorImagemTest`) | JPEG sai com quality 0.90 (atualizado para refletir o novo padrão) |
| Back (`FotoServiceTest`) | Comportamento de processamento inalterado |

Testes de canvas em si não dá pra cobrir com JUnit — fica validado manualmente
(conforme regra do projeto: bug de canvas é da camada do browser, não do back).

## Riscos

**Único risco real:** subir o limite para 10MB. Hoje o `spring.servlet.multipart.max-file-size`
é 5MB — vamos igualar front e back. Se uma foto passar do limite do back após o aumento,
a resposta será 413 com mensagem clara (já existe), não 500.

**Importante:** o teste `recusaImagemAcimaDoLimiteDePixels` em `ProcessadorImagemTest`
tem um comentário dizendo "o limite de 5MB do multipart NAO protege contra isto". Esse
limite mudou — atualizar o comentário para refletir 10MB.

Não há risco de OOM: o `ProcessadorImagem` valida megapixels (50M) antes de
descomprimir, independente do tamanho do arquivo.

## Fora de escopo

- WebP como entrada/saída (decidido fora em `2026-07-22-upload-foto-design.md`).
- Novo tamanho de imagem (ex.: XL para impressão).
- Galeria / múltiplas fotos por entidade.
- Drag-and-drop visual (já funciona tecnicamente — só tem o evento drop, não feedback visual melhor).
- Animação de transição entre prévia e foto salva.
