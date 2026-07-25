package com.domus.api.modules.visitante;

import com.domus.api.modules.celula.CelulaMembro;
import com.domus.api.modules.celula.CelulaMembroRepository;
import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Endereco;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.visitante.DTOs.VisitanteRequest;
import com.domus.api.modules.visitante.DTOs.VisitanteResponse;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.util.TextoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VisitanteService {

    private final VisitanteRepository visitanteRepository;
    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CelulaRepository celulaRepository;
    private final CelulaMembroRepository celulaMembroRepository;

    @Transactional(readOnly = true)
    public PagedResponse<VisitanteResponse> listar(UUID igrejaId, String q,
                                                    Boolean contatoRealizado,
                                                    Boolean visitaRealizada,
                                                    Boolean acompanhamentoFeito,
                                                    Pageable pageable) {
        Page<VisitanteResponse> pagina = visitanteRepository.buscarPorIgreja(
                        igrejaId, q, contatoRealizado, visitaRealizada, acompanhamentoFeito, pageable)
                .map(VisitanteResponse::from);
        return PagedResponse.from(pagina);
    }

    @Transactional(readOnly = true)
    public VisitanteResponse buscarPorId(UUID id, UUID igrejaId) {
        return VisitanteResponse.from(buscarDaIgrejaOuFalhar(id, igrejaId));
    }

    @Transactional
    public VisitanteResponse criar(VisitanteRequest data, UUID igrejaId, UUID usuarioId) {
        validarNome(data.nome());
        validarFilhos(data.temFilhos(), data.quantidadeFilhos());

        var igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));
        var usuario = usuarioId != null ? usuarioRepository.findById(usuarioId).orElse(null) : null;

        Visitante visitante = Visitante.builder()
                .igreja(igreja)
                .nome(TextoUtil.capitalizar(data.nome()))
                .telefone(data.telefone())
                .sexo(data.sexo())
                .estadoCivil(data.estadoCivil())
                .dataNascimento(data.dataNascimento())
                .temFilhos(data.temFilhos())
                .quantidadeFilhos(data.temFilhos() ? data.quantidadeFilhos() : null)
                .observacoes(data.observacoes())
                .criadoPor(usuario)
                .atualizadoPor(usuario)
                .build();

        if (data.endereco() != null) {
            visitante.setEndereco(Endereco.builder()
                    .cep(data.endereco().cep())
                    .logradouro(data.endereco().logradouro())
                    .numero(data.endereco().numero())
                    .complemento(data.endereco().complemento())
                    .bairro(data.endereco().bairro())
                    .cidade(data.endereco().cidade())
                    .uf(data.endereco().uf())
                    .build());
        }

        return VisitanteResponse.from(visitanteRepository.save(visitante));
    }

    @Transactional
    public VisitanteResponse atualizar(UUID id, VisitanteRequest data, UUID igrejaId, UUID usuarioId) {
        validarNome(data.nome());
        validarFilhos(data.temFilhos(), data.quantidadeFilhos());

        Visitante visitante = buscarDaIgrejaOuFalhar(id, igrejaId);

        visitante.setNome(TextoUtil.capitalizar(data.nome()));
        visitante.setTelefone(data.telefone());
        visitante.setSexo(data.sexo());
        visitante.setEstadoCivil(data.estadoCivil());
        visitante.setDataNascimento(data.dataNascimento());
        visitante.setTemFilhos(data.temFilhos());
        visitante.setQuantidadeFilhos(data.temFilhos() ? data.quantidadeFilhos() : null);
        visitante.setObservacoes(data.observacoes());

        if (data.endereco() != null) {
            visitante.setEndereco(Endereco.builder()
                    .cep(data.endereco().cep())
                    .logradouro(data.endereco().logradouro())
                    .numero(data.endereco().numero())
                    .complemento(data.endereco().complemento())
                    .bairro(data.endereco().bairro())
                    .cidade(data.endereco().cidade())
                    .uf(data.endereco().uf())
                    .build());
        }

        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(visitante::setAtualizadoPor);
        }

        return VisitanteResponse.from(visitanteRepository.save(visitante));
    }

    @Transactional
    public void excluir(UUID id, UUID igrejaId) {
        Visitante visitante = buscarDaIgrejaOuFalhar(id, igrejaId);
        if (visitanteRepository.existeCelulaMembroAtivo(id)) {
            throw new BusinessException("VISITANTE_EM_CELULA",
                    "Não é possível excluir um visitante que está em uma célula.");
        }
        visitanteRepository.delete(visitante);
    }

    @Transactional
    public VisitanteResponse moverParaCelula(UUID id, UUID celulaId, UUID igrejaId) {
        Visitante visitante = buscarDaIgrejaOuFalhar(id, igrejaId);
        var celula = celulaRepository.findByIdAndIgrejaId(celulaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Célula não encontrada."));

        if (visitante.getEntrouEmCelulaEm() == null) {
            visitante.setEntrouEmCelulaEm(LocalDateTime.now());
        }

        CelulaMembro existente = celulaMembroRepository.findByVisitanteId(id).orElse(null);
        if (existente != null) {
            existente.setCelula(celula);
            celulaMembroRepository.save(existente);
        } else {
            celulaMembroRepository.save(CelulaMembro.builder()
                    .igreja(visitante.getIgreja()).celula(celula).visitante(visitante)
                    .build());
        }

        return VisitanteResponse.from(visitanteRepository.save(visitante));
    }

    @Transactional
    public VisitanteResponse toggleContato(UUID id, boolean valor, UUID igrejaId) {
        Visitante visitante = buscarDaIgrejaOuFalhar(id, igrejaId);
        visitante.setContatoRealizado(valor);
        return VisitanteResponse.from(visitanteRepository.save(visitante));
    }

    @Transactional
    public VisitanteResponse toggleVisita(UUID id, boolean valor, UUID igrejaId) {
        Visitante visitante = buscarDaIgrejaOuFalhar(id, igrejaId);
        visitante.setVisitaRealizada(valor);
        return VisitanteResponse.from(visitanteRepository.save(visitante));
    }

    @Transactional
    public VisitanteResponse toggleAcompanhamento(UUID id, boolean valor, UUID igrejaId) {
        Visitante visitante = buscarDaIgrejaOuFalhar(id, igrejaId);
        visitante.setAcompanhamentoFeito(valor);
        return VisitanteResponse.from(visitanteRepository.save(visitante));
    }

    private Visitante buscarDaIgrejaOuFalhar(UUID id, UUID igrejaId) {
        return visitanteRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Visitante não encontrado."));
    }

    private void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new BusinessException("NOME_OBRIGATORIO", "O nome do visitante é obrigatório.");
        }
    }

    private void validarFilhos(Boolean temFilhos, Integer quantidadeFilhos) {
        if (Boolean.TRUE.equals(temFilhos)) {
            if (quantidadeFilhos == null || quantidadeFilhos < 0) {
                throw new BusinessException("FILHOS_INVALIDO",
                        "Informe a quantidade de filhos (0 ou mais).");
            }
        }
    }
}
