'use client'

import { useEffect, useRef, useState } from 'react'
import { X, Image as ImageIcon, Globe, Check } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useAtualizarPostagem } from '@/hooks/postagem/useAtualizarPostagem'
import { useVinculoStatus } from '@/hooks/igreja/useVinculo'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { useArrastarParaRolar } from '@/hooks/useArrastarParaRolar'
import { SeletorEPreviaFoto, type SeletorEPreviaFotoRef } from '@/components/common/UploadFoto/SeletorEPreviaFoto'
import { notificar } from '@/components/common/Notificacao/notificar'
import { SelectMenu } from '@/components/common/SelectMenu/SelectMenu'
import type { Postagem, TipoPostagem } from '@/types/postagem.type'
import styles from './ModalEditarPostagem.module.css'

const TAGS_CATEGORIA: { label: string; valor: TipoPostagem }[] = [
  { label: 'Devocional', valor: 'DEVOCIONAL' },
  { label: 'Resumo do Culto', valor: 'RESUMO_CULTO' },
  { label: 'Pedido de Oração', valor: 'PEDIDO_ORACAO' },
  { label: 'Testemunho', valor: 'TESTEMUNHO' },
  { label: 'Geral', valor: 'GERAL' },
]

const OPCOES_TIPO_AVISO = [
  { value: 'MURAL_AVISO', label: 'Comunicado Pastoral / Aviso Geral' },
  { value: 'RESUMO_CULTO', label: 'Escala de Voluntários' },
  { value: 'GERAL', label: 'Ação Social / Geral' },
]

interface Props {
  postagem: Postagem
  aoFechar: () => void
  onEditarSuccess?: (id: string) => void
}

export function ModalEditarPostagem({ postagem, aoFechar, onEditarSuccess }: Props) {
  const { saindo, fechar } = useFecharAnimado(aoFechar, 220)
  const { data: vinculoStatus } = useVinculoStatus()
  const temFamilia = vinculoStatus != null && vinculoStatus.estado !== 'INDEPENDENTE'
  const { congregacao, concordar } = useRotulos()

  const atualizarPost = useAtualizarPostagem()

  const [titulo, setTitulo] = useState(postagem.titulo ?? '')
  const [conteudo, setConteudo] = useState(postagem.conteudo ?? '')
  const [tipo, setTipo] = useState<TipoPostagem>(postagem.tipo)
  const [fotoId, setFotoId] = useState<string | null>(postagem.fotoId ?? null)
  const [compartilharRede, setCompartilharRede] = useState<boolean>(!postagem.restritoPropriaIgreja)

  const seletorFotoRef = useRef<SeletorEPreviaFotoRef>(null)
  const { ref: refSeletorTags, propsArrasto: propsArrastoTags } = useArrastarParaRolar<HTMLDivElement>()

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !atualizarPost.isPending) fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar, atualizarPost.isPending])

  const ehAvisoOficial = postagem.oficial || postagem.tipo === 'MURAL_AVISO' || Boolean(postagem.titulo)
  const podeSalvar = Boolean(conteudo.trim() || fotoId)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!podeSalvar) return

    atualizarPost.mutate(
      {
        postagemId: postagem.id,
        payload: {
          tipo,
          titulo: ehAvisoOficial ? (titulo.trim() || undefined) : postagem.titulo,
          conteudo: conteudo.trim(),
          fotoId: fotoId ?? null,
          restritoPropriaIgreja: temFamilia ? !compartilharRede : postagem.restritoPropriaIgreja,
        },
      },
      {
        onSuccess: () => {
          onEditarSuccess?.(postagem.id)
          fechar()
        },
        onError: () => {
          notificar.erro('Não foi possível salvar', 'Tente novamente.')
        },
      },
    )
  }

  const textoRotuloRede = `${concordar(congregacao.genero, 'os_min')} demais ${congregacao.plural.toLowerCase()}`

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={() => !atualizarPost.isPending && fechar()}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className={styles.modalHeader}>
          <h2 className={styles.modalTitulo}>{ehAvisoOficial ? 'Editar Aviso Oficial' : 'Editar Postagem'}</h2>
          <button type="button" className={styles.modalFechar} onClick={fechar} aria-label="Fechar">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className={styles.modalCorpo}>
            {ehAvisoOficial && (
              <>
                <div className={styles.campo}>
                  <label className={styles.label}>TIPO DO AVISO</label>
                  <SelectMenu
                    value={tipo}
                    options={OPCOES_TIPO_AVISO}
                    onChange={(v) => setTipo(v as TipoPostagem)}
                    placeholder=""
                  />
                </div>

                <div className={styles.campo}>
                  <label className={styles.label} htmlFor="titulo-editar">
                    Título do Aviso
                  </label>
                  <input
                    id="titulo-editar"
                    type="text"
                    className={styles.inputTitulo}
                    placeholder="Ex: Culto de Celebração & Santa Ceia"
                    value={titulo}
                    onChange={(e) => setTitulo(e.target.value)}
                  />
                </div>
              </>
            )}

            <div className={styles.campo}>
              {ehAvisoOficial && (
                <label className={styles.label} htmlFor="conteudo-editar">
                  Conteúdo do Comunicado
                </label>
              )}
              <textarea
                id={ehAvisoOficial ? 'conteudo-editar' : undefined}
                className={styles.textarea}
                rows={4}
                placeholder="Digite o texto..."
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

            {!ehAvisoOficial && compartilharRede && (
              <div className={styles.tagRedeCompartilhada}>
                <Globe size={12} />
                <span>
                  Será compartilhado com {concordar(congregacao.genero, 'os_min')} demais {congregacao.plural.toLowerCase()}
                </span>
                <button
                  type="button"
                  className={styles.btnRemoverTagRede}
                  onClick={() => setCompartilharRede(false)}
                  title="Remover compartilhamento"
                  aria-label="Remover compartilhamento"
                >
                  <X size={12} />
                </button>
              </div>
            )}

            {!ehAvisoOficial && (
              <div ref={refSeletorTags} {...propsArrastoTags} className={styles.seletorTags}>
                {TAGS_CATEGORIA.map((tag) => (
                  <button
                    key={tag.valor}
                    type="button"
                    className={`${styles.chipTag} ${tipo === tag.valor ? styles.chipTagActive : ''}`}
                    onClick={() => setTipo(tag.valor)}
                  >
                    {tag.label}
                  </button>
                ))}
              </div>
            )}

            {ehAvisoOficial && temFamilia && (
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
                  aria-label="Adicionar ou alterar foto"
                >
                  <ImageIcon size={18} />
                </button>
                <div className={styles.tooltipBox} role="tooltip">
                  Adicionar ou alterar foto
                </div>
              </div>

              {temFamilia && !ehAvisoOficial && (
                <div className={styles.tooltipWrap}>
                  <button
                    type="button"
                    className={`${styles.btnAcaoIcone} ${compartilharRede ? styles.btnRedeAtivo : ''}`}
                    onClick={() => setCompartilharRede((v) => !v)}
                    aria-label={
                      compartilharRede
                        ? `Compartilhando com ${concordar(congregacao.genero, 'os_min')} demais ${congregacao.plural.toLowerCase()}`
                        : `Compartilhar com ${concordar(congregacao.genero, 'os_min')} demais ${congregacao.plural.toLowerCase()}`
                    }
                  >
                    <Globe size={18} />
                  </button>
                  <div className={styles.tooltipBox} role="tooltip">
                    {compartilharRede
                      ? `Compartilhando com ${concordar(congregacao.genero, 'os_min')} demais ${congregacao.plural.toLowerCase()}`
                      : `Compartilhar com ${concordar(congregacao.genero, 'os_min')} demais ${congregacao.plural.toLowerCase()}`}
                  </div>
                </div>
              )}
            </div>

            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="button" className={styles.btnCancelar} onClick={fechar} disabled={atualizarPost.isPending}>
                Cancelar
              </button>
              <button
                type="submit"
                className={styles.btnSalvar}
                disabled={atualizarPost.isPending || !podeSalvar}
              >
                <Check size={14} />
                {atualizarPost.isPending ? 'Salvando...' : 'Salvar Alterações'}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  )
}
