# Usabilidade da feature de foto Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corrigir quatro problemas de UX/bug da feature de foto: foto preta após
salvar (perfil), preview não aparece ao selecionar foto de perfil, qualidade baixa no
banner do evento no formulário, e permitir fotos maiores/melhores para banners.

**Architecture:** Mudanças cirúrgicas nos componentes e no pipeline de processamento de
imagem. Nenhuma nova entidade, migration ou endpoint. Reescrita da função de recorte
do canvas no front com `createImage` + `img.decode()` (padrão recomendado pelo
`react-easy-crop`). Backend ganha qualidade JPEG de 0.90 (era 0.85) e multipart de 10MB
(era 5MB). Front mostra prévia local do círculo igual ao banner, e usa DISPLAY no lugar
de THUMB.

**Tech Stack:** Java 21 / Spring Boot (backend); Next.js / TypeScript / CSS Modules
(frontend); Thumbnailator (processamento de imagem); react-easy-crop (recorte).

**Spec:** `backend/api/docs/superpowers/specs/2026-08-24-foto-usabilidade-design.md`

## Global Constraints

- Não commitar antes de o autor testar (regra do projeto). Cada task termina com
  "**esperar o autor testar**" quando envolve mudança visível.
- Cada task de código termina com `mvn -q test -Dtest=...` (backend) ou
  `npm run build` (frontend) quando aplicável — mas o commit só vem depois do teste
  manual do autor em mudanças visuais (foto de perfil/banner).
- Frontend: componente único `<UploadFoto>` é usado em 7 lugares; mudanças afetam todos.
- Backend: `ProcessadorImagem` é reusado por toda feature de foto; mudança de qualidade
  JPEG afeta TODA foto (perfil, evento, logo, ministério, célula).
- Nenhuma migration nova. Nenhum endpoint novo. Nenhuma entidade nova.

---

## File Structure

**Backend:**
```
src/main/java/com/domus/api/modules/foto/ProcessadorImagem.java   (modifica — quality)
src/main/resources/application.properties                          (modifica — multipart)
src/test/java/com/domus/api/modules/foto/ProcessadorImagemTest.java (modifica — comentário)
```

**Frontend:**
```
src/components/common/UploadFoto/UploadFoto.tsx             (modifica — fluxo + DISPLAY + 10MB)
src/components/common/UploadFoto/UploadFoto.module.css     (modifica — mobile)
src/components/common/UploadFoto/CropperFoto.tsx           (modifica — getCroppedImg)
src/components/common/UploadFoto/CropperFoto.module.css    (modifica — mobile)
```

---

## Task 1: Backend — atualizar qualidade JPEG do ProcessadorImagem

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/modules/foto/ProcessadorImagem.java:28`

- [ ] **Step 1: Trocar a constante de 0.85 para 0.90**

Em `ProcessadorImagem.java`, linha 28:

ANTES:
```java
private static final double QUALIDADE_JPEG = 0.85;
```

DEPOIS:
```java
private static final double QUALIDADE_JPEG = 0.90;
```

- [ ] **Step 2: Rodar a suíte do ProcessadorImagem para garantir que nada quebrou**

Run:
```bash
cd backend/api && mvn -q test -Dtest=ProcessadorImagemTest
```

Expected: todos os 5 testes passam (`geraDisplayEThumbNosTamanhosCertos`,
`naoAumentaImagemMenorQueOAlvo`, `recusaArquivoQueNaoEImagem`,
`recusaImagemAcimaDoLimiteDePixels`, `aplicaOrientacaoDoExifEDescartaOsMetadados`).

- [ ] **Step 3: NÃO COMMITA AINDA**

Mudança ainda depende das outras tasks (test + aplicação.properties). Espere a Task 3
para commitar tudo junto.

---

## Task 2: Backend — subir limite do multipart para 10MB

**Files:**
- Modify: `backend/api/src/main/resources/application.properties:9-11`

- [ ] **Step 1: Atualizar as propriedades do multipart**

Em `application.properties`:

ANTES:
```
# O multipart do Spring recusa ANTES de ler o arquivo inteiro — é a primeira barreira.
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=6MB
```

DEPOIS:
```
# O multipart do Spring recusa ANTES de ler o arquivo inteiro — é a primeira barreira.
# 10MB no file / 11MB no request: permite banners de evento maiores (fotos de perfil
# raramente passam de 3MB). Limite de pixels (50MP) é o que protege contra OOM.
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=11MB
```

- [ ] **Step 2: NÃO COMMITA AINDA**

Espere a Task 3.

---

## Task 3: Backend — atualizar comentário no teste sobre o limite do multipart

**Files:**
- Modify: `backend/api/src/test/java/com/domus/api/modules/foto/ProcessadorImagemTest.java:58-59`

- [ ] **Step 1: Trocar "5MB" por "10MB" no comentário do teste**

Em `ProcessadorImagemTest.java`, dentro do método `recusaImagemAcimaDoLimiteDePixels`:

ANTES:
```java
        // Bomba de descompressao: o limite de 5MB do multipart NAO protege contra isto —
        // o perigo nao e o tamanho do arquivo, e o do bitmap depois de decodificado.
```

DEPOIS:
```java
        // Bomba de descompressao: o limite de 10MB do multipart NAO protege contra isto —
        // o perigo nao e o tamanho do arquivo, e o do bitmap depois de decodificado.
```

- [ ] **Step 2: Rodar a suíte do ProcessadorImagem de novo**

Run:
```bash
cd backend/api && mvn -q test -Dtest=ProcessadorImagemTest
```

Expected: todos os 5 testes passam.

- [ ] **Step 3: Rodar a suíte inteira do módulo foto pra garantir nada quebrou**

Run:
```bash
cd backend/api && mvn -q test -Dtest='com.domus.api.modules.foto.*'
```

Expected: `ProcessadorImagemTest`, `FotoServiceTest`, `LimpezaFotosJobTest`,
`PessoaRepositoryDesvincularFotoTest` passam.

- [ ] **Step 4: Commitar as mudanças do backend (Tasks 1+2+3)**

```bash
cd backend/api && git add src/main/java/com/domus/api/modules/foto/ProcessadorImagem.java \
                       src/main/resources/application.properties \
                       src/test/java/com/domus/api/modules/foto/ProcessadorImagemTest.java
git commit -m "feat(foto): qualidade JPEG 0.90 e multipart 10MB"
```

- [ ] **Step 5: ESPERAR O AUTOR TESTAR**

Esta mudança afeta toda foto processada (perfil, evento, logo, ministério, célula). O
autor precisa conferir:
- Foto de perfil continua com qualidade aceitável (não regrediu).
- Foto de evento permite enviar arquivos até 10MB.
- Não há erro 413 em uploads de 5–10MB que antes passavam (porque o back também era 5MB).
- Não há erro 413 em uploads de < 10MB que funcionavam antes.

NÃO CONTINUE sem confirmação.

---

## Task 4: Frontend — reescrever `getCroppedImg` com `createImage` + `decode`

**Files:**
- Modify: `frontend/src/components/common/UploadFoto/CropperFoto.tsx:22-51`

- [ ] **Step 1: Substituir a função `getCroppedImg` inteira**

Em `CropperFoto.tsx`, substituir o bloco completo da função `getCroppedImg` (linhas 22-51):

ANTES (linhas 22-51):
```ts
function getCroppedImg(imageUrl: string, pixelCrop: Area, formato: Props['formato']): Promise<File> {
  const canvas = document.createElement('canvas')
  const { largura, altura } = SAIDA[formato]
  canvas.width = largura
  canvas.height = altura
  const ctx = canvas.getContext('2d')
  if (!ctx) return Promise.reject(new Error('Canvas not supported'))

  const img = new Image()
  img.src = imageUrl

  return new Promise((resolve) => {
    img.onload = () => {
      ctx.drawImage(
        img,
        pixelCrop.x, pixelCrop.y, pixelCrop.width, pixelCrop.height,
        0, 0, largura, altura,
      )
      canvas.toBlob(
        (blob) => {
          if (!blob) return
          const name = imageUrl.replace(/^.*[\\/]/, '').replace(/\.\w+$/, '.jpg') || 'foto.jpg'
          resolve(new File([blob], name, { type: 'image/jpeg' }))
        },
        'image/jpeg',
        0.92,
      )
    }
  })
}
```

DEPOIS:
```ts
function createImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.addEventListener('load', () => resolve(img))
    img.addEventListener('error', (e) => reject(e instanceof ErrorEvent ? e.error ?? new Error(e.message) : new Error('Falha ao carregar imagem')))
    img.src = url
  })
}

async function getCroppedImg(imageUrl: string, pixelCrop: Area, formato: Props['formato']): Promise<File> {
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
        if (!blob) {
          reject(new Error('Falha ao gerar imagem recortada.'))
          return
        }
        const name = imageUrl.replace(/^.*[\\/]/, '').replace(/\.\w+$/, '.jpg') || 'foto.jpg'
        resolve(new File([blob], name, { type: 'image/jpeg' }))
      },
      'image/jpeg',
      0.92,
    )
  })
}
```

- [ ] **Step 2: Atualizar `confirmar()` para propagar erros**

Em `CropperFoto.tsx`, a função `confirmar()` (linhas 68-73):

ANTES:
```ts
  async function confirmar() {
    if (!croppedAreaPixels) return
    setGerando(true)
    const recortado = await getCroppedImg(urlRef.current, croppedAreaPixels, formato)
    onConfirmar(recortado)
  }
```

DEPOIS:
```ts
  async function confirmar() {
    if (!croppedAreaPixels) return
    setGerando(true)
    try {
      const recortado = await getCroppedImg(urlRef.current, croppedAreaPixels, formato)
      onConfirmar(recortado)
    } catch (err) {
      setGerando(false)
      const mensagem = err instanceof Error ? err.message : 'Erro desconhecido.'
      notificar.erro('Não foi possível processar a foto', mensagem)
    }
  }
```

Isso exige adicionar `import { notificar } from '@/components/common/Notificacao/notificar'`
no topo do arquivo (junto com os outros imports).

- [ ] **Step 3: Verificar que o build do frontend compila**

Run:
```bash
cd frontend && npm run build
```

Expected: build sem erros. Avisos de lint são tolerados se não forem do arquivo
modificado.

- [ ] **Step 4: NÃO COMMITA AINDA**

Espere a Task 5.

---

## Task 5: Frontend — UploadFoto mostra prévia no círculo, usa DISPLAY e aceita 10MB

**Files:**
- Modify: `frontend/src/components/common/UploadFoto/UploadFoto.tsx:13, 56-60, 108, 172-182`

- [ ] **Step 1: Subir o limite de upload para 10MB**

Em `UploadFoto.tsx`, linha 13:

ANTES:
```ts
const TAMANHO_MAXIMO_BYTES = 5 * 1024 * 1024
```

DEPOIS:
```ts
const TAMANHO_MAXIMO_BYTES = 10 * 1024 * 1024
```

E a mensagem de erro no `validarArquivo` (linha 50):

ANTES:
```ts
      notificar.erro('Imagem grande demais', 'O tamanho máximo é 5 MB.')
```

DEPOIS:
```ts
      notificar.erro('Imagem grande demais', 'O tamanho máximo é 10 MB.')
```

- [ ] **Step 2: Não abrir o cropper automaticamente no círculo**

Em `UploadFoto.tsx`, função `selecionarArquivo` (linhas 56-60):

ANTES:
```ts
function selecionarArquivo(arquivo: File) {
  if (!validarArquivo(arquivo)) return
  setArquivoBruto(arquivo)
  if (formato === 'circulo') setRecortando(true)
}
```

DEPOIS:
```ts
function selecionarArquivo(arquivo: File) {
  if (!validarArquivo(arquivo)) return
  setArquivoBruto(arquivo)
}
```

- [ ] **Step 3: Trocar THUMB por DISPLAY na foto atual**

Em `UploadFoto.tsx`, linha 108:

ANTES:
```ts
const urlAtual = urlFoto(valor, 'THUMB')
```

DEPOIS:
```ts
const urlAtual = urlFoto(valor, 'DISPLAY')
```

- [ ] **Step 4: Mostrar botões de "Ajustar recorte" e "Usar esta foto" nos dois formatos**

Em `UploadFoto.tsx`, no bloco `.acoes` (linhas 172-182):

ANTES:
```tsx
          {formato === 'banner' && arquivoBruto && !recortando && (
            <>
              <button type="button" className={styles.botaoSecundario} onClick={() => setRecortando(true)}>
                <Crop size={14} aria-hidden="true" />
                Ajustar recorte
              </button>
              <button type="button" className={styles.botaoPrimario} onClick={() => enviar(arquivoBruto)}>
                Usar esta foto
              </button>
            </>
          )}
```

DEPOIS:
```tsx
          {arquivoBruto && !recortando && (
            <>
              <button type="button" className={styles.botaoSecundario} onClick={() => setRecortando(true)}>
                <Crop size={14} aria-hidden="true" />
                Ajustar recorte
              </button>
              <button type="button" className={styles.botaoPrimario} onClick={() => enviar(arquivoBruto)}>
                Usar esta foto
              </button>
            </>
          )}
```

- [ ] **Step 5: Verificar que o build do frontend compila**

Run:
```bash
cd frontend && npm run build
```

Expected: build sem erros.

- [ ] **Step 6: NÃO COMMITA AINDA**

Espere a Task 6.

---

## Task 6: Frontend — ajustes de mobile (CSS)

**Files:**
- Modify: `frontend/src/components/common/UploadFoto/UploadFoto.module.css:213-217`
- Modify: `frontend/src/components/common/UploadFoto/CropperFoto.module.css:220-227`

- [ ] **Step 1: Aumentar a área de toque do avatar no mobile (UploadFoto.module.css)**

Em `UploadFoto.module.css`, no bloco `@media (max-width: 767px)` (linhas 213-217):

ANTES:
```css
@media (max-width: 767px) {
  .areaBanner { max-width: 100%; }
  .acoes { flex-direction: column; }
  .botaoPrimario, .botaoSecundario, .botaoEditar, .botaoRemover { width: 100%; justify-content: center; }
}
```

DEPOIS:
```css
@media (max-width: 767px) {
  .areaCirculo { width: 140px; height: 140px; }
  .areaBanner { max-width: 100%; }
  .placeholder { padding: 8px; }
  .acoes { flex-direction: column; }
  .botaoPrimario, .botaoSecundario, .botaoEditar, .botaoRemover { width: 100%; justify-content: center; }
}
```

- [ ] **Step 2: Aumentar a viewport do cropper e respeitar safe-area no mobile (CropperFoto.module.css)**

Em `CropperFoto.module.css`, no bloco `@media (max-width: 767px)` (linhas 220-227):

ANTES:
```css
@media (max-width: 767px) {
  .modal { padding: 16px; max-width: 100vw; }
  .corpo { flex-direction: column; }
  .viewportWrap { height: 280px; }
  .previewCol { flex-direction: row; }
  .rodape { flex-direction: column-reverse; }
  .btnCancelar, .btnConfirmar { width: 100%; justify-content: center; }
}
```

DEPOIS:
```css
@media (max-width: 767px) {
  .modal {
    padding: 16px;
    max-width: 100vw;
    max-height: calc(100vh - env(safe-area-inset-top) - env(safe-area-inset-bottom));
  }
  .corpo { flex-direction: column; }
  .viewportWrap { height: 320px; }
  .previewCol { flex-direction: row; }
  .rodape { flex-direction: column-reverse; }
  .btnCancelar, .btnConfirmar { width: 100%; justify-content: center; }
}
```

- [ ] **Step 3: Verificar que o build do frontend compila**

Run:
```bash
cd frontend && npm run build
```

Expected: build sem erros.

- [ ] **Step 4: Commitar as mudanças do frontend (Tasks 4+5+6)**

```bash
cd frontend && git add src/components/common/UploadFoto/
cd .. && git add frontend/src/components/common/UploadFoto/
git commit -m "feat(foto): corrigir canvas preto, prévia no círculo, DISPLAY, mobile"
```

- [ ] **Step 5: ESPERAR O AUTOR TESTAR**

Esta é a parte **visual** da feature. O autor precisa conferir:

- **Foto preta (perfil):**
  - Selecionar foto de perfil, recortar, aplicar, salvar → conferir que aparece normalmente.
  - Repetir 2-3 vezes com fotos diferentes pra garantir que não é coincidência.
  - Testar em mobile e desktop (a causa era cross-platform).

- **Preview no círculo:**
  - Selecionar foto de perfil → deve aparecer a prévia **antes** do cropper.
  - Clicar em "Ajustar recorte" → cropper abre.
  - Clicar em "Usar esta foto" → envia direto, sem recorte.

- **Qualidade no banner (evento):**
  - Abrir edição de evento → conferir que a foto do banner aparece nítida no formulário.
  - Comparar com a qualidade do modal (já era nítido antes).
  - Testar com foto pequena (200px) vs. foto grande (3000px).

- **Mobile:**
  - Selecionar foto no celular → a área do avatar é maior (140px).
  - Cropper no celular → a viewport é maior (320px) e respeita o notch.
  - Texto do placeholder cabe sem cortar.

NÃO CONTINUE sem confirmação.

---

## Self-Review

**1. Spec coverage:**
- A. Foto preta após salvar → Task 4 (reescrita do `getCroppedImg` com `decode()`). ✓
- B. Preview não aparece no upload (perfil) → Task 5 step 2 (não abre cropper) + Task 5 step 4 (botões nos dois formatos). ✓
- C. Qualidade baixa no banner no formulário → Task 5 step 3 (THUMB → DISPLAY). ✓
- D. Foto de evento com qualidade melhor → Tasks 1+2 (qualidade 0.90, multipart 10MB) + Task 5 step 1 (10MB no front). ✓
- E. Mobile — área do avatar maior → Task 6 step 1. ✓
- E. Mobile — cropper maior + safe area → Task 6 step 2. ✓

**2. Placeholder scan:** Nenhum "TBD", "TODO", "implement later". Todo step tem código concreto ou comando específico.

**3. Type consistency:**
- `confirmar()` virou async com try/catch — coerente com `getCroppedImg` agora async.
- `notificar` adicionado no import — coerente com uso em `validarArquivo`.
- `createImage` é helper local — não exportado, não conflita.

---

## Execution Handoff

**Plan complete and saved to `backend/api/docs/superpowers/plans/2026-08-24-foto-usabilidade.md`.**

Dois caminhos de execução:

1. **Subagent-Driven (recomendado)** — eu disparo um subagent por task, revejo entre
   tasks, itero rápido.

2. **Inline Execution** — eu executo as tasks nesta sessão, com checkpoints pra você
   revisar.

Qual?
