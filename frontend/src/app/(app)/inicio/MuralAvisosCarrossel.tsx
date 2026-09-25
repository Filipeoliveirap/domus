'use client'

import { useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import { Megaphone, ChevronLeft, ChevronRight, Plus, Clock, User, Globe, Building2 } from 'lucide-react'

import { useMuralAvisos } from '@/hooks/postagem/useMuralAvisos'
import { useDeletarPostagem } from '@/hooks/postagem/useDeletarPostagem'
import { useAuthStore } from '@/store/authStore'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { ModalNovoAviso } from './ModalNovoAviso'
import { ModalDetalheAviso } from './ModalDetalheAviso'
import { ModalPerfilResumo, type PerfilResumoDados, type PosicaoTarget } from '@/components/common/ModalPerfilResumo/ModalPerfilResumo'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { doisPrimeirosNomes } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { CarrosselSuave, type CarrosselSuaveRef } from '@/components/common/CarrosselSuave/CarrosselSuave'
import type { Postagem } from '@/types/postagem.type'

import styles from './MuralAvisosCarrossel.module.css'

export function formatarTipoAviso(tipo: string): string {
  switch (tipo) {
    case 'MURAL_AVISO':
      return 'Comunicado Pastoral'
    case 'RESUMO_CULTO':
      return 'Escala de Voluntários'
    case 'GERAL':
      return 'Ação Social'
    case 'DEVOCIONAL':
      return 'Devocional'
    case 'TESTEMUNHO':
      return 'Testemunho'
    case 'PEDIDO_ORACAO':
      return 'Pedido de Oração'
    default:
      return tipo.replace('_', ' ')
  }
}

function SkeletonMuralAvisos() {
  return (
    <section className={styles.secaoMural} aria-busy="true" aria-label="Carregando mural de avisos">
      <div className={styles.cabecalho}>
        <div className={styles.tituloInfo}>
          <Skeleton width="36px" height="36px" radius="var(--radius-md)" />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <Skeleton width="220px" height="18px" />
            <Skeleton width="320px" height="12px" />
          </div>
        </div>
        <div className={styles.controles}>
          <Skeleton width="105px" height="32px" radius="var(--radius-sm)" />
          <Skeleton width="32px" height="32px" radius="var(--radius-sm)" />
          <Skeleton width="32px" height="32px" radius="var(--radius-sm)" />
        </div>
      </div>
      <div className={styles.barraFiltros}>
        <Skeleton width="55px" height="24px" radius="14px" />
        <Skeleton width="95px" height="24px" radius="14px" />
        <Skeleton width="65px" height="24px" radius="14px" />
        <Skeleton width="85px" height="24px" radius="14px" />
      </div>
      <div className={styles.carrosselTrilha} style={{ pointerEvents: 'none', overflowX: 'hidden' }}>
        {[1, 2, 3].map((i) => (
          <article key={i} className={styles.cardAviso} style={{ animation: 'none' }}>
            <div className={styles.faixaDestaque} />
            <div>
              <div className={styles.cardTopo}>
                <Skeleton width="120px" height="16px" radius="12px" />
                <Skeleton width="65px" height="12px" radius="4px" />
              </div>
              <Skeleton width="80%" height="16px" style={{ margin: '8px 0 6px' }} />
              <Skeleton width="100%" height="12px" style={{ marginBottom: 4 }} />
              <Skeleton width="60%" height="12px" style={{ marginBottom: 12 }} />
            </div>
            <div className={styles.cardRodape}>
              <div className={styles.autorInfo}>
                <Skeleton width="14px" height="14px" circle />
                <Skeleton width="80px" height="12px" />
              </div>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

export function MuralAvisosCarrossel() {
  const { data: avisos, isLoading } = useMuralAvisos()
  const deletarPost = useDeletarPostagem()
  const role = useAuthStore((s) => s.role)
  const minhaIgrejaId = useAuthStore((s) => s.igrejaId)
  const { congregacao, concordar } = useRotulos()

  const podeCriarAviso = role === 'ADMIN_IGREJA' || role === 'LIDER'
  const [modalNovoAberto, setModalNovoAberto] = useState(false)
  const [avisoSelecionado, setAvisoSelecionado] = useState<Postagem | null>(null)
  const [saindoAvisoId, setSaindoAvisoId] = useState<string | null>(null)
  const [perfilResumo, setPerfilResumo] = useState<PerfilResumoDados | null>(null)
  const [posicaoTarget, setPosicaoTarget] = useState<PosicaoTarget | null>(null)
  const [filtroTipo, setFiltroTipo] = useState<string>('TODOS')
  const [estadoScroll, setEstadoScroll] = useState({ noInicio: true, noFim: false })

  const carrosselRef = useRef<CarrosselSuaveRef>(null)
  const idAnteriorRef = useRef<string | null>(null)

  const listaTotal = avisos ?? []
  const listaAvisos = listaTotal.filter((a) => {
    if (filtroTipo === 'TODOS') return true
    return a.tipo === filtroTipo
  })

  const primeiroAvisoId = listaAvisos[0]?.id

  useEffect(() => {
    if (idAnteriorRef.current && primeiroAvisoId && idAnteriorRef.current !== primeiroAvisoId) {
      carrosselRef.current?.rolarParaInicio()
    }
    idAnteriorRef.current = primeiroAvisoId ?? null
  }, [primeiroAvisoId])

  const handleDeletarAviso = (avisoId: string) => {
    setSaindoAvisoId(avisoId)
    setTimeout(() => {
      deletarPost.mutate(avisoId, {
        onSuccess: () => {
          setSaindoAvisoId(null)
        },
      })
    }, 280)
  }

  const FILTROS = [
    { label: 'Todos', valor: 'TODOS' },
    { label: 'Comunicados', valor: 'MURAL_AVISO' },
    { label: 'Escalas', valor: 'RESUMO_CULTO' },
    { label: 'Ação Social', valor: 'GERAL' },
  ]

  const rolar = (direcao: 'esq' | 'dir') => {
    carrosselRef.current?.rolar(direcao)
  }

  if (isLoading) {
    return <SkeletonMuralAvisos />
  }

  const avisoAtualizado = (avisos ?? []).find((a) => a.id === avisoSelecionado?.id) ?? avisoSelecionado

  return (
    <section className={styles.secaoMural}>
      <div className={styles.cabecalho}>
        <div className={styles.tituloInfo}>
          <div className={styles.iconeCampaign}>
            <Megaphone size={20} />
          </div>
          <div>
            <h2 className={styles.titulo}>
              Mural Oficial & Quadro de Avisos
              {listaTotal.length > 0 && (
                <span className={styles.badgeAtivos}>
                  {listaTotal.length} {listaTotal.length === 1 ? 'aviso' : 'avisos'}
                </span>
              )}
            </h2>
            <p className={styles.subtitulo}>
              Comunicados pastorais, escalas ministeriais e avisos oficiais da congregação
            </p>
          </div>
        </div>

        <div className={styles.controles}>
          {podeCriarAviso && (
            <button
              type="button"
              className={styles.btnNovoAviso}
              onClick={() => setModalNovoAberto(true)}
            >
              <Plus size={16} />
              Novo aviso
            </button>
          )}

          <button
            type="button"
            className={styles.btnNavegacao}
            onClick={() => rolar('esq')}
            disabled={estadoScroll.noInicio}
            aria-label="Aviso anterior"
          >
            <ChevronLeft size={18} />
          </button>

          <button
            type="button"
            className={styles.btnNavegacao}
            onClick={() => rolar('dir')}
            disabled={estadoScroll.noFim}
            aria-label="Próximo aviso"
          >
            <ChevronRight size={18} />
          </button>
        </div>
      </div>

      <div className={styles.barraFiltros}>
        {FILTROS.map((f) => (
          <button
            key={f.valor}
            type="button"
            className={`${styles.chipFiltro} ${filtroTipo === f.valor ? styles.chipFiltroAtivo : ''}`}
            onClick={() => setFiltroTipo(f.valor)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {listaAvisos.length === 0 ? (
        <div className={`${styles.cardAviso} card-painel`}>
          <p className={styles.cardConteudo}>Nenhum aviso publicado no mural até o momento.</p>
        </div>
      ) : (
        <CarrosselSuave
          ref={carrosselRef}
          className={styles.carrosselTrilha}
          onEstadoScrollChange={setEstadoScroll}
        >
          {listaAvisos.map((aviso: Postagem) => {
            const estaSaindo = aviso.id === saindoAvisoId
            const ehOutraIgreja = aviso.igrejaAutor != null && aviso.igrejaAutor.id !== minhaIgrejaId

            return (
              <article
                key={aviso.id}
                className={`${styles.cardAviso} ${estaSaindo ? styles.saindoCard : ''} card-interativo`}
                onClick={() => setAvisoSelecionado(aviso)}
                role="button"
                tabIndex={0}
              >
                <div className={styles.faixaDestaque} />
                <div>
                  <div className={styles.cardTopo}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <span className={styles.tagTipo}>{formatarTipoAviso(aviso.tipo)}</span>
                      {aviso.restritoPropriaIgreja === false && (
                        <span
                          className={styles.tagRede}
                          title={`Aviso compartilhado com ${concordar(congregacao.genero, 'os_min')} demais ${congregacao.plural.toLowerCase()}`}
                        >
                          <Globe size={11} aria-hidden="true" />
                          {congregacao.plural}
                        </span>
                      )}
                    </div>
                    <span className={styles.dataTime}>
                      <Clock size={13} />
                      {new Date(aviso.criadoEm).toLocaleDateString('pt-BR')}
                    </span>
                  </div>

                  {aviso.titulo && <h3 className={styles.cardTitulo}>{aviso.titulo}</h3>}
                  <p className={styles.cardConteudo}>{aviso.conteudo}</p>

                  {aviso.fotoId && urlFoto(aviso.fotoId, 'DISPLAY') && (
                    <div className={styles.fotoCardContainer}>
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={urlFoto(aviso.fotoId, 'DISPLAY')!}
                        alt={aviso.titulo ?? 'Foto do aviso'}
                        className={styles.fotoCardAviso}
                        loading="lazy"
                      />
                    </div>
                  )}
                </div>

                <div className={styles.cardRodape}>
                  <div
                    className={styles.autorInfo}
                    onClick={(e) => {
                      e.stopPropagation()
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
                        id: aviso.autor.id,
                        nome: aviso.autor.nome,
                        fotoId: aviso.autor.fotoId,
                        cargo: aviso.autor.cargo,
                        igreja: aviso.igrejaAutor,
                      })
                    }}
                    role="button"
                    tabIndex={0}
                    style={{ cursor: 'pointer' }}
                  >
                    <User size={14} />
                    <span>{doisPrimeirosNomes(aviso.autor.nome)}</span>
                  </div>
                </div>
              </article>
            )
          })}
        </CarrosselSuave>
      )}

      {modalNovoAberto && <ModalNovoAviso aoFechar={() => setModalNovoAberto(false)} />}

      {avisoSelecionado && avisoAtualizado && (
        <ModalDetalheAviso
          aviso={avisoAtualizado}
          aoFechar={() => setAvisoSelecionado(null)}
          onDeletar={handleDeletarAviso}
        />
      )}

      {perfilResumo && (
        <ModalPerfilResumo
          dados={perfilResumo}
          posicaoTarget={posicaoTarget}
          aoFechar={() => {
            setPerfilResumo(null)
            setPosicaoTarget(null)
          }}
        />
      )}
    </section>
  )
}
