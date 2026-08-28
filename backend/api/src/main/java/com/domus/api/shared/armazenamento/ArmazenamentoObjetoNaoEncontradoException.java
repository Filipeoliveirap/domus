package com.domus.api.shared.armazenamento;

/** Lançada só quando o storage confirma que o objeto não existe — nunca pra falha de rede/autenticação. */
public class ArmazenamentoObjetoNaoEncontradoException extends ArmazenamentoException {
    public ArmazenamentoObjetoNaoEncontradoException(String chave) {
        super("Objeto não encontrado: " + chave, null);
    }
}
