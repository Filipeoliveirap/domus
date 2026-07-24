package com.domus.api.modules.ministerio;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MinisterioMembroRepository extends JpaRepository<MinisterioMembro, UUID> {

    Optional<MinisterioMembro> findByMinisterioIdAndPessoaId(UUID ministerioId, UUID pessoaId);

    List<MinisterioMembro> findByMinisterioIdOrderByPapelAsc(UUID ministerioId);

    List<MinisterioMembro> findByPessoaIdAndIgrejaIdAndStatus(UUID pessoaId, UUID igrejaId, StatusMembro status);

    boolean existsByMinisterioIdAndPessoaIdAndPapelAndStatus(
            UUID ministerioId, UUID pessoaId, Papel papel, StatusMembro status);
}
