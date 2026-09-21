# Upload de foto — design

**Data:** 2026-07-22
**Natureza:** infraestrutura. Destrava três features que a esperam.
**Fase do roadmap:** 2

## Problema

Três campos existem no banco e não têm como ser preenchidos:

| Campo | Uso |
|---|---|
| `pessoa.foto` | avatar em listas e detalhe |
| `evento.foto` | banner do evento |
| `igreja.logo_url` | logo no topo e no cadastro |

A tela de cadastro de evento mostra "Adicionar imagem — Em breve" desde a Fase 2. Os avatares
caem em iniciais porque nunca há foto. E os posts da comunidade da igreja, previstos para
depois, vão precisar da mesma coisa.

Fazer três vezes a mesma infraestrutura seria três vezes o mesmo risco.

## A decisão que define o resto: bucket privado, servido pelo Domus

Uma URL pública é **permanente e não autenticada**. Quem tem o link vê a foto para sempre,
inclusive depois de a pessoa ser arquivada.

Essas fotos são de **membros da igreja, incluindo crianças**. Este sistema passou a Fase 2
inteira fechando vazamento de dado de membro — escondeu endereço e observações pastorais de
quem não é admin, tirou telefone de convidado da resposta reduzida, removeu um endpoint que
lia CNPJ de qualquer igreja. Publicar o **rosto** dessas pessoas abriria, pela porta da frente,
uma porta maior que todas as que foram fechadas.

Então:

- **Bucket privado**, novo, separado do bucket de backup (que é write-only por desenho — o CI
  escreve e não lê).
- A tela **nunca fala com o R2**. Pede `GET /fotos/{id}?tamanho=thumb|display`; o Domus valida
  a sessão e a igreja, busca no R2 e devolve os bytes.
- **A CSP não muda.** Hoje `img-src` só permite `'self'`, `data:` e domínios do Google — uma
  URL do R2 seria bloqueada pelo navegador. Servindo pelo próprio domínio, `'self'` cobre.

### O custo dessa escolha, e como ele é pago

Toda imagem passa a bater na API: uma lista com 20 avatares faz 20 requisições que antes iriam
direto ao storage.

Isso é resolvido por **imutabilidade**, não por cache esperto:

- o id de uma foto **nunca é reaproveitado** — trocar a foto gera um id novo;
- portanto a resposta vai com `Cache-Control: public, max-age=31536000, immutable`;
- o navegador busca **uma vez** e nunca revalida.

A lista de 20 avatares custa 20 requisições no primeiro acesso e **zero** nos seguintes. Sem a
imutabilidade, essa arquitetura não se sustentaria.

## Três versões, e o original fica

| Versão | Tamanho | Servida? |
|---|---|---|
| `original` | como veio | **não** — arquivo apenas |
| `display` | 1200px (maior lado) | sim |
| `thumb` | 200px (maior lado) | sim |

**Por que 1200px basta:** todos os usos são tela — avatar, banner, logo, post. Nenhum é
impressão. O autor confirmou não ter caso de alta resolução.

**Por que guardar o original:** 500 fotos de 4 MB custam cerca de **US$ 0,03/mês** no R2.
Descartar economizaria centavos e seria **irreversível**. Custo desprezível de um lado,
perda permanente do outro — a escolha se faz sozinha. E com os posts da comunidade no
horizonte, o original é o que permite gerar um tamanho novo sem pedir a foto de novo a ninguém.

## Validação: o que acontece antes de guardar

Upload de arquivo é vetor clássico de ataque. Na ordem:

1. **Tipo pelo conteúdo, nunca pela extensão.** Um `.jpg` pode ser qualquer coisa; validar por
   nome é confiar em quem envia. Aceitos: JPEG, PNG, WebP.
2. **Limite de 5 MB, verificado em fluxo.** Ler o arquivo inteiro na memória para só então
   decidir que é grande demais é um jeito de derrubar o servidor com uploads.
3. **Redecodificar e regravar a imagem.** É isto que descarta os metadados EXIF — e junto com
   eles a **coordenada de GPS** que o celular grava. Ninguém deveria descobrir onde uma pessoa
   mora pela foto de perfil dela. Como efeito colateral, também neutraliza payload escondido
   em metadado.
4. **Nome aleatório.** O nome enviado nunca vira caminho no storage.

## Ciclo de vida

Sem isto o bucket cresce para sempre e ninguém percebe.

| Evento | O que acontece |
|---|---|
| **Troca de foto** | a anterior é removida na hora — não há motivo para guardar |
| **Foto órfã** (subiu e não salvou o formulário) | removida após **24 h** |
| **Pessoa arquivada** | foto removida após **6 meses** arquivada |
| **Exclusão definitiva** (Fase 3) | foto removida junto, imediatamente |

**Por que a foto NÃO sai no arquivamento imediato:** arquivar é *soft delete*, e a Fase 3
prevê desarquivar. Apagar a foto na hora tornaria o desarquivamento parcial — a pessoa volta
sem rosto, sem recuperação. O caso real é a secretária arquivar a pessoa errada e perceber no
dia seguinte. Os seis meses dão folga para isso e ainda limitam o crescimento.

Na **exclusão definitiva** é o contrário: ela existe pelo direito de eliminação da LGPD, e ali
remover o rosto não é economia — é o que foi pedido.

Os prazos ficam **configuráveis** (`app.fotos.orfa-horas`, `app.fotos.arquivada-meses`): são
números que só o uso real ajusta.

## Modelo de dados

```
foto
  id          UUID PK
  igreja_id   UUID FK NOT NULL   -- isolamento multi-tenant, como toda entidade
  chave       VARCHAR NOT NULL   -- prefixo aleatório no bucket
  tipo        VARCHAR NOT NULL   -- image/jpeg | image/png | image/webp
  bytes       BIGINT NOT NULL    -- do original, para acompanhar consumo
  created_at  TIMESTAMP NOT NULL
```

`pessoa.foto`, `evento.foto` e `igreja.logo_url` deixam de ser `VARCHAR` com uma URL e passam a
ser **FK nulável para `foto.id`**.

Duas razões, e a segunda é a que importa:

1. Guardar URL acoplaria o banco ao provedor de storage. Guardando o id, trocar de provedor um
   dia não toca em dado nenhum.
2. **A FK faz o banco impedir o pior erro possível desta feature.** O job de limpeza decide por
   ausência de referência, e um engano ali apaga a foto de alguém para sempre. Com
   `ON DELETE RESTRICT`, o banco recusa apagar uma foto ainda referenciada — o job passa a ter
   uma segunda linha de defesa que não depende de a consulta dele estar certa.

**Órfã** é uma foto com mais de 24 h que nenhuma das três tabelas referencia — uma consulta só,
rodada por rotina agendada.

## Frontend

Um componente, `<UploadFoto>`, usado nos três lugares: prévia antes de enviar, recorte e
substituição.

O **recorte é obrigatório na foto de pessoa e no logo** (ambos aparecem em formato fixo — círculo
e caixa) e **opcional no banner do evento**, que já é enviado na proporção que será exibida.
Sem recorte, uma foto de perfil retangular vira um círculo cortado no lugar errado — quase sempre
na testa da pessoa.

As telas hoje usam `<img>` com `eslint-disable` porque a URL era "de storage externo". Servindo
pelo próprio domínio, isso pode ser revisto — mas **não nesta spec**: mexer em `next/image`
agora misturaria dois assuntos.

## Riscos

**O maior é a API virar servidora de imagem.** A imutabilidade resolve para o navegador de quem
já visitou, mas o primeiro acesso de cada tela ainda passa por lá. Aceitável no tamanho de uma
igreja; se um dia incomodar, a saída é colocar o Cloudflare na frente com cache de borda — sem
mexer no modelo.

**Segundo: o job de limpeza apaga o que não devia.** Ele decide por ausência de referência, e um
erro ali é perda permanente. Precisa de teste que prove que foto referenciada nunca é
selecionada, e de log de quantas removeu.

**Terceiro: memória.** Redimensionar imagem carrega bitmap descomprimido — uma foto de 4000px
ocupa dezenas de MB em memória mesmo vindo de um arquivo de 4 MB. O limite de entrada precisa
considerar isso, não só o tamanho do arquivo.

## Testes

- Arquivo com extensão `.jpg` e conteúdo de outro tipo é recusado.
- Acima de 5 MB é recusado sem carregar tudo em memória.
- EXIF com GPS não sobrevive ao processamento.
- `GET /fotos/{id}` de outra igreja devolve 404.
- `GET /fotos/{id}` sem sessão devolve 401.
- Trocar a foto remove a anterior do bucket.
- A rotina de órfãs não seleciona foto referenciada por pessoa, evento ou igreja.
- Resposta traz `Cache-Control: immutable`.

## Fora de escopo

Vídeo, múltiplas fotos por entidade (galeria), CDN de borda, e revisão do `next/image`.
