package com.domus.api.shared.armazenamento;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Guarda em memória. Existe para o teste não depender de rede nem de credencial. */
@Component
@ConditionalOnProperty(name = "app.fotos.armazenamento", havingValue = "memoria")
public class ArmazenamentoEmMemoria implements ArmazenamentoFotos {

    private final Map<String, byte[]> arquivos = new ConcurrentHashMap<>();

    @Override
    public void guardar(String chave, byte[] conteudo, String tipo) {
        arquivos.put(chave, conteudo);
    }

    @Override
    public byte[] ler(String chave) {
        byte[] b = arquivos.get(chave);
        if (b == null) throw new ArmazenamentoException("Chave não encontrada: " + chave, null);
        return b;
    }

    @Override
    public void remover(String prefixo) {
        arquivos.keySet().removeIf(k -> k.startsWith(prefixo));
    }

    /** Só para teste: quantos arquivos existem sob um prefixo. */
    public long contar(String prefixo) {
        return arquivos.keySet().stream().filter(k -> k.startsWith(prefixo)).count();
    }
}
