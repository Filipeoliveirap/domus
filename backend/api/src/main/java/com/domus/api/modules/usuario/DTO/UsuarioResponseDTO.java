package com.domus.api.modules.usuario.DTO;


import com.domus.api.modules.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String nome,
        String email,
        String role,
        boolean ativo,
        LocalDateTime ultimoLoginEm,
        // Convite ainda não aceito: usuário criado por convite que nunca fez login
        // (nem nativo nem Google). Derivado de ultimoLoginEm == null.
        boolean convitePendente,
        LocalDateTime criadoEm,
        UUID fotoId,
        /** Capacidades extras acumuladas (SECRETARIO, TESOUREIRO). */
        List<String> capacidadesExtras
) {
    /**
     * Construtor "de projeção", usado pela query JPQL de {@code UsuarioRepository.buscarPorIgreja}
     * (constructor expression) — recebe o {@code fotoId} já resolvido pela FK, sem passar pela
     * associação LAZY {@code Pessoa.foto}. {@code convitePendente} continua derivado aqui, não
     * na query, pra não duplicar a regra. Capacidades extras são carregadas separadamente.
     */
    public UsuarioResponseDTO(UUID id, String nome, String email, String role, boolean ativo,
                              LocalDateTime ultimoLoginEm, LocalDateTime criadoEm, UUID fotoId) {
        this(id, nome, email, role, ativo, ultimoLoginEm, ultimoLoginEm == null, criadoEm, fotoId, List.of());
    }

    /**
     * Usado nos pontos "um usuário só" (concessão/reativação de acesso, troca de status/role,
     * busca por id) — não há projeção em lote disponível ali, então lemos {@code Pessoa.getFoto()}
     * direto. Dentro de transação isso funciona e o custo (um SELECT lazy a mais) é desprezível
     * fora de listagem paginada; a listagem em si (que pagina até 20 itens e rodaria esse SELECT
     * uma vez por linha) usa a query com projeção de {@code buscarPorIgreja}, que evita isso.
     */
    public static UsuarioResponseDTO from(Usuario u) {
        return new UsuarioResponseDTO(
                u.getId(),
                u.getNome(),
                u.getEmail(),
                u.getRole().getNome(),
                u.isAtivo(),
                u.getUltimoLoginEm(),
                u.getUltimoLoginEm() == null,
                u.getCreatedAt(),
                u.getPessoa().getFoto() != null ? u.getPessoa().getFoto().getId() : null,
                List.of()
        );
    }
}
