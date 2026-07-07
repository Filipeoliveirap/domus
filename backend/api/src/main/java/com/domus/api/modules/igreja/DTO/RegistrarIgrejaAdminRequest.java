package com.domus.api.modules.igreja.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;



@Getter
@Setter
public class RegistrarIgrejaAdminRequest {
    @NotBlank(message = "Nome da igreja é obrigatório")
    @Size(max = 255, message = "Nome da igreja deve ter no máximo 255 caracteres")
    private String nomeIgreja;

    @NotBlank(message = "E-mail de contato é obrigatório")
    @Email(message = "E-mail de contato inválido")
    private String emailContato;

    @Size(max = 18, message = "CNPJ deve ter no máximo 18 caracteres")
    private String cnpj;

    @NotBlank(message = "Telefone para contato é obrigatório")
    @Pattern(
            regexp = "^\\d{10,11}$",
            message = "Telefone inválido. Informe DDD + número (10 ou 11 dígitos)"
    )
    private String telefoneContato;

    @NotBlank(message = "Nome do administrador é obrigatório")
    @Size(max = 255, message = "Nome do administrador deve ter no máximo 255 caracteres")
    private String nomeAdmin;

    @NotBlank(message = "Email do administrador é obrigatório")
    @Email(message = "Email do administrador inválido")
    private String emailAdmin;

    @NotBlank(message = "Senha do administrador é obrigatória")
    @Size(min = 8, message = "Senha do administrador deve ter no mínimo 8 caracteres")
    private String senhaAdmin;

}
