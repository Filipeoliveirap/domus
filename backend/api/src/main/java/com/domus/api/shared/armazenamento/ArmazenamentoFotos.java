package com.domus.api.shared.armazenamento;

/**
 * Onde os bytes da foto ficam. Trocável por desenho (mesmo motivo do EmailService):
 * hoje R2, amanhã outro provedor, e nos testes memória.
 *
 * <p>O banco guarda apenas a CHAVE — nunca uma URL. Guardar URL acoplaria o dado ao
 * provedor, e trocar de provedor viraria migration.
 */
public interface ArmazenamentoFotos {

    void guardar(String chave, byte[] conteudo, String tipo);

    /** @throws ArmazenamentoException se a chave não existe ou o provedor falhou. */
    byte[] ler(String chave);

    /** Remove tudo sob o prefixo (as três versões de uma foto). Idempotente. */
    void remover(String prefixo);
}
