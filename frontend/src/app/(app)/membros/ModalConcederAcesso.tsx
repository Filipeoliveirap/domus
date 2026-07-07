'use client'

import { useState } from 'react'
import { KeyRound, X, Mail, Eye, EyeOff, ShieldCheck, Users, User, Info, AlertTriangle, RotateCcw } from 'lucide-react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useConcederAcesso } from '@/hooks/membro/useConcederAcesso'
import { concederAcessoSchema, type ConcederAcessoFormData } from '@/lib/validators'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import { MembroResponse } from '@/types/membro.type'
import styles from './ModalConcederAcesso.module.css'


const ROLES = [
  { value: 'ADMIN_IGREJA', titulo: 'Administrador', descricao: 'Controle total do sistema', icone: ShieldCheck },
  { value: 'LIDER', titulo: 'Líder', descricao: 'Gestão de grupos e eventos', icone: Users },
  { value: 'MEMBRO', titulo: 'Membro', descricao: 'Acesso básico ao perfil', icone: User },
] as const

export function ModalConcederAcesso({
  membro,
  onClose,
}: {
  membro: MembroResponse
  onClose: () => void
}) {
  const { confirmar, reativar, cancelarReativacao, precisaReativar, isLoading, erroGeral } = useConcederAcesso(membro, onClose)
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const [mostrarConfirmar, setMostrarConfirmar] = useState(false)

  const semEmail = !membro.email

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<ConcederAcessoFormData>({
    resolver: zodResolver(concederAcessoSchema),
    defaultValues: { role: 'MEMBRO', senha: '', confirmarSenha: '' },
  })

  const roleSelecionada = watch('role')

  if (semEmail) {
    return (
      <div className={styles.overlay} onMouseDown={onClose}>
        <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()}>
          <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar">
            <X size={20} />
          </button>
          <div className={styles.bloqueado}>
            <div className={styles.bloqueadoIcone}><AlertTriangle size={28} /></div>
            <h2 className={styles.title}>E-mail necessário</h2>
            <p className={styles.bloqueadoTexto}>
              O membro <strong>{membro.nome}</strong> não tem um e-mail cadastrado.
              O e-mail é necessário para conceder acesso, pois é com ele que a pessoa faz login.
            </p>
            <p className={styles.bloqueadoDica}>
              Edite o cadastro do membro e adicione um e-mail antes de conceder acesso.
            </p>
            <Button variant="ghost" size="md" onClick={onClose} style={{ width: '100%' }}>
              Entendi
            </Button>
          </div>
        </div>
      </div>
    )
  }

  if (precisaReativar) {
    return (
      <div className={styles.overlay} onMouseDown={onClose}>
        <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()}>
          <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar">
            <X size={20} />
          </button>
          <div className={styles.bloqueado}>
            <div className={styles.reativarIcone}><RotateCcw size={28} /></div>
            <h2 className={styles.title}>Reativar acesso?</h2>
            <p className={styles.bloqueadoTexto}>
              <strong>{membro.nome}</strong> já teve acesso ao sistema, que foi arquivado.
              Deseja reativar com a senha e o perfil que você acabou de definir?
            </p>
            <p className={styles.bloqueadoDica}>
              O acesso anterior será restaurado com as novas credenciais.
            </p>

            {erroGeral && <div className={styles.alertError}>{erroGeral}</div>}

            <div className={styles.reativarAcoes}>
              <button type="button" className={styles.btnCancel} onClick={cancelarReativacao}>
                Voltar
              </button>
              <Button variant="primary" size="md" isLoading={isLoading} onClick={reativar}>
                Sim, reativar
              </Button>
            </div>
          </div>
        </div>
      </div>
    )
  }

  // ─── Estado normal: form de conceder acesso ────────────────────
  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <div className={styles.iconBox}>
            <KeyRound size={24} />
          </div>
          <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar">
            <X size={20} />
          </button>
        </div>

        <div className={styles.intro}>
          <h2 className={styles.title}>Conceder acesso ao sistema</h2>
          <p className={styles.subtitle}>
            Dê a <strong>{membro.nome}</strong> um login para acessar a plataforma.
          </p>
        </div>

        <form className={styles.form} onSubmit={handleSubmit(confirmar)}>
          {/* E-mail — read-only, vem do membro */}
          <div className={styles.emailReadonly}>
            <span className={styles.emailLabel}>E-MAIL DE LOGIN</span>
            <div className={styles.emailBox}>
              <Mail size={16} className={styles.emailIcon} />
              <span>{membro.email}</span>
            </div>
          </div>

          {/* Senha + confirmar */}
          <div className={styles.senhaGrid}>
            <Input
              id="senha"
              type={mostrarSenha ? 'text' : 'password'}
              label="SENHA"
              placeholder="••••••••"
              autoComplete="new-password"
              error={errors.senha?.message}
              rightElement={
                <button type="button" className={styles.toggle} onClick={() => setMostrarSenha((v) => !v)} aria-label="Mostrar senha">
                  {mostrarSenha ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              }
              {...register('senha')}
            />
            <Input
              id="confirmarSenha"
              type={mostrarConfirmar ? 'text' : 'password'}
              label="CONFIRMAR SENHA"
              placeholder="••••••••"
              autoComplete="new-password"
              error={errors.confirmarSenha?.message}
              rightElement={
                <button type="button" className={styles.toggle} onClick={() => setMostrarConfirmar((v) => !v)} aria-label="Mostrar senha">
                  {mostrarConfirmar ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              }
              {...register('confirmarSenha')}
            />
          </div>

          {/* Nível de acesso (role) */}
          <div className={styles.roleSection}>
            <span className={styles.roleLabel}>NÍVEL DE ACESSO</span>
            <div className={styles.roleGrid}>
              {ROLES.map((r) => {
                const Icone = r.icone
                const ativo = roleSelecionada === r.value
                return (
                  <label key={r.value} className={`${styles.roleCard} ${ativo ? styles.roleCardAtivo : ''}`}>
                    <input
                      type="radio"
                      value={r.value}
                      className={styles.roleRadio}
                      {...register('role')}
                    />
                    <Icone size={20} className={styles.roleIcone} />
                    <span className={styles.roleTitulo}>{r.titulo}</span>
                    <span className={styles.roleDescricao}>{r.descricao}</span>
                  </label>
                )
              })}
            </div>
            {errors.role && <span className={styles.erroCampo}>{errors.role.message}</span>}
          </div>

          {/* Aviso */}
          <div className={styles.infoBox}>
            <Info size={18} className={styles.infoIcon} />
            <p className={styles.infoText}>
              Este membro se tornará um usuário ativo. Por segurança, oriente-o a trocar a senha após o primeiro acesso.
            </p>
          </div>

          {erroGeral && <div className={styles.alertError}>{erroGeral}</div>}

          {/* Footer */}
          <div className={styles.footer}>
            <button type="button" className={styles.btnCancel} onClick={onClose}>Cancelar</button>
            <Button type="submit" variant="primary" size="md" isLoading={isLoading}>
              Confirmar acesso
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}