package com.domus.api.modules.evento.elegibilidade;

import com.domus.api.shared.exception.BusinessException;

import java.util.List;

/**
 * Carrega a lista de {@link Impedimento} até o {@code GlobalExceptionHandler}, que responde
 * 422 (não 400 genérico) — a inscrição não foi rejeitada por dado inválido, foi rejeitada
 * porque a PESSOA não é elegível para o evento. O front usa {@link #getImpedimentos()} para
 * mostrar exatamente o que barrou, com o mesmo código que o {@code GET .../elegibilidade}
 * já teria mostrado antes de tentar.
 */
public class NaoElegivelException extends BusinessException {

    // Mensagem genérica para quem NÃO gerencia inscrições: o 422 de elegibilidade é
    // acionável (ACESSO_COMUM pode chamar POST .../inscricoes/pessoas com um pessoaId
    // arbitrário da igreja e ler o resultado), então nome e idade de terceiro NUNCA podem
    // sair aqui para quem não tem acesso à lista de pessoas. Quem gerencia continua vendo a
    // mensagem detalhada (Regra 2 do InscricaoService já lhe dá acesso à decisão de contornar).
    private static final String MENSAGEM_GENERICA = "Esta pessoa não atende aos requisitos deste evento.";

    private final List<Impedimento> impedimentos;

    public NaoElegivelException(List<Impedimento> impedimentos) {
        super("NAO_ELEGIVEL", mensagemDe(impedimentos));
        this.impedimentos = impedimentos;
    }

    private NaoElegivelException(List<Impedimento> impedimentos, String mensagem) {
        super("NAO_ELEGIVEL", mensagem);
        this.impedimentos = impedimentos;
    }

    /**
     * @param podeVerDetalhes {@code false} para quem não gerencia inscrições: tanto a
     *                        mensagem quanto CADA {@link Impedimento} da lista (que também
     *                        vai no JSON de resposta, ver {@code ErrorResponse.ofElegibilidade})
     *                        são trocados pela versão genérica — sanitizar só a mensagem de
     *                        topo e deixar nome/idade vazando na lista seria meio furo.
     */
    public static NaoElegivelException para(List<Impedimento> impedimentos, boolean podeVerDetalhes) {
        if (podeVerDetalhes) {
            return new NaoElegivelException(impedimentos);
        }
        List<Impedimento> genericos = impedimentos.stream()
                .map(i -> new Impedimento(i.codigo(), MENSAGEM_GENERICA, i.contornavel()))
                .toList();
        return new NaoElegivelException(genericos, MENSAGEM_GENERICA);
    }

    public List<Impedimento> getImpedimentos() {
        return impedimentos;
    }

    private static String mensagemDe(List<Impedimento> impedimentos) {
        return impedimentos.stream()
                .map(Impedimento::mensagem)
                .reduce((a, b) -> a + " " + b)
                .orElse("Esta pessoa não é elegível para este evento.");
    }
}
