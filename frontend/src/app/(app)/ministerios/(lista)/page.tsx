'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Pencil, Archive, Users, Crown, X, Trash2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCadastroMinisterios } from '@/lib/permissoes'
import { useMinisterios } from '@/hooks/ministerio/useMinisterios'
import { useExcluirMinisterioDefinitivamente } from '@/hooks/ministerio/useExcluirMinisterioDefinitivamente'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { urlFoto } from '@/lib/urlFoto'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { ModalMinisterioForm } from './ModalMinisterioForm'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { ministerioService } from '@/services/ministerio.service'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { useQueryClient } from '@tanstack/react-query'
import type { MinisterioResponse } from '@/types/ministerio.type'
import styles from './ministerios.module.css'

// Rótulo de líder(es) do card — nome do líder + contagem de membros no próprio card,
// sem precisar abrir o detalhe. Sem líder ainda = "Sem líder".
function rotuloLideres(lideres: string[]): string {
  if (lideres.length === 0) return 'Sem líder'
  if (lideres.length === 1) return lideres[0]
  return `${lideres[0]} +${lideres.length - 1}`
}

export default function MinisteriosPage() {
  const router = useRouter()
  const role = useAuthStore((s) => s.role)
  const hidratado = useAuthStore((s) => s.hidratado)
  const podeGerenciar = podeGerenciarCadastroMinisterios(role)

  const { data: ministerios = [], isLoading } = useMinisterios()
  const { ministerio: rotuloMinisterio } = useRotulos()
  const queryClient = useQueryClient()
  // `null` = fechado; `'novo'` = criar; objeto = editar (mesma convenção de /eventos/locais).
  const [formAberto, setFormAberto] = useState<'novo' | MinisterioResponse | null>(null)
  const [excluindoDefinitivo, setExcluindoDefinitivo] = useState<MinisterioResponse | null>(null)
  const [fotoVisualizando, setFotoVisualizando] = useState<string | null>(null)
  const [arquivandoId, setArquivandoId] = useState<string | null>(null)

  // Arquivar é reversível (dá pra restaurar na aba Arquivadas) — ação direta, sem
  // confirmação, mesmo padrão de Célula. Confirmação escrita fica só pra excluir de vez.
  async function handleArquivar(ministerio: MinisterioResponse) {
    if (arquivandoId) return
    setArquivandoId(ministerio.id)
    try {
      await ministerioService.arquivar(ministerio.id)
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso(`${ministerio.nome} foi arquivada.`)
    } catch {
      notificar.erro(`Erro ao arquivar ${rotuloMinisterio.singular.toLowerCase()}.`)
    } finally {
      setArquivandoId(null)
    }
  }

  if (!hidratado || isLoading) {
    return (
      <div className={styles.pagina}>
        <div className={styles.grade}>
          {[1,2,3].map(i => (
            <div key={i} className={styles.card}>
              <Skeleton width="48px" height="48px" radius="var(--radius-lg)" />
              <Skeleton width="70%" height="18px" />
              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 'auto', paddingTop: 12 }}>
                <Skeleton width="80px" height="14px" />
                <Skeleton width="30px" height="24px" radius="var(--radius-full)" />
              </div>
            </div>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <div>
          <div className={styles.tituloLinha}>
            <h1 className={styles.titulo}>{rotuloMinisterio.plural}</h1>
            {ministerios.length > 0 && <span className={styles.contador}>{ministerios.length}</span>}
          </div>
          <p className={styles.subtitulo}>{rotuloMinisterio.plural} da igreja e quem participa de cada uma</p>
        </div>
        {podeGerenciar && (
          <button type="button" className={styles.botaoPrimario} onClick={() => setFormAberto('novo')}>
            Nova {rotuloMinisterio.singular.toLowerCase()}
          </button>
        )}
      </header>

      {ministerios.length === 0 ? (
        <EstadoVazio
          icone={Users}
          titulo={`Nenhuma ${rotuloMinisterio.singular.toLowerCase()} cadastrada`}
          mensagem={podeGerenciar
            ? `Cadastre a primeira ${rotuloMinisterio.singular.toLowerCase()} da igreja.`
            : `Nenhuma ${rotuloMinisterio.singular.toLowerCase()} foi cadastrada ainda.`}
          acaoPrimaria={podeGerenciar ? { label: `Nova ${rotuloMinisterio.singular.toLowerCase()}`, onClick: () => setFormAberto('novo') } : undefined}
        />
      ) : (
        <div className={styles.grade}>
          {ministerios.map((ministerio) => {
            const acoes: ItemAcao[] = [
              { label: 'Editar', icone: Pencil, onClick: () => setFormAberto(ministerio) },
              ministerio.temVinculo
                ? { label: 'Arquivar', icone: Archive, onClick: () => handleArquivar(ministerio), perigo: true, separadorAntes: true }
                : { label: 'Excluir', icone: Trash2, onClick: () => setExcluindoDefinitivo(ministerio), perigo: true, separadorAntes: true },
            ]
            return (
              <div
                key={ministerio.id}
                className={styles.card}
                role="button"
                tabIndex={0}
                onClick={() => router.push(`/ministerios/${ministerio.id}`)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') router.push(`/ministerios/${ministerio.id}`)
                }}
              >
                {podeGerenciar && (
                  <div className={styles.cardActions} onClick={(e) => e.stopPropagation()}>
                    <MenuAcoes itens={acoes} />
                  </div>
                )}
                {ministerio.fotoId ? (
                  <img
                    src={urlFoto(ministerio.fotoId, 'THUMB')!} alt=""
                    className={styles.cardFoto}
                    onClick={(e) => { e.stopPropagation(); setFotoVisualizando(ministerio.fotoId) }}
                  />
                ) : (
                  <div className={styles.cardIcon}>
                    <Users size={24} />
                  </div>
                )}
                <div className={styles.cardTopo}>
                  <span className={styles.cardTitulo}>{ministerio.nome}</span>
                </div>
                <div className={styles.cardLider}>
                  <Crown size={14} />
                  <span>{rotuloLideres(ministerio.lideres)}</span>
                </div>
                <div className={styles.cardMembros}>
                  {ministerio.totalMembros} {ministerio.totalMembros === 1 ? 'membro' : 'membros'}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {formAberto && (
        <ModalMinisterioForm ministerio={formAberto === 'novo' ? null : formAberto} onClose={() => setFormAberto(null)} />
      )}
      {excluindoDefinitivo && (
        <ModalExcluirDefinitivo ministerio={excluindoDefinitivo} onClose={() => setExcluindoDefinitivo(null)} />
      )}
      {fotoVisualizando && (
        <div className={styles.viewerOverlay} onMouseDown={() => setFotoVisualizando(null)}>
          <div className={styles.viewerModal} onMouseDown={e => e.stopPropagation()}>
            <button className={styles.viewerClose} onClick={() => setFotoVisualizando(null)}>
              <X size={20} />
            </button>
            <img src={urlFoto(fotoVisualizando, 'DISPLAY')!} alt="" className={styles.viewerImg} />
          </div>
        </div>
      )}
    </div>
  )
}

function ModalExcluirDefinitivo({ ministerio, onClose }: { ministerio: MinisterioResponse; onClose: () => void }) {
  // Esse botão só aparece quando não tem ninguém vinculado (senão o menu mostra
  // "Arquivar") — confirmação simples basta, sem precisar digitar o nome.
  const { confirmar, isLoading } = useExcluirMinisterioDefinitivamente(ministerio, onClose)
  const { ministerio: rotuloMinisterio } = useRotulos()
  return (
    <ModalConfirmacao
      titulo={`Excluir ${rotuloMinisterio.singular.toLowerCase()} definitivamente?`}
      mensagem={<>Isso vai apagar <strong>{ministerio.nome}</strong> de vez. Não tem como desfazer.</>}
      textoConfirmar="Excluir"
      perigo
      isLoading={isLoading}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
