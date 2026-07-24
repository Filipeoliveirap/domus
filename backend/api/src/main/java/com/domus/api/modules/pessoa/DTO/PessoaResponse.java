package com.domus.api.modules.pessoa.DTO;

import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.Sexo;
import com.domus.api.modules.pessoa.Vinculo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PessoaResponse(
        UUID id,
        String nome,
        String email,
        String telefone,
        LocalDate dataNascimento,
        EnderecoDTO endereco,
        Vinculo vinculo,
        EstadoCivil estadoCivil,
        Sexo sexo,
        String cargo,
        UUID fotoId,
        String observacoes,
        LocalDateTime createdAt,
        LocalDate dataBatismo,
        String avisoTelefoneDuplicado
) {
    public static PessoaResponse from(Pessoa m) {
        return from(m, null);
    }

    /**
     * B2: {@code avisoTelefoneDuplicado} carrega o NOME de outro membro da mesma igreja com o
     * mesmo telefone — é AVISO, não bloqueio (telefone é legitimamente compartilhado: casal,
     * família, idoso que usa o número de um filho). Só é preenchido logo após
     * cadastrar/atualizar (ver {@code PessoaService}); a leitura normal ({@code from(Pessoa)})
     * não recalcula o aviso a cada GET.
     */
    public static PessoaResponse from(Pessoa m, String avisoTelefoneDuplicado) {
        return from(m, avisoTelefoneDuplicado, true);
    }

    /**
     * Resposta com ou sem os <b>dados sensíveis</b> do membro — hoje <b>endereço</b> e
     * <b>observações</b> (notas pastorais).
     *
     * <p><b>Por que aqui e não na tela:</b> esconder no front não esconde nada. Se o JSON traz
     * o campo, qualquer LÍDER abre o DevTools e lê o endereço da casa de todo mundo. A omissão
     * precisa acontecer antes de sair da API.
     *
     * <p><b>Ao adicionar campo novo em membro, decida a qual grupo ele pertence.</b> O padrão
     * seguro é sensível: o que é público já está visível em outras telas (nome, foto,
     * aniversário); o que não está, provavelmente não deveria vazar.
     */
    public static PessoaResponse from(Pessoa m, String avisoTelefoneDuplicado,
                                      boolean incluirDadosSensiveis) {
        return new PessoaResponse(
                m.getId(), m.getNome(), m.getEmail(), m.getTelefone(),
                m.getDataNascimento(),
                incluirDadosSensiveis ? enderecoDe(m.getEndereco()) : null,
                m.getVinculo(),
                m.getEstadoCivil(), m.getSexo(), m.getCargo(),
                m.getFoto() != null ? m.getFoto().getId() : null,
                incluirDadosSensiveis ? m.getObservacoes() : null,
                m.getCreatedAt(),
                m.getDataBatismo(), avisoTelefoneDuplicado
        );
    }

    private static EnderecoDTO enderecoDe(com.domus.api.modules.pessoa.Endereco e) {
        if (e == null) return null;
        return new EnderecoDTO(e.getCep(), e.getLogradouro(), e.getNumero(),
                e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf());
    }
}