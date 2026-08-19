'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Archive, RotateCcw, Trash2, Users } from 'lucide-react'
import { useMinisteriosArquivados } from '@/hooks/ministerio/useMinisteriosArquivados'
import { useRestaurarMinisterio } from '@/hooks/ministerio/useRestaurarMinisterio'
import { useExcluirMinisterioDefinitivamente } from '@/hooks/ministerio/useExcluirMinisterioDefinitivamente'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCadastroMinisterios } from '@/lib/permissoes'
import { ROTULO_MINISTERIO, ROTULO_MINISTERIO_PLURAL } from '@/lib/rotulosMinisterio'
import type { MinisterioResponse } from '@/types/ministerio.type'
import styles from './arquivados.module.css'

export default function MinisteriosArquivadosPage() {
  const { data: ministerios, isLoading, isError, refetch } = useMinisteriosArquivados()
  const role = useAuthStore((s) => s.role)
  const router = useRouter()
  const podeGerenciar = podeGerenciarCadastroMinisterios(role)
  const { restaurar, isLoading: restaurando } = useRestaurarMinisterio()
  const [excluindo, setExcluindo] = useState<MinisterioResponse | null>(null)

  if (!podeGerenciar) {
    return <EstadoErro titulo="Sem acesso" mensagem={`Só administradores veem ${ROTULO_MINISTERIO_PLURAL.toLowerCase()} arquivadas.`} />
  }

  if (isLoading) {
    return (
      <div className={styles.lista}>
        {[1, 2].map(i => <Skeleton key={i} width="100%" height="72px" radius="var(--radius-lg)" />)}
      </div>
    )
  }

  if (isError) {
    return <EstadoErro titulo="Erro ao carregar" mensagem="Verifique sua conexão." aoTentarNovamente={() => refetch()} />
  }

  if (!ministerios || ministerios.length === 0) {
    return <EstadoVazio icone={Archive} titulo={`Nenhuma ${ROTULO_MINISTERIO.toLowerCase()} arquivada`} mensagem={`${ROTULO_MINISTERIO_PLURAL} arquivadas aparecem aqui.`} />
  }

  return (
    <>
      <div className={styles.lista}>
        {ministerios.map((m) => (
          <div key={m.id} className={styles.linha} onClick={() => router.push(`/ministerios/${m.id}`)}>
            <div className={styles.info}>
              <div className={styles.icone}><Users size={18} /></div>
              <p className={styles.nome}>{m.nome}</p>
            </div>
            <div className={styles.acoes} onClick={e => e.stopPropagation()}>
              <button
                className={styles.botaoRestaurar}
                disabled={restaurando}
                onClick={() => restaurar(m.id, m.nome)}
              >
                <RotateCcw size={14} /> Restaurar
              </button>
              <button
                className={styles.botaoExcluir}
                onClick={() => setExcluindo(m)}
              >
                <Trash2 size={14} /> Excluir definitivamente
              </button>
            </div>
          </div>
        ))}
      </div>

      {excluindo && (
        <ModalExcluirDefinitivo ministerio={excluindo} onClose={() => setExcluindo(null)} />
      )}
    </>
  )
}

function ModalExcluirDefinitivo({ ministerio, onClose }: { ministerio: MinisterioResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useExcluirMinisterioDefinitivamente(ministerio, onClose)
  const rotulo = ROTULO_MINISTERIO.toLowerCase()

  if (!ministerio.temVinculo) {
    return (
      <ModalConfirmacao
        titulo={`Excluir ${rotulo} definitivamente?`}
        mensagem={<>Isso vai apagar <strong>{ministerio.nome}</strong> de vez. Não tem como desfazer.</>}
        textoConfirmar="Excluir"
        perigo
        isLoading={isLoading}
        onConfirmar={confirmar}
        onClose={onClose}
      />
    )
  }

  return (
    <ModalConfirmacaoCritica
      titulo={`Excluir ${rotulo} definitivamente?`}
      mensagem={
        <>
          <strong>{ministerio.nome}</strong> tem {ministerio.totalMembros} {ministerio.totalMembros === 1 ? 'pessoa vinculada' : 'pessoas vinculadas'}.
          Isso não vai apagar essas pessoas nem o histórico delas em outros lugares do
          sistema — só remove o vínculo delas com esta {rotulo} específica. A {rotulo} em si
          some de vez. Não tem como desfazer.
        </>
      }
      consequencias={[
        { tipo: 'perde', texto: `Todos os ${ministerio.totalMembros} vínculos com esta ${rotulo} são removidos` },
        { tipo: 'mantem', texto: 'As pessoas continuam existindo normalmente no sistema' },
      ]}
      palavraConfirmacao={ministerio.nome}
      textoConfirmar="Excluir definitivamente"
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
