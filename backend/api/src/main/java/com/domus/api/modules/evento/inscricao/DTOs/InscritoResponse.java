package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.pessoa.Pessoa;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Uma linha da lista de inscritos (ADMIN/LÍDER). */
public record InscritoResponse(
        UUID id,
        UUID pessoaId,
        String nome,
        UUID fotoId,
        boolean pessoaRemovida,
        /** NULL = a pessoa se inscreveu sozinha. */
        UUID inscritoPorUsuarioId,
        /** NULL também quando a conta de quem inscreveu foi arquivada depois — front não inventa nome. */
        String inscritoPorNome,
        UUID inscritoPorFotoId,
        /** Preenchido só pra convidado sem cadastro (ver {@link InscricaoEvento#isConvidadoSemCadastro}). */
        String convidadoPorNome,
        LocalDateTime inscritoEm,
        List<AcompanhanteResponse> acompanhantes,
        EventoResponse.IgrejaResumo igrejaDaPessoa
) {
    private static final String NOME_PESSOA_REMOVIDA = "Pessoa removida do sistema";

    /**
     * @param pessoaResolvida resolvida em lote pelo chamador via bypass do @SQLRestriction
     *                        (nunca {@code i.getPessoa()} direto — pessoa arquivada, mas não
     *                        excluída, ainda mostra os dados reais; NULL só quando excluída
     *                        de vez OU quando é convidado sem cadastro).
     * @param registrante     resumo já resolvido em lote pelo chamador; NULL nos mesmos casos.
     * @param convidadoPorResolvida resolvida em lote (mesmo motivo de pessoaResolvida); NULL
     *                        quando não há convidante (Pessoa cadastrada, ou cadastro avulso
     *                        sem host).
     */
    public static InscritoResponse from(InscricaoEvento i, Pessoa pessoaResolvida,
                                         RegistranteResumo registrante, Pessoa convidadoPorResolvida) {
        boolean pessoaRemovida = pessoaResolvida == null && i.getNomeConvidado() == null;
        String nome = pessoaResolvida != null ? pessoaResolvida.getNome()
                : i.getNomeConvidado() != null ? i.getNomeConvidado()
                : NOME_PESSOA_REMOVIDA;

        return new InscritoResponse(
                i.getId(),
                pessoaResolvida == null ? null : pessoaResolvida.getId(),
                nome,
                pessoaResolvida != null && pessoaResolvida.getFoto() != null ? pessoaResolvida.getFoto().getId() : null,
                pessoaRemovida,
                i.getInscritoPorUsuarioId(),
                registrante == null ? null : registrante.nome(),
                registrante == null ? null : registrante.fotoId(),
                convidadoPorResolvida == null ? null : convidadoPorResolvida.getNome(),
                i.getCreatedAt(),
                i.getAcompanhantes().stream().map(AcompanhanteResponse::from).toList(),
                EventoResponse.IgrejaResumo.de(pessoaResolvida != null ? pessoaResolvida.getIgreja() : i.getIgreja())
        );
    }
}
