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
                String endereco = local.temEnderecoProprio()
                        ? local.getCepLogradouroNumero()
                        : null;
                return new LocalInfo(local.getId(), local.getNome(), endereco, !local.temEnderecoProprio());
            }
            if (e.getLocalTexto() != null) {
                return new LocalInfo(null, e.getLocalTexto(), null, false);
            }
            return null;
        }
    }

    public record PessoaResumo(UUID id, String nome) {
        static PessoaResumo dePessoa(Pessoa p) {
            return p == null ? null : new PessoaResumo(p.getId(), p.getNome());
        }

        static PessoaResumo deUsuario(Usuario u) {
            return u == null ? null : new PessoaResumo(u.getId(), u.getPessoa().getNome());
        }
    }

    public static EventoResponse from(Evento e) {
        return from(e, null);
    }

    public static EventoResponse from(Evento e, Integer inscricoesRemovidas) {
        return new EventoResponse(
                e.getId(), e.getTitulo(), e.getDescricao(),
                e.getInicioEm(), e.getFimEm(), LocalInfo.from(e), e.getTipo(),
                PessoaResumo.dePessoa(e.getResponsavel()),
                PessoaResumo.deUsuario(e.getCriadoPor()),
                PessoaResumo.deUsuario(e.getAtualizadoPor()),
                e.getFoto() != null ? e.getFoto().getId() : null, e.getCreatedAt(),
                e.getVagas(), e.getPreco(), e.isExclusivoMembros(),
                e.isRequerInscricao(), e.getSituacao(), inscricoesRemovidas,
                e.getRecorteEtario(), e.getIdadeMin(), e.getIdadeMax(),
                e.getRestricaoEstadoCivil(), e.getRestricaoSexo()
        );
    }
}
