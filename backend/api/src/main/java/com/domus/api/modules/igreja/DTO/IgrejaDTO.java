package com.domus.api.modules.igreja.DTO;

import com.domus.api.modules.igreja.Igreja;

public record IgrejaDTO(
        String nome,
        String cnpj,
        String email,
        String telefone

) {

    public static IgrejaDTO from(Igreja igreja){
        return new IgrejaDTO(
                igreja.getNome(),
                igreja.getCnpj(),
                igreja.getEmailContato(),
                igreja.getTelefoneContato()
        );
    }
}
