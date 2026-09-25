'use client'

import { useEffect, useRef, useState } from 'react'
import { X, Image as ImageIcon, Globe } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useCriarPostagem } from '@/hooks/postagem/useCriarPostagem'
import { useVinculoStatus } from '@/hooks/igreja/useVinculo'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { SelectMenu } from '@/components/common/SelectMenu/SelectMenu'
import { SeletorEPreviaFoto, type SeletorEPreviaFotoRef } from '@/components/common/UploadFoto/SeletorEPreviaFoto'
import type { TipoPostagem } from '@/types/postagem.type'
import styles from './ModalNovoAviso.module.css'

const OPCOES_TIPO = [
  { value: 'MURAL_AVISO', label: 'Comunicado Pastoral / Aviso Geral' },
  { value: 'RESUMO_CULTO', label: 'Escala de Voluntários' },
  { value: 'GERAL', label: 'Ação Social / Geral' },
]

interface Props {
  aoFechar: () => void
  aoCriar?: () => void
}

export function ModalNovoAviso({ aoFechar, aoCriar }: Props) {
  const { saindo, fechar } = useFecharAnimado(aoFechar, 220)
  const { data: vinculoStatus } = useVinculoStatus()
  const temFamilia = vinculoStatus != null && vinculoStatus.estado !== 'INDEPENDENTE'
  const { congregacao, concordar } = useRotulos()

  const criarPostagem = useCriarPostagem()
  const [titulo, setTitulo] = useState('')
  const [conteudo, setConteudo] = useState('')
  const [tipo, setTipo] = useState<TipoPostagem>('MURAL_AVISO')
  const [fotoId, setFotoId] = useState<string | null>(null)
  const [compartilharRede, setCompartilharRede] = useState(false)

  const seletorFotoRef = useRef<SeletorEPreviaFotoRef>(null)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar])

  const podeSalvar = Boolean(conteudo.trim() || fotoId)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!podeSalvar || criarPostagem.isPending) return

    criarPostagem.mutate(
      {
        tipo,
        oficial: true,
        titulo: titulo.trim() || undefined,
        conteudo: conteudo.trim(),
        fotoId: fotoId ?? undefined,
        fixado: false,
        restritoPropriaIgreja: temFamilia ? !compartilharRede : true,
      },
      {
        onSuccess: () => {
          aoCriar?.()
          fechar()
        },
      },
    )
  }

  const textoRotuloRede = `${concordar(congregacao.genero, 'os_min')} demais ${congregacao.plural.toLowerCase()}`

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={fechar}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <span className={styles.grabber} aria-hidden="true" />
        <div className={styles.modalHeader}>
          <h2 className={styles.modalTitulo}>Publicar Aviso no Mural</h2>
          <button type="button" className={styles.modalFechar} onClick={fechar} aria-label="Fechar">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className={styles.modalCorpo}>
            <div className={styles.campo}>
              <label className={styles.label}>TIPO DO AVISO</label>
              <SelectMenu
                value={tipo}
                options={OPCOES_TIPO}
                onChange={(v) => setTipo(v as TipoPostagem)}
                placeholder=""
              />
            </div>

            <div className={styles.campo}>
              <label className={styles.label} htmlFor="titulo-aviso">
                Título do Aviso
              </label>
              <input
                id="titulo-aviso"
                type="text"
                className={styles.input}
                placeholder="Ex: Culto de Celebração & Santa Ceia"
                value={titulo}
                onChange={(e) => setTitulo(e.target.value)}
              />
            </div>

            <div className={styles.campo}>
              <label className={styles.label} htmlFor="conteudo-aviso">
                Conteúdo do Comunicado
              </label>
              <textarea
                id="conteudo-aviso"
                className={styles.textarea}
                placeholder="Digite as informações importantes do aviso..."
                value={conteudo}
                onChange={(e) => setConteudo(e.target.value)}
              />
            </div>

            <SeletorEPreviaFoto
              ref={seletorFotoRef}
              fotoId={fotoId}
              onChange={setFotoId}
              formato="post"
            />

            {temFamilia && (
              <div className={`${styles.opcaoRedeCard} ${compartilharRede ? styles.opcaoRedeCardAtivo : ''}`}>
                <div className={styles.opcaoRedeInfo}>
                  <div className={styles.opcaoRedeIconeWrap}>
                    <Globe size={18} className={styles.opcaoRedeIcone} />
                  </div>
                  <div className={styles.opcaoRedeTexto}>
                    <span className={styles.opcaoRedeTitulo}>
                      Compartilhar {concordar(congregacao.genero, 'com_os_demais')} {congregacao.plural.toLowerCase()}
                    </span>
                    <span className={styles.opcaoRedeSubtitulo}>
                      {compartilharRede
                        ? `Visível para ${concordar(congregacao.genero, 'os_min')} ${congregacao.plural.toLowerCase()} do seu grupo`
                        : `Visível apenas para ${concordar(congregacao.genero, 'seu')} ${congregacao.singular.toLowerCase()}`}
                    </span>
                  </div>
                </div>
                <label className={styles.switch}>
                  <input
                    type="checkbox"
                    className={styles.switchInput}
                    checked={compartilharRede}
                    onChange={(e) => setCompartilharRede(e.target.checked)}
                  />
                  <span className={styles.switchTrilho} />
                </label>
              </div>
            )}
          </div>

          <div className={styles.modalRodape}>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <div className={styles.tooltipWrap}>
                <button
                  type="button"
                  className={styles.btnAcaoIcone}
                  onClick={() => seletorFotoRef.current?.abrirSeletor()}
                  aria-label="Adicionar imagem ao aviso"
                >
                  <ImageIcon size={18} />
                </button>
                <div className={styles.tooltipBox} role="tooltip">
                  Adicionar imagem ao aviso
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="button" className={styles.btnCancelar} onClick={fechar} disabled={criarPostagem.isPending}>
                Cancelar
              </button>
              <button
                type="submit"
                className={styles.btnSalvar}
                disabled={criarPostagem.isPending || !podeSalvar}
              >
                {criarPostagem.isPending ? 'Publicando...' : 'Publicar no Mural'}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  )
}
