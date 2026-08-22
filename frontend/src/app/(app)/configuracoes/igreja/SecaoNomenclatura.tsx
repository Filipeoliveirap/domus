'use client'

import { useState } from 'react'
import { Type, Save, RotateCcw } from 'lucide-react'
import { rotulosService } from '@/services/igreja/rotulos.service'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useAuthStore } from '@/store/authStore'
import { concordar } from '@/lib/rotulos/concordancia'
import type { RotulosCustomizados, Genero, RotulosRequest, BlocoRotuloRequest } from '@/types/igreja/igreja.type'
import baseStyles from '../configuracoes.module.css'
import styles from './SecaoNomenclatura.module.css'

interface BlocoEstado { singular: string; plural: string; genero: Genero }

// Mesmo padrão de useRotulos.ts — "Rede" é o texto real hoje (herdado de rotulosMinisterio.ts).
const PADRAO: Record<'ministerio' | 'congregacao' | 'celula', BlocoEstado> = {
  ministerio: { singular: 'Rede', plural: 'Redes', genero: 'FEMININO' },
  congregacao: { singular: 'Unidade', plural: 'Unidades', genero: 'FEMININO' },
  celula: { singular: 'Célula', plural: 'Células', genero: 'FEMININO' },
}

function estadoInicial(dados: RotulosCustomizados | null | undefined): Record<'ministerio' | 'congregacao' | 'celula', BlocoEstado> {
  return {
    ministerio: dados?.ministerioSingular && dados.ministerioPlural && dados.ministerioGenero
      ? { singular: dados.ministerioSingular, plural: dados.ministerioPlural, genero: dados.ministerioGenero }
      : PADRAO.ministerio,
    congregacao: dados?.congregacaoSingular && dados.congregacaoPlural && dados.congregacaoGenero
      ? { singular: dados.congregacaoSingular, plural: dados.congregacaoPlural, genero: dados.congregacaoGenero }
      : PADRAO.congregacao,
    celula: dados?.celulaSingular && dados.celulaPlural && dados.celulaGenero
      ? { singular: dados.celulaSingular, plural: dados.celulaPlural, genero: dados.celulaGenero }
      : PADRAO.celula,
  }
}

interface Props { rotulosAtuais: RotulosCustomizados | null | undefined }

export function SecaoNomenclatura({ rotulosAtuais }: Props) {
  const [blocos, setBlocos] = useState(() => estadoInicial(rotulosAtuais))
  const [salvando, setSalvando] = useState(false)
  const atualizarUsuarioLogado = useAuthStore((s) => s.atualizarUsuarioLogado)

  function atualizarCampo(modulo: keyof typeof blocos, campo: keyof BlocoEstado, valor: string) {
    setBlocos((atual) => ({ ...atual, [modulo]: { ...atual[modulo], [campo]: valor } }))
  }

  function restaurarPadrao(modulo: keyof typeof blocos) {
    setBlocos((atual) => ({ ...atual, [modulo]: PADRAO[modulo] }))
  }

  async function salvar() {
    setSalvando(true)
    try {
      const paraRequest = (modulo: keyof typeof blocos): BlocoRotuloRequest | null => {
        const b = blocos[modulo]
        const p = PADRAO[modulo]
        const ehPadrao = b.singular === p.singular && b.plural === p.plural && b.genero === p.genero
        return ehPadrao ? { singular: null, plural: null, genero: null } : b
      }

      const request: RotulosRequest = {
        ministerio: paraRequest('ministerio'),
        congregacao: paraRequest('congregacao'),
        celula: paraRequest('celula'),
      }

      const resultado = await rotulosService.atualizar(request)
      atualizarUsuarioLogado({ rotulos: resultado })
      notificar.sucesso('Nomenclatura salva', 'Os novos rótulos já valem em todo o sistema.')
    } catch (erro: unknown) {
      const mensagem =
        (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Tente novamente em alguns instantes.'
      notificar.erro('Não foi possível salvar', mensagem)
    } finally {
      setSalvando(false)
    }
  }

  const modulos: { chave: keyof typeof blocos; titulo: string; ajuda: string }[] = [
    { chave: 'ministerio', titulo: 'Ministério', ajuda: 'Padrão: Rede. Outras sugestões: Departamento, Equipe.' },
    { chave: 'congregacao', titulo: 'Congregação', ajuda: 'Sugestões: Unidade, Filial, Polo.' },
    { chave: 'celula', titulo: 'Célula', ajuda: 'Sugestões: Pequeno Grupo, GC, Grupo de Multiplicação.' },
  ]

  return (
    <section className={baseStyles.cartao}>
      <div className={styles.cabecalho}>
        <Type size={20} aria-hidden="true" />
        <div>
          <h2 className={styles.titulo}>Nomenclatura</h2>
          <p className={styles.subtitulo}>
            Adapte os termos do sistema à linguagem da sua igreja. Vale pra menus,
            botões e listas em toda a aplicação.
          </p>
        </div>
      </div>

      <div className={styles.blocos}>
        {modulos.map(({ chave, titulo, ajuda }) => (
          <div key={chave} className={styles.bloco}>
            <div className={styles.blocoCabecalho}>
              <h3>{titulo}</h3>
              <p>{ajuda}</p>
            </div>
            <div className={styles.blocoGrade}>
              <div className={styles.campo}>
                <label className={styles.rotulo} htmlFor={`${chave}-singular`}>Singular</label>
                <input
                  id={`${chave}-singular`}
                  className={styles.input}
                  placeholder={`Ex.: ${PADRAO[chave].singular}`}
                  value={blocos[chave].singular}
                  onChange={(e) => atualizarCampo(chave, 'singular', e.target.value)}
                />
              </div>
              <div className={styles.campo}>
                <label className={styles.rotulo} htmlFor={`${chave}-plural`}>Plural</label>
                <input
                  id={`${chave}-plural`}
                  className={styles.input}
                  placeholder={`Ex.: ${PADRAO[chave].plural}`}
                  value={blocos[chave].plural}
                  onChange={(e) => atualizarCampo(chave, 'plural', e.target.value)}
                />
              </div>
              <div className={styles.campo}>
                <span className={styles.rotulo}>Se diz &quot;o&quot; ou &quot;a&quot; antes desse termo?</span>
                <div className={styles.segmentado}>
                  <button
                    type="button"
                    className={`${styles.segmentoBtn} ${blocos[chave].genero === 'MASCULINO' ? styles.segmentoAtivo : ''}`}
                    onClick={() => atualizarCampo(chave, 'genero', 'MASCULINO')}
                  >
                    &quot;O&quot; (masculino)
                  </button>
                  <button
                    type="button"
                    className={`${styles.segmentoBtn} ${blocos[chave].genero === 'FEMININO' ? styles.segmentoAtivo : ''}`}
                    onClick={() => atualizarCampo(chave, 'genero', 'FEMININO')}
                  >
                    &quot;A&quot; (feminino)
                  </button>
                </div>
              </div>
            </div>
            <div className={styles.preview}>
              <span className={styles.previewRotulo}>Vai aparecer assim, por exemplo:</span>
              <span className={styles.previewValor}>
                {concordar(blocos[chave].genero, 'novo')} {blocos[chave].singular || PADRAO[chave].singular}
              </span>
              <span className={styles.previewValor}>{blocos[chave].plural || PADRAO[chave].plural}</span>
              <button type="button" className={styles.botaoRestaurar} onClick={() => restaurarPadrao(chave)}>
                <RotateCcw size={14} aria-hidden="true" />
                Restaurar padrão
              </button>
            </div>
          </div>
        ))}
      </div>

      <div className={styles.acoes}>
        <button type="button" className={baseStyles.botaoPrimario} onClick={salvar} disabled={salvando}>
          <Save size={16} aria-hidden="true" />
          {salvando ? 'Salvando...' : 'Salvar nomenclatura'}
        </button>
      </div>
    </section>
  )
}
