package com.domus.api.modules.pessoa;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.financeiro.movimentacao.busca.ReindexacaoMovimentacaoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.DTO.EnderecoDTO;
import com.domus.api.modules.pessoa.DTO.PessoaRequestDTO;
import com.domus.api.modules.pessoa.DTO.PessoaResponse;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import com.domus.api.modules.usuario.*;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PessoaService {

    private final PessoaRepository membroRepository;
    private final IgrejaRepository igrejaRepository;
    private final UsuarioService  usuarioService;
    private final CacheEvictor cacheEvictor;
    private final OutboxRegistrador outboxRegistrador;
    private final ReindexacaoMovimentacaoService  reindexacaoMovimentacaoService;

    @Transactional(readOnly = true)
    public java.util.List<String> listarBairros(UUID igrejaId) {
        return membroRepository.bairrosDistintos(igrejaId);
    }

    @Cacheable(
            value = "membros",
            key = "T(com.domus.api.config.redis.CacheKeys).membros(#igrejaId, #q, #pageable, #podeVerDadosSensiveis, #vinculo)"
    )
    @Transactional(readOnly = true)
    public PagedResponse<PessoaResponse> listarMembros(UUID igrejaId, String q, Pageable pageable,
                                                       boolean podeVerDadosSensiveis, Vinculo vinculo) {
        Page<PessoaResponse> pagina = membroRepository.buscarPorIgreja(igrejaId, q, vinculo, pageable)
                .map(m -> PessoaResponse.from(m, null, podeVerDadosSensiveis));
        return PagedResponse.from(pagina);
    }


    @Transactional
    public PessoaResponse cadastrarMembro(PessoaRequestDTO data, UUID igrejaId) {
        log.info("Iniciando cadastro de membro. nome={}, igreja_id={}", data.nome(), igrejaId);

        String email = normalizarEmail(data.email());

        if (email != null) {
            if (membroRepository.existsByEmail(email)) {
                throw new BusinessException("EMAIL_DUPLICADO", "E-mail já cadastrado no sistema.");
            }
            if (membroRepository.existsByEmailIncluindoArquivados(email)) {
                throw new BusinessException("EMAIL_ARQUIVADO",
                        "Este e-mail pertence a um cadastro arquivado. Use outro e-mail ou restaure o cadastro.");
            }
        }

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        Pessoa membro = Pessoa.builder()
                .igreja(igreja)
                .nome(normalizar(data.nome()))
                .email(email)
                .telefone(data.telefone())
                .dataNascimento(data.dataNascimento())
                .endereco(paraEndereco(data.endereco()))
                .vinculo(data.vinculo())
                .estadoCivil(data.estadoCivil())
                .ministerio(normalizar(data.ministerio()))
                .observacoes(data.observacoes())
                .dataBatismo(data.dataBatismo())
                .build();

        Pessoa salvo = membroRepository.save(membro);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MEMBRO,
                TipoEventoOutbox.CRIADO,
                salvo.getId(),
                igrejaId
        );
        log.info("Pessoa cadastrado. id={}, Igreja_id={}", salvo.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("membros", igrejaId);

        String aviso = avisoTelefoneDuplicado(salvo.getTelefone(), salvo.getId(), igrejaId);
        return PessoaResponse.from(salvo, aviso);
    }

    private String normalizarEmail(String email) {
        return (email == null || email.isBlank()) ? null : email.trim().toLowerCase();
    }

    @Transactional
    public PessoaResponse atualizarMembro(UUID id, PessoaRequestDTO data, UUID igrejaId) {
        log.info("Atualizando membro. id={}, igreja_id={}", id, igrejaId);
        Pessoa membro = membroRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));

        String nomeAntigo = membro.getNome();

        String emailNovo = normalizarEmail(data.email());
        if (emailNovo != null && !emailNovo.equals(membro.getEmail())) {

            if (membroRepository.existsByEmail(emailNovo)) {
                throw new BusinessException("EMAIL_DUPLICADO", "E-mail já cadastrado no sistema.");
            }
            if (membroRepository.existsByEmailIncluindoArquivados(emailNovo)) {
                throw new BusinessException("EMAIL_ARQUIVADO",
                        "Este e-mail pertence a um cadastro arquivado. Use outro e-mail.");
            }
        }

        membro.setNome(normalizar(data.nome()));
        membro.setEmail(emailNovo);
        membro.setTelefone(data.telefone());
        membro.setDataNascimento(data.dataNascimento());
        membro.setEndereco(paraEndereco(data.endereco()));
        membro.setVinculo(data.vinculo());
        membro.setEstadoCivil(data.estadoCivil());
        membro.setMinisterio(normalizar(data.ministerio()));
        membro.setObservacoes(data.observacoes());
        membro.setDataBatismo(data.dataBatismo());

        Pessoa salvo = membroRepository.save(membro);

        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MEMBRO,
                TipoEventoOutbox.ATUALIZADO,
                membro.getId(),
                igrejaId
        );

        boolean nomeMudou = !java.util.Objects.equals(nomeAntigo, membro.getNome());
        if (nomeMudou) {
            usuarioService.reindexarPorMembro(membro.getId(), igrejaId);
            reindexacaoMovimentacaoService.reindexarPorMembro(membro.getId(), igrejaId);
        }

        cacheEvictor.evictPorIgreja("membros", igrejaId);
        log.info("Pessoa atualizado. id={}, IgrejaId={}", salvo.getId(), igrejaId);

        String aviso = avisoTelefoneDuplicado(salvo.getTelefone(), salvo.getId(), igrejaId);
        return PessoaResponse.from(salvo, aviso);
    }

    /**
     * B2: procura OUTRO membro da mesma igreja com o mesmo telefone (dígitos normalizados) e
     * devolve o NOME dele para o front avisar — nunca bloqueia. Telefone não é chave de login
     * como o e-mail; casal, família e idoso usando o número de um parente são casos legítimos,
     * então duplicidade aqui é só um alerta ("confira se não é a mesma pessoa duas vezes"),
     * nunca um erro de negócio.
     *
     * <p>Isolado por {@code igrejaId} (nunca cruza tenant) e exclui o próprio {@code pessoaId}
     * sendo salvo, senão toda atualização "acharia" a si mesma como duplicata.
     */
    private String avisoTelefoneDuplicado(String telefone, UUID pessoaId, UUID igrejaId) {
        String digitos = com.domus.api.shared.util.TextoUtil.somenteDigitos(telefone);
        if (digitos == null) return null;

        return membroRepository.findByIgrejaIdAndTelefoneIsNotNull(igrejaId).stream()
                .filter(outro -> !outro.getId().equals(pessoaId))
                .filter(outro -> digitos.equals(
                        com.domus.api.shared.util.TextoUtil.somenteDigitos(outro.getTelefone())))
                .map(Pessoa::getNome)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void arquivarMembro(UUID id, UUID igrejaId) {
        Pessoa membro = membroRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));

        usuarioService.arquivarPorMembro(membro.getId(), igrejaId);
        membroRepository.delete(membro);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MEMBRO,
                TipoEventoOutbox.REMOVIDO,
                membro.getId(),
                igrejaId
        );
        cacheEvictor.evictPorIgreja("membros", igrejaId);
    }

    @Transactional(readOnly = true)
    public PessoaResponse buscarPorId(UUID id, UUID igrejaId, boolean podeVerDadosSensiveis) {
        Pessoa membro = membroRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));
        return PessoaResponse.from(membro, null, podeVerDadosSensiveis);
    }

    private Endereco paraEndereco(EnderecoDTO dto) {
        if (dto == null) return null;
        return Endereco.builder()
                .cep(dto.cep()).logradouro(normalizar(dto.logradouro())).numero(dto.numero())
                .complemento(dto.complemento())
                .bairro(normalizar(dto.bairro()))
                .cidade(normalizar(dto.cidade()))
                .uf(dto.uf())
                .build();
    }

    static String normalizar(String v) {
        return com.domus.api.shared.util.TextoUtil.capitalizar(v);
    }
}