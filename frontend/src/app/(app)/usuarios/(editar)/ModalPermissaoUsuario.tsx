'use client'
import { useEffect, useState } from 'react'
import { clsx } from 'clsx'
import { ShieldCheck, Users, User, X, Check } from 'lucide-react'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { usuarioService } from '@/services/usuarios.service'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useAuthStore } from '@/store/authStore'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import axios from 'axios'
import type { ApiError } from '@/types/api.types'
import { UsuarioResponse, Role } from '@/types/usuario.types'
import styles from './ModalPermissaoUsuario.module.css'

const roleOptions = [
  { value: 'ADMIN_IGREJA', label: 'Administrador', badge: 'Gestor Total', icon: ShieldCheck, descricao: 'Acesso total ao sistema, configurações da igreja, gestão financeira e controle de pessoas.' },
  { value: 'LIDER', label: 'Líder', badge: 'Gestor de Grupo', icon: Users, descricao: 'Acesso às pessoas e gerência de eventos. Sem acesso à gestão financeira global.' },
  { value: 'ACESSO_COMUM', label: 'Acesso comum', badge: 'Restrito', icon: User, descricao: 'Vê pessoas e eventos, e se inscreve. Sem gestão.' },
] as const

export function ModalPermissaoUsuario({ usuario, onClose }: { usuario: UsuarioResponse; onClose: () => void }) {
  const queryClient = useQueryClient()
  const idLogado = useAuthStore((s) => s.id)
  const atualizarUsuarioLogado = useAuthStore((s) => s.atualizarUsuarioLogado)
  const { celula, ministerio } = useRotulos()

  const capacidadesIniciais = usuario.capacidadesExtras ?? []
  const [roleSelecionada, setRoleSelecionada] = useState<Role>(usuario.role as Role)
  const [secretario, setSecretario] = useState(capacidadesIniciais.includes('SECRETARIO'))
  const [tesoureiro, setTesoureiro] = useState(capacidadesIniciais.includes('TESOUREIRO'))
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const { saindo, fechar } = useFecharAnimado(onClose, 240)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape' && !isLoading) fechar() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar, isLoading])

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = anterior }
  }, [])

  const roleMudou = roleSelecionada !== usuario.role
  const capacidadesMudaram = secretario !== capacidadesIniciais.includes('SECRETARIO')
    || tesoureiro !== capacidadesIniciais.includes('TESOUREIRO')
  const semMudanca = !roleMudou && !capacidadesMudaram

  async function salvar() {
    setErroGeral(null); setIsLoading(true)
    try {
      if (roleMudou) {
        const atualizado = await usuarioService.atualizarRole(usuario.id, roleSelecionada)
        if (idLogado === usuario.id) {
          atualizarUsuarioLogado({ role: atualizado.role as Role })
        }
      }

      if (capacidadesMudaram) {
        const salvarCap = async (cap: string, ativo: boolean) => {
          if (ativo) await usuarioService.concederCapacidade(usuario.id, cap)
          else await usuarioService.revogarCapacidade(usuario.id, cap)
        }
        await Promise.all([
          salvarCap('SECRETARIO', secretario),
          salvarCap('TESOUREIRO', tesoureiro),
        ])
      }

      invalidarCache(queryClient, 'usuario')
      notificar.sucesso('Permissões atualizadas com sucesso!')
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        const e = error.response?.data
        if (e?.error === 'ULTIMO_ADMIN') { setErroGeral(e.message); return }
        setErroGeral(e?.message ?? 'Erro ao alterar permissões. Tente novamente.')
      } else setErroGeral('Erro ao alterar permissões. Tente novamente.')
    } finally { setIsLoading(false) }
  }

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={() => !isLoading && fechar()}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <span className={styles.grabber} aria-hidden="true" />
        <div className={styles.header}>
          <div>
            <h2 className={styles.title}>Alterar permissões</h2>
            <p className={styles.subtitle}>Editando acesso de: <strong>{usuario.nome}</strong></p>
          </div>
          <button type="button" className={styles.btnClose} onClick={fechar} aria-label="Fechar"><X size={20} /></button>
        </div>

        <div className={styles.body}>
          <p className={styles.descricaoTopo}>Selecione o novo nível de privilégio para este usuário. Cada perfil possui permissões específicas.</p>

          <div className={styles.lista}>
            {roleOptions.map((opt) => {
              const Icon = opt.icon
              const selecionada = roleSelecionada === opt.value
              return (
                <button
                  type="button"
                  key={opt.value}
                  onClick={() => setRoleSelecionada(opt.value as Role)}
                  className={`${styles.card} ${selecionada ? styles.cardAtivo : ''}`}
                >
                  <div className={styles.cardIcone}><Icon size={20} /></div>
                  <div className={styles.cardTexto}>
                    <div className={styles.cardTitulo}>
                      <span className={styles.cardNome}>{opt.label}</span>
                      <span className={styles.cardBadge}>{opt.badge}</span>
                    </div>
                    <p className={styles.cardDesc}>{opt.descricao}</p>
                  </div>
                  <span className={`${styles.radio} ${selecionada ? styles.radioAtivo : ''}`}>
                    {selecionada && <Check size={14} />}
                  </span>
                </button>
              )
            })}
          </div>

          <div className={styles.capacidades}>
            <p className={styles.capacidadesLabel}>Capacidades extras</p>
            <div className={styles.capGrid}>
              <label className={styles.capCard}>
                <input type="checkbox" className={styles.capCheck} checked={secretario}
                  onChange={e => setSecretario(e.target.checked)} />
                <span className={styles.capTexto}>
                  <span className={styles.capTitulo}>Secretário</span>
                  <span className={styles.capDesc}>Gerencia pessoas, visitantes, {celula.plural.toLowerCase()} e {ministerio.plural.toLowerCase()}</span>
                </span>
              </label>
              <label className={styles.capCard}>
                <input type="checkbox" className={styles.capCheck} checked={tesoureiro}
                  onChange={e => setTesoureiro(e.target.checked)} />
                <span className={styles.capTexto}>
                  <span className={styles.capTitulo}>Tesoureiro</span>
                  <span className={styles.capDesc}>Acesso ao financeiro</span>
                </span>
              </label>
            </div>
          </div>

          {erroGeral && <div className={styles.alertError}>{erroGeral}</div>}
        </div>

        <div className={styles.footer}>
          <button type="button" className={styles.btnCancel} onClick={fechar}>Cancelar</button>
          <button type="button" disabled={isLoading || semMudanca} className={styles.btnSubmit} onClick={salvar}>
            {isLoading ? 'Salvando...' : 'Salvar alterações'}
          </button>
        </div>
      </div>
    </div>
  )
}