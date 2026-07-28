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
        Integer inscricoesRemovidas,
        String recorteEtario,
        Integer idadeMin,
        Integer idadeMax,
        EstadoCivil restricaoEstadoCivil,
        Sexo restricaoSexo,
        IgrejaResumo igrejaOrganizadora,
        boolean podeGerenciarEsteEvento
) {
    public record LocalInfo(UUID id, String nome, String endereco, boolean enderecoHerdado) {
        static LocalInfo from(Evento e) {
            LocalEvento local = e.getLocal();
            if (local != null) {
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
        static PessoaResumo dePessoa(Pessoa p, String textoFallback) {
            if (p != null) return new PessoaResumo(p.getId(), p.getNome());
            return textoFallback == null ? null : new PessoaResumo(null, textoFallback);
        }

        static PessoaResumo deUsuario(Usuario u, String textoFallback) {
            if (u != null) return new PessoaResumo(u.getId(), u.getPessoa().getNome());
            return textoFallback == null ? null : new PessoaResumo(null, textoFallback);
        }
    }

    public record IgrejaResumo(UUID id, String nome, String sigla) {
        static IgrejaResumo de(com.domus.api.modules.igreja.Igreja igreja) {
            return new IgrejaResumo(igreja.getId(), igreja.getNome(), igreja.getSigla());
        }
    }

    public static EventoResponse from(Evento e, UUID minhaIgrejaId, boolean podeGerenciar) {
        return from(e, null, minhaIgrejaId, podeGerenciar);
    }

    public static EventoResponse from(Evento e, Integer inscricoesRemovidas,
                                       UUID minhaIgrejaId, boolean podeGerenciar) {
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
                e.getRestricaoEstadoCivil(), e.getRestricaoSexo(),
                IgrejaResumo.de(e.getIgreja()), podeGerenciar
        );
    }
}
