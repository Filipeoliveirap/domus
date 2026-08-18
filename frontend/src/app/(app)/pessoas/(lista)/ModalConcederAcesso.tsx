'use client'

import { useState } from 'react'
import { KeyRound, X, Mail, ShieldCheck, Users, User, Info, RotateCcw } from 'lucide-react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useConcederAcesso } from '@/hooks/pessoa/useConcederAcesso'
import { concederAcessoSchema, type ConcederAcessoFormData, type ConcederAcessoFormInput } from '@/lib/validators'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import { PessoaResponse } from '@/types/pessoa.type'
import styles from './ModalConcederAcesso.module.css'


const ROLES = [
  { value: 'ADMIN_IGREJA', titulo: 'Administrador', descricao: 'Controle total do sistema', icone: ShieldCheck },
  { value: 'LIDER', titulo: 'Líder', descricao: 'Gestão de eventos e visualização de pessoas', icone: Users },
  { value: 'ACESSO_COMUM', titulo: 'Acesso comum', descricao: 'Vê pessoas e eventos, e se inscreve. Sem gestão.', icone: User },
] as const

export function ModalConcederAcesso({
  pessoa,
  onClose,
}: {
  pessoa: PessoaResponse
  onClose: () => void
}) {
  const { confirmar, reativar, cancelarReativacao, precisaReativar, isLoading, erroGeral } = useConcederAcesso(pessoa, onClose)

  const semEmail = !pessoa.email

  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors },
  } = useForm<ConcederAcessoFormInput, unknown, ConcederAcessoFormData>({
    resolver: zodResolver(concederAcessoSchema),
    defaultValues: { role: 'ACESSO_COMUM', email: '' },
  })

  const roleSelecionada = watch('role')

  const [secretario, setSecretario] = useState(false)
  const [tesoureiro, setTesoureiro] = useState(false)

  const capacidades: string[] = []
  if (secretario) capacidades.push('SECRETARIO')
  if (tesoureiro) capacidades.push('TESOUREIRO')

  const onSubmit = (data: ConcederAcessoFormData) => {
    if (semEmail && !data.email) {
      setError('email', { message: 'Informe um e-mail para enviar o convite' })
      return
    }
    confirmar({ ...data, capacidades })
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
              <strong>{pessoa.nome}</strong> já teve acesso ao sistema, que foi arquivado.
              Deseja reativar com o perfil que você acabou de escolher?
            </p>
            <p className={styles.bloqueadoDica}>
              A pessoa receberá um novo convite para definir a senha ou entrar com Google.
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

  // ─── Estado normal: form de convite ────────────────────
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
          <h2 className={styles.title}>Convidar para o sistema</h2>
          <p className={styles.subtitle}>
            <strong>{pessoa.nome}</strong> vai receber um e-mail para definir a senha ou entrar com Google.
          </p>
        </div>

        <form className={styles.form} onSubmit={handleSubmit(onSubmit)}>
          {/* E-mail: read-only quando a pessoa já tem; editável quando não tem */}
          {semEmail ? (
            <Input
              id="email"
              type="email"
              label="E-MAIL PARA O CONVITE"
              placeholder="pessoa@exemplo.com"
              error={errors.email?.message}
              {...register('email')}
            />
          ) : (
            <div className={styles.emailReadonly}>
              <span className={styles.emailLabel}>E-MAIL DO CONVITE</span>
              <div className={styles.emailBox}>
                <Mail size={16} className={styles.emailIcon} />
                <span>{pessoa.email}</span>
              </div>
            </div>
          )}

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

          {/* Capacidades extras */}
          <div className={styles.capacidadesSection}>
            <span className={styles.roleLabel}>CAPACIDADES EXTRAS</span>
            <label className={styles.checkbox}>
              <input type="checkbox" checked={secretario} onChange={e => setSecretario(e.target.checked)} />
              <span>Secretário — gerencia pessoas e visitantes</span>
            </label>
            <label className={styles.checkbox}>
              <input type="checkbox" checked={tesoureiro} onChange={e => setTesoureiro(e.target.checked)} />
              <span>Tesoureiro — acesso ao financeiro</span>
            </label>
          </div>

          {/* Aviso */}
          <div className={styles.infoBox}>
            <Info size={18} className={styles.infoIcon} />
            <p className={styles.infoText}>
              A pessoa define a própria senha pelo link do e-mail (ou entra direto com Google). O convite vale por 7 dias.
            </p>
          </div>

          {erroGeral && <div className={styles.alertError}>{erroGeral}</div>}

          {/* Footer */}
          <div className={styles.footer}>
            <button type="button" className={styles.btnCancel} onClick={onClose}>Cancelar</button>
            <Button type="submit" variant="primary" size="md" isLoading={isLoading}>
              Enviar convite
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
