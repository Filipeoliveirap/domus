'use client'

import { useRef, useState } from 'react'
import Image from 'next/image'
import { Send, Image as ImageIcon, Globe, X } from 'lucide-react'
import { SeletorEPreviaFoto, type SeletorEPreviaFotoRef } from '@/components/common/UploadFoto/SeletorEPreviaFoto'
import { useAuthStore } from '@/store/authStore'
import { useCriarPostagem } from '@/hooks/postagem/useCriarPostagem'
import { useVinculoStatus } from '@/hooks/igreja/useVinculo'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { useArrastarParaRolar } from '@/hooks/useArrastarParaRolar'
import { iniciais, doisPrimeirosNomes } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { ModalPerfilResumo, type PerfilResumoDados, type PosicaoTarget } from '@/components/common/ModalPerfilResumo/ModalPerfilResumo'
import { DrawerDetalhePessoa } from '@/app/(app)/pessoas/(lista)/(detalhe)/DrawerDetalhePessoa'
import type { TipoPostagem } from '@/types/postagem.type'
import styles from './CaixaCriarPostagem.module.css'

const TAGS_CATEGORIA: { label: string; valor: TipoPostagem }[] = [
  { label: 'Devocional', valor: 'DEVOCIONAL' },
  { label: 'Resumo do Culto', valor: 'RESUMO_CULTO' },
  { label: 'Pedido de Oração', valor: 'PEDIDO_ORACAO' },
  { label: 'Testemunho', valor: 'TESTEMUNHO' },
  { label: 'Geral', valor: 'GERAL' },
]

export function CaixaCriarPostagem() {
  const meuId = useAuthStore((s) => s.id)
  const meuPessoaId = useAuthStore((s) => s.pessoaId) ?? meuId
  const role = useAuthStore((s) => s.role)
  const nomeCompleto = useAuthStore((s) => s.nome) ?? 'Membro'
  const nomeCurto = doisPrimeirosNomes(nomeCompleto)
  const fotoIdPerfil = useAuthStore((s) => s.fotoId)
  const urlPerfil = urlFoto(fotoIdPerfil, 'THUMB')

  const { data: vinculoStatus } = useVinculoStatus()
  const temFamilia = vinculoStatus != null && vinculoStatus.estado !== 'INDEPENDENTE'
  const { congregacao, concordar } = useRotulos()

  const criarPostagem = useCriarPostagem()
  const [conteudo, setConteudo] = useState('')
  const [tipo, setTipo] = useState<TipoPostagem>('DEVOCIONAL')
  const [fotoId, setFotoId] = useState<string | null>(null)
  const [compartilharRede, setCompartilharRede] = useState(false)
  const [perfilResumo, setPerfilResumo] = useState<PerfilResumoDados | null>(null)
  const [posicaoTarget, setPosicaoTarget] = useState<PosicaoTarget | null>(null)
  const [pessoaDetalheId, setPessoaDetalheId] = useState<string | null>(null)

  const seletorFotoRef = useRef<SeletorEPreviaFotoRef>(null)
  const { ref: refSeletorTags, propsArrasto: propsArrastoTags } = useArrastarParaRolar<HTMLDivElement>()

  const podePublicar = Boolean(conteudo.trim() || fotoId)

  const abrirPerfilUsuario = (e: React.MouseEvent) => {
    e.stopPropagation()
    if (!meuPessoaId) return
    const rect = e.currentTarget.getBoundingClientRect()
    setPosicaoTarget({
      top: rect.top,
      left: rect.left,
      bottom: rect.bottom,
      right: rect.right,
      width: rect.width,
      height: rect.height,
    })
    setPerfilResumo({
      id: meuPessoaId,
      nome: nomeCompleto,
      fotoId: fotoIdPerfil,
      cargo: role === 'ADMIN_IGREJA' ? 'Administrador' : role === 'LIDER' ? 'Líder' : 'Membro',
    })
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!podePublicar) return

    criarPostagem.mutate(
      {
        tipo,
        oficial: false,
        conteudo: conteudo.trim(),
        fotoId: fotoId || undefined,
        restritoPropriaIgreja: temFamilia ? !compartilharRede : true,
      },
      {
        onSuccess: () => {
          setConteudo('')
          setFotoId(null)
          setCompartilharRede(false)
        },
      },
    )
  }

  const textoRotuloRede = `${concordar(congregacao.genero, 'os_min')} demais ${congregacao.plural.toLowerCase()}`

  return (
    <>
      <form className={styles.caixa} onSubmit={handleSubmit}>
        <div className={styles.autorLinha}>
          <div
            className={`${styles.avatar} ${styles.avatarClicavel}`}
            onClick={abrirPerfilUsuario}
            role="button"
            tabIndex={0}
          >
            {urlPerfil ? (
              <Image src={urlPerfil} alt="" width={40} height={40} unoptimized style={{ borderRadius: '50%' }} />
            ) : (
              iniciais(nomeCompleto)
            )}
          </div>
          <div className={styles.corpoInput}>
            <span
              className={styles.nomeAutor}
              onClick={abrirPerfilUsuario}
              role="button"
              tabIndex={0}
              style={{ cursor: 'pointer' }}
            >
              {nomeCurto}
            </span>
          <textarea
            className={styles.textarea}
            rows={2}
            placeholder="O que está em seu coração hoje? Compartilhe um devocional, testemunho ou resumo..."
            value={conteudo}
            onChange={(e) => setConteudo(e.target.value)}
          />
          {compartilharRede && (
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
        </div>
      </div>

      <SeletorEPreviaFoto
        ref={seletorFotoRef}
        fotoId={fotoId}
        onChange={setFotoId}
        formato="post"
      />

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

      <div className={styles.barraAcoes}>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <div className={styles.tooltipWrap}>
            <button
              type="button"
              className={styles.btnAcaoIcone}
              onClick={() => seletorFotoRef.current?.abrirSeletor()}
              aria-label="Adicionar foto"
            >
              <ImageIcon size={18} />
            </button>
            <div className={styles.tooltipBox} role="tooltip">
              Adicionar foto
            </div>
          </div>

          {temFamilia && (
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

        <button
          type="submit"
          className={styles.btnPublicar}
          disabled={criarPostagem.isPending || !podePublicar}
        >
          <Send size={14} />
          {criarPostagem.isPending ? 'Publicando...' : 'Publicar'}
        </button>
      </div>
    </form>

    {perfilResumo && (
      <ModalPerfilResumo
        dados={perfilResumo}
        posicaoTarget={posicaoTarget}
        aoFechar={() => {
          setPerfilResumo(null)
          setPosicaoTarget(null)
        }}
        onVerDetalhesCompletos={(id) => setPessoaDetalheId(id)}
      />
    )}

    {pessoaDetalheId && (
      <DrawerDetalhePessoa
        pessoaId={pessoaDetalheId}
        onClose={() => setPessoaDetalheId(null)}
      />
    )}
  </>
  )
}
