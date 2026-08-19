'use client'

import { useState } from 'react'
import { Archive, RotateCcw, Trash2, User } from 'lucide-react'
import { useUsuariosArquivados } from '@/hooks/usuario/useUsuariosArquivados'
import { useRestaurarUsuario } from '@/hooks/usuario/useRestaurarUsuario'
import { useExcluirUsuarioDefinitivamente } from '@/hooks/usuario/useExcluirUsuarioDefinitivamente'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { ModalDetalheUsuario } from '@/components/module/usuarios/ModalDetalheUsuario'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarUsuarios } from '@/lib/permissoes'
import { rotuloRole } from '@/lib/formats/usuarioFormat'
import type { UsuarioArquivadoResponse } from '@/types/usuario.types'
import styles from './arquivados.module.css'

export default function UsuariosArquivadosPage() {
  const { data: usuarios, isLoading, isError, refetch } = useUsuariosArquivados()
  const role = useAuthStore((s) => s.role)
  const autorizado = podeGerenciarUsuarios(role)
  const { restaurar, isLoading: restaurando } = useRestaurarUsuario()
  const [excluindo, setExcluindo] = useState<UsuarioArquivadoResponse | null>(null)
  const [detalheId, setDetalheId] = useState<string | null>(null)

  if (!autorizado) {
    return <AcessoRestrito />
  }

  if (isLoading) {
    return (
      <div className={styles.lista}>
        {[1, 2].map((i) => <Skeleton key={i} width="100%" height="64px" radius="var(--radius-lg)" />)}
      </div>
    )
  }

  if (isError) {
    return <EstadoErro titulo="Erro ao carregar" mensagem="Verifique sua conexão." aoTentarNovamente={() => refetch()} />
  }

  if (!usuarios || usuarios.length === 0) {
    return <EstadoVazio icone={Archive} titulo="Nenhum usuário arquivado" mensagem="Usuários com acesso revogado aparecem aqui." />
  }

  return (
    <>
      <div className={styles.lista}>
        {usuarios.map((u) => (
          <div key={u.id} className={styles.linha} onClick={() => setDetalheId(u.id)}>
            <div className={styles.info}>
              <div className={styles.icone}><User size={18} /></div>
              <div>
                <p className={styles.nome}>{u.nome}</p>
                <p className={styles.detalhe}>{u.email ?? 'sem e-mail'} · {rotuloRole(u.role)}</p>
              </div>
            </div>
            <div className={styles.acoes} onClick={(e) => e.stopPropagation()}>
              <button
                className={styles.botaoRestaurar}
                disabled={restaurando}
                onClick={() => restaurar(u.id, u.nome)}
              >
                <RotateCcw size={14} /> Restaurar acesso
              </button>
              <button className={styles.botaoExcluir} onClick={() => setExcluindo(u)}>
                <Trash2 size={14} /> Excluir definitivamente
              </button>
            </div>
          </div>
        ))}
      </div>

      {excluindo && (
        <ModalExcluirDefinitivo usuario={excluindo} onClose={() => setExcluindo(null)} />
      )}

      {detalheId && (
        <ModalDetalheUsuario usuarioId={detalheId} onClose={() => setDetalheId(null)} />
      )}
    </>
  )
}

function ModalExcluirDefinitivo({ usuario, onClose }: { usuario: UsuarioArquivadoResponse; onClose: () => void }) {
  const { confirmar, isLoading } = useExcluirUsuarioDefinitivamente(usuario, onClose)

  return (
    <ModalConfirmacao
      titulo="Excluir login definitivamente?"
      mensagem={
        <>
          Isso vai apagar o login de <strong>{usuario.nome}</strong> de vez. Não tem como
          desfazer. A pessoa e todo o histórico dela (célula, ministério, movimentações,
          eventos…) continuam intactos — só o acesso ao sistema some.
        </>
      }
      textoConfirmar="Excluir"
      perigo
      isLoading={isLoading}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
