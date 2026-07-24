package com.domus.api.modules.evento.DTOs;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.SituacaoEvento;
import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.Sexo;
import com.domus.api.modules.usuario.Usuario;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventoResponse(
        UUID id,
        String titulo,
        String descricao,
        LocalDateTime inicioEm,
        LocalDateTime fimEm,
        /** Local cadastrado (com endereço) ou ad-hoc (só nome); null = sem local definido. */
        LocalInfo local,
        String tipo,
        PessoaResumo responsavel,
        PessoaResumo criadoPor,
        PessoaResumo atualizadoPor,
        UUID fotoId,
        LocalDateTime createdAt,
        Integer vagas,
        java.math.BigDecimal preco,
        boolean exclusivoMembros,
        boolean requerInscricao,
        boolean controlaPresenca,
        SituacaoEvento situacao,
        /**
         * Só populado pela edição que ligou {@code exclusivoMembros} e removeu automaticamente
         * quem não se qualifica mais (ver B4). {@code null} em toda outra resposta — campo
         * aditivo, não quebra quem já consome {@code EventoResponse}.
         */
        Integer inscricoesRemovidas,
        String recorteEtario,
        Integer idadeMin,
        Integer idadeMax,
        EstadoCivil restricaoEstadoCivil,
        Sexo restricaoSexo
) {
    /** {@code id} null = local ad-hoc (o texto veio de {@code localTexto}, não de cadastro). */
    public record LocalInfo(UUID id, String nome, String endereco, boolean enderecoHerdado) {
        static LocalInfo from(Evento e) {
            LocalEvento local = e.getLocal();
            if (local != null) {
                // Reusa a resolução do LocalEventoResponse: endereço próprio, OU o da igreja
                // quando herdado. Antes montava só o próprio (null no herdado), então os
                // modais de evento mostravam o nome do local sem endereço nenhum embaixo.
                var r = com.domus.api.modules.evento.local.DTOs.LocalEventoResponse.from(local);
                return new LocalInfo(local.getId(), local.getNome(), r.endereco(), r.enderecoHerdado());
            }
            if (e.getLocalTexto() != null) {
                return new LocalInfo(null, e.getLocalTexto(), null, false);
            }
            return null;
        }
    }

    public record PessoaResumo(UUID id, String nome) {
        /**
         * {@code textoFallback} é o nome congelado no momento em que a pessoa foi arquivada
         * (ver {@code Evento#responsavelTexto} etc.) — {@code id() == null} nesse caso sinaliza
         * "não é mais um cadastro navegável", igual ao {@code LocalInfo} com local ad-hoc.
         * Sem este fallback, arquivar a pessoa/usuário deixaria {@code p}/{@code u} null (a FK
         * já foi zerada no arquivamento) e a resposta perderia silenciosamente "quem cadastrou".
         */
        static PessoaResumo dePessoa(Pessoa p, String textoFallback) {
            if (p != null) return new PessoaResumo(p.getId(), p.getNome());
            return textoFallback == null ? null : new PessoaResumo(null, textoFallback);
        }

        static PessoaResumo deUsuario(Usuario u, String textoFallback) {
            if (u != null) return new PessoaResumo(u.getId(), u.getPessoa().getNome());
            return textoFallback == null ? null : new PessoaResumo(null, textoFallback);
        }
    }

    public static EventoResponse from(Evento e) {
        return from(e, null);
    }

    public static EventoResponse from(Evento e, Integer inscricoesRemovidas) {
        return new EventoResponse(
                e.getId(), e.getTitulo(), e.getDescricao(),
                e.getInicioEm(), e.getFimEm(), LocalInfo.from(e), e.getTipo(),
                PessoaResumo.dePessoa(e.getResponsavel(), e.getResponsavelTexto()),
                PessoaResumo.deUsuario(e.getCriadoPor(), e.getCriadoPorTexto()),
                PessoaResumo.deUsuario(e.getAtualizadoPor(), e.getAtualizadoPorTexto()),
                e.getFoto() != null ? e.getFoto().getId() : null, e.getCreatedAt(),
                e.getVagas(), e.getPreco(), e.isExclusivoMembros(),
                e.isRequerInscricao(), e.isControlaPresenca(), e.getSituacao(), inscricoesRemovidas,
                e.getRecorteEtario(), e.getIdadeMin(), e.getIdadeMax(),
                e.getRestricaoEstadoCivil(), e.getRestricaoSexo()
        );
    }
}
