package com.domus.api.shared.armazenamento;

/** Trocável por provedor (hoje R2, testes em memória). Banco guarda só a chave — nunca URL, senão trocar de provedor vira migration. */
public interface ArmazenamentoFotos {

    void guardar(String chave, byte[] conteudo, String tipo);

    /** @throws ArmazenamentoException se a chave não existe ou o provedor falhou. */
    byte[] ler(String chave);

    /** Remove tudo sob o prefixo (as três versões de uma foto). Idempotente. */
    void remover(String prefixo);
}
