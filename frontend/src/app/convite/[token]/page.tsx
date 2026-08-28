'use client'

import { use, useState } from 'react'
import axios from 'axios'
import Link from 'next/link'
import { Building2, CalendarDays, Clock, MapPin, Ticket, User } from 'lucide-react'
import { useConvitePublico } from '@/hooks/convite/useConvitePublico'
import { authService } from '@/services/auth.service'
import { useQuery } from '@tanstack/react-query'
import { periodoEvento, hora } from '@/lib/formats/eventoFormat'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { FormularioConvidado } from './FormularioConvidado'
import { EntrarLogado } from './EntrarLogado'
import styles from './ConvitePublico.module.css'

type Etapa = 'landing' | 'escolha' | 'formulario' | 'sucesso'

/** Só as 3 fotos que o backend libera pra este token (ver ConviteController.foto) — nunca a
 *  rota autenticada /fotos/{id}, que exige sessão. */
function urlFotoConvite(token: string, fotoId: string | null, tamanho: 'THUMB' | 'DISPLAY' = 'DISPLAY') {
  return fotoId ? `/api/convites/${token}/fotos/${fotoId}?tamanho=${tamanho}` : null
}

export default function ConvitePublicoPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = use(params)
  const { data: convite, isLoading, error } = useConvitePublico(token)

  // Sessão própria, sem depender do authStore global (que é da área logada/AuthGuard) — esta
  // página precisa funcionar pra quem nunca abriu o Domus antes.
  const { data: sessao, isFetched: sessaoVerificada } = useQuery({
    queryKey: ['convite-sessao-atual'],
    queryFn: () => authService.me({ semRedirect: true }).catch((erro: unknown) => {
      if (axios.isAxiosError(erro) && erro.response?.status === 401) return null
      throw erro
    }),
    retry: false,
  })

  // window.location (não useSearchParams) — mesmo motivo do AuthGuard: evita forçar a rota
  // inteira a virar dinâmica só por causa de um parâmetro lido uma vez no mount. Volta do
  // login com `?entrar=1` já cai direto no formulário, sem precisar clicar "Inscrever-se"
  // de novo (senão a pessoa perdia a intenção original ao sair pra logar e voltar).
  const [etapa, setEtapa] = useState<Etapa>(() => (
    typeof window !== 'undefined' && window.location.search.includes('entrar=1') ? 'formulario' : 'landing'
  ))

  if (isLoading) {
    return <div className={styles.pagina}><p className={styles.estado}>Carregando…</p></div>
  }

  if (error) {
    const status = axios.isAxiosError(error) ? error.response?.status : undefined
    return (
      <div className={styles.pagina}>
        <div className={styles.erroCard}>
          <h1>{status === 400 ? 'Este evento já aconteceu' : 'Convite inválido'}</h1>
          <p>{status === 400
            ? 'O evento pro qual você foi convidado já passou.'
            : 'Este link não é mais válido — peça um novo pra quem te convidou.'}</p>
        </div>
      </div>
    )
  }

  if (!convite) return null

  if (etapa === 'sucesso') {
    return (
      <div className={styles.pagina}>
        <div className={styles.erroCard}>
          <h1>Inscrição confirmada!</h1>
          <p>Você está inscrito(a) em &quot;{convite.titulo}&quot;.</p>
        </div>
      </div>
    )
  }

  const logoUrl = urlFotoConvite(token, convite.igrejaLogoFotoId, 'THUMB')
  const bannerUrl = urlFotoConvite(token, convite.fotoId, 'DISPLAY')
  const convidanteFotoUrl = urlFotoConvite(token, convite.convidadoPorFotoId, 'THUMB')

  return (
    <div className={styles.pagina}>
      <header className={styles.topo}>
        {logoUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={logoUrl} alt="" className={styles.topoLogo} />
        ) : (
          <span className={styles.topoIcone}><Building2 size={18} /></span>
        )}
        <span>{convite.igrejaNome}</span>
      </header>

      <div className={styles.conteudo}>
        <div className={styles.hero}>
          {bannerUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={bannerUrl} alt={convite.titulo} className={styles.banner} />
          ) : (
            <div className={styles.bannerPlaceholder}><CalendarDays size={40} /></div>
          )}
          <h1 className={styles.titulo}>{convite.titulo}</h1>
          {convite.descricao && <p className={styles.descricao}>{convite.descricao}</p>}
        </div>

        {convite.convidadoPorNome && !sessao && (
          <div className={styles.convidantePor}>
            {convidanteFotoUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={convidanteFotoUrl} alt="" className={styles.convidanteFoto} />
            ) : (
              <span className={styles.convidanteIconePlaceholder}><User size={20} /></span>
            )}
            <div>
              <p className={styles.convidantePorLabel}>Você foi convidado por</p>
              <p className={styles.convidantePorNome}>{convite.convidadoPorNome}</p>
            </div>
          </div>
        )}

        <div className={styles.infoCard}>
          <div className={styles.infoLinha}>
            <CalendarDays size={18} />
            <span>{periodoEvento(convite)}</span>
          </div>
          <div className={styles.infoLinha}>
            <Clock size={18} />
            <span>Início às {hora(convite.inicioEm)}</span>
          </div>
          {convite.localNome && (
            <div className={styles.infoLinha}>
              <MapPin size={18} />
              <span>{convite.localNome}{convite.localEndereco ? ` — ${convite.localEndereco}` : ''}</span>
            </div>
          )}
          {convite.preco !== null && (
            <div className={styles.infoLinha}>
              <Ticket size={18} />
              <span>{formatarMoeda(convite.preco)}</span>
            </div>
          )}
          {convite.vagasRestantes !== null && (
            <p className={styles.aviso}>{convite.vagasRestantes} vagas restantes</p>
          )}

          {convite.requerInscricao && etapa === 'landing' && (
            <button
              type="button"
              className={styles.btnInscrever}
              onClick={() => setEtapa(sessaoVerificada && sessao ? 'formulario' : 'escolha')}
              disabled={!sessaoVerificada}
            >
              Inscrever-se
            </button>
          )}
        </div>

        {/* Evento sem inscrição: convite é só informativo (título/data/hora/local acima) —
           não tem o que confirmar, então nem etapa 'escolha'/'formulario' existe aqui. */}
        {convite.requerInscricao && etapa === 'escolha' && (
          <div className={styles.escolha}>
            <Link href={`/login?next=${encodeURIComponent(`/convite/${token}?entrar=1`)}`} className={styles.btnLogin}>
              Já tenho conta — Fazer login
            </Link>
            <button type="button" className={styles.btnSemConta} onClick={() => setEtapa('formulario')}>
              Continuar sem conta
            </button>
          </div>
        )}

        {convite.requerInscricao && etapa === 'formulario' && !sessao && (
          <FormularioConvidado token={token} eventoId={convite.eventoId} campos={convite.campos} preco={convite.preco} onSucesso={() => setEtapa('sucesso')} />
        )}

        {convite.requerInscricao && etapa === 'formulario' && sessao && (
          <EntrarLogado eventoId={convite.eventoId} nomeUsuario={sessao.nome} onSucesso={() => setEtapa('sucesso')} />
        )}
      </div>
    </div>
  )
}
