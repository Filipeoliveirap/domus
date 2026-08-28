package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.pessoa.Pessoa;
import java.time.LocalDateTime;
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
        /** Preenchido só pra convidado sem cadastro — telefone que ele mesmo (ou quem o
         *  cadastrou) informou; NULL pra pessoa com cadastro (usa {@link #telefonePessoa}). */
        String telefoneConvidado,
        /** Preenchido só pra pessoa com cadastro — telefone do próprio cadastro; NULL pra
         *  convidado sem cadastro (usa {@link #telefoneConvidado}) e pra pessoa removida. */
        String telefonePessoa,
        /** Preenchido só pra pessoa com cadastro; NULL pra convidado sem cadastro e pra
         *  pessoa removida. Convidado sem cadastro não tem e-mail próprio nesta lista — só
         *  o e-mail de comprovante de pagamento, que não é exibido aqui. */
        String emailPessoa,
        LocalDateTime inscritoEm,
        boolean compareceu,
        EventoResponse.IgrejaResumo igrejaDaPessoa,
        /** CONFIRMADA ou AGUARDANDO_PAGAMENTO — a lista agora inclui as duas (ver
         *  InscricaoRepository.listarIdsPaginadoPorEvento), então o front precisa saber
         *  qual é qual pra mostrar a tag "Pagamento pendente". */
        StatusInscricao status,
        /** {@code true} quando AGUARDANDO_PAGAMENTO mas já existe pelo menos uma cobrança
         *  PAGO dessa inscrição — ou seja, a pessoa JÁ pagou o valor original e só falta a
         *  diferença de um reajuste de preço (ver InscricaoService.aplicarMudancaValorPago),
         *  bem diferente de quem nunca pagou nada. Sempre {@code false} fora de
         *  AGUARDANDO_PAGAMENTO. Front usa isso pra escolher entre a tag "Pagamento
         *  pendente" e "Falta complementar" (2026-08-27). */
        boolean pagamentoParcial,
        /** ID da {@code CobrancaEvento} com estorno pendente desta inscrição, ou {@code null}
         *  quando não há nenhuma (2026-08-27). Pode existir em CONFIRMADA (reajuste de preço
         *  pra baixo cujo excedente falhou ao devolver) ou em AGUARDANDO_PAGAMENTO/CANCELADA
         *  (cancelamento/estorno em massa que falhou) — por isso não depende do status, ao
         *  contrário de {@link #pagamentoParcial}. Front usa pra mostrar a tag "Estorno
         *  pendente" com botão de tentar de novo (POST /cobrancas/{id}/tentar-estorno-novamente). */
        UUID cobrancaEstornoPendenteId
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
     * @param pagamentoParcial resolvido em lote pelo chamador (ver
     *                        {@code CobrancaEventoRepository.findInscricaoIdsComCobrancaPaga}).
     * @param cobrancaEstornoPendenteId resolvido em lote pelo chamador (ver
     *                        {@code CobrancaEventoRepository.findByInscricaoIdInAndEstornoPendenteTrue});
     *                        {@code null} quando não há estorno pendente pra esta inscrição.
     */
    public static InscritoResponse from(InscricaoEvento i, Pessoa pessoaResolvida,
                                         RegistranteResumo registrante, Pessoa convidadoPorResolvida,
                                         boolean pagamentoParcial, UUID cobrancaEstornoPendenteId) {
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
                i.getTelefoneConvidado(),
                pessoaResolvida != null ? pessoaResolvida.getTelefone() : null,
                pessoaResolvida != null ? pessoaResolvida.getEmail() : null,
                i.getCreatedAt(),
                i.isCompareceu(),
                EventoResponse.IgrejaResumo.de(pessoaResolvida != null ? pessoaResolvida.getIgreja() : i.getIgreja()),
                i.getStatus(),
                i.getStatus() == StatusInscricao.AGUARDANDO_PAGAMENTO && pagamentoParcial,
                cobrancaEstornoPendenteId
        );
    }
}
