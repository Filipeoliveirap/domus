package com.domus.api.modules.foto;

/** Versões servidas. O original é guardado mas nunca servido. */
public enum TamanhoFoto {
    /** 1200px no maior lado — banner, detalhe, post. */
    DISPLAY(1200),
    /** 200px no maior lado — avatar em lista. */
    THUMB(200);

    private final int ladoMaximo;

    TamanhoFoto(int ladoMaximo) { this.ladoMaximo = ladoMaximo; }

    public int getLadoMaximo() { return ladoMaximo; }

    /** Sufixo da chave no bucket: `{chave}/display.webp`. */
    public String sufixo() { return name().toLowerCase() + ".webp"; }
}
