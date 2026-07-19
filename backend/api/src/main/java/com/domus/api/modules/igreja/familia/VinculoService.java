package com.domus.api.modules.igreja.familia;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.familia.DTO.VinculoDTOs.*;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.AcessoNegadoException;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Formação e desmanche da família de igrejas.
 *
 * <p>A direção do consentimento é o que sustenta o desenho: quem se expõe é a <b>filha</b>
 * (o financeiro dela é que aparece para a mãe), então é a filha quem digita o código. A mãe
 * apenas <b>gera</b> o código — ou seja, consente em receber. Se a mãe pudesse declarar
 * "aquela é minha congregação", qualquer igreja leria financeiro alheio.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VinculoService {

    private final IgrejaRepository igrejaRepository;
    private final FamiliaIgrejaService familiaService;
    private final CodigoVinculoGenerator gerador;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public VinculoStatusResponse status(UUID igrejaId) {
        Igreja igreja = buscar(igrejaId);

        Igreja mae = igreja.getIgrejaMae();
        if (mae != null) {
            // A filha vê a mãe e desde quando está na família — mas nunca as irmãs.
            return new VinculoStatusResponse(
                    EstadoVinculo.FILHA, null, null, resumir(mae, igreja.getVinculadoEm()), List.of());
        }

        var congregacoes = familiaService.filhasDe(igrejaId).stream()
                .map(f -> resumir(f, f.getVinculadoEm()))
                .toList();

        EstadoVinculo estado = congregacoes.isEmpty() ? EstadoVinculo.INDEPENDENTE : EstadoVinculo.MAE;
        return new VinculoStatusResponse(
                estado, igreja.getCodigoVinculo(), igreja.getCodigoGeradoEm(), null, congregacoes);
    }

    /**
     * Gera (ou rotaciona) o código. Rotacionar invalida o anterior — é a defesa contra
     * vazamento do código, junto com desvincular. Não há expiração nem uso único: o código
     * é reutilizável porque a mãe tem várias congregações.
     *
     * <p>Gerar código <b>não</b> torna a igreja mãe: ela segue INDEPENDENTE até a primeira
     * filha entrar, e ainda pode desistir e entrar na família de outra.
     */
    @Transactional
    public String gerarCodigo(UUID igrejaId) {
        Igreja igreja = buscar(igrejaId);

        if (igreja.getIgrejaMae() != null) {
            throw new BusinessException("JA_EH_FILHA",
                    "Sua igreja é uma congregação e não pode ter congregações próprias.");
        }

        // Falha explícita em vez de salvar um código colidido e estourar 500 na UNIQUE.
        String codigo = null;
        for (int tentativa = 0; tentativa < 10 && codigo == null; tentativa++) {
            String candidato = gerador.gerar();
            if (igrejaRepository.findByCodigoVinculo(candidato).isEmpty()) {
                codigo = candidato;
            }
        }
        if (codigo == null) {
            throw new BusinessException("CODIGO_INDISPONIVEL",
                    "Não foi possível gerar um código agora. Tente novamente.");
        }

        igreja.setCodigoVinculo(codigo);
        igreja.setCodigoGeradoEm(LocalDateTime.now());
        igrejaRepository.save(igreja);
        log.info("Código de vínculo gerado. igreja_id={}", igrejaId);
        return codigo;
    }

    /**
     * A filha entra na família digitando o código da mãe.
     *
     * <p>As quatro recusas, todas aqui no serviço (sem trigger no banco — a consulta da
     * família não é recursiva, então um ciclo hipotético não derrubaria nada).
     */
    @Transactional
    public VinculoStatusResponse entrarNaFamilia(UUID igrejaId, UUID usuarioId, String codigoDigitado) {
        String codigo = gerador.normalizar(codigoDigitado);
        if (codigo == null) {
            throw new BusinessException("CODIGO_INVALIDO", "Código de vínculo inválido.");
        }

        // 1. O código existe?
        UUID maeId = igrejaRepository.findByCodigoVinculo(codigo)
                .map(Igreja::getId)
                .orElseThrow(() -> new BusinessException("CODIGO_INVALIDO", "Código de vínculo inválido."));

        // 2. É ela mesma? (auto-vínculo)
        if (maeId.equals(igrejaId)) {
            throw new BusinessException("AUTO_VINCULO",
                    "Você não pode vincular sua igreja a ela mesma.");
        }

        /*
         * TRAVA AS DUAS LINHAS ANTES DE VALIDAR.
         *
         * Sem isto há uma corrida que quebra a regra dos 2 níveis: se A e B trocarem códigos
         * e os dois admins clicarem "entrar" ao mesmo tempo, cada transação lê o estado antigo
         * da outra, as quatro validações passam nas duas, e o commit deixa A.mae=B E B.mae=A.
         *
         * O ciclo não trava nada (a consulta não é recursiva), mas destrói a autorização:
         * pertenceAFamilia() passa a aprovar NOS DOIS SENTIDOS e cada igreja lê o financeiro
         * da outra — exatamente o que esta feature existe para impedir.
         *
         * A ordem por UUID é o que evita deadlock: duas transações que travam as mesmas duas
         * linhas em ordens opostas se bloqueiam mutuamente. Ordenando, elas serializam.
         */
        UUID primeiro = igrejaId.compareTo(maeId) <= 0 ? igrejaId : maeId;
        UUID segundo  = igrejaId.compareTo(maeId) <= 0 ? maeId : igrejaId;
        travar(primeiro);
        travar(segundo);

        // Reler DEPOIS do lock: só agora o estado é confiável para decidir.
        Igreja mae = buscar(maeId);
        Igreja filha = buscar(igrejaId);

        // O código pode ter sido rotacionado entre a busca e o lock.
        if (!codigo.equals(mae.getCodigoVinculo())) {
            throw new BusinessException("CODIGO_INVALIDO", "Código de vínculo inválido.");
        }

        // 3. A dona do código já tem mãe? (violaria os 2 níveis)
        if (mae.getIgrejaMae() != null) {
            // Mensagem propositalmente igual à de código inexistente: distinguir as duas
            // transformaria o endpoint num oráculo sobre igrejas de terceiros.
            throw new BusinessException("CODIGO_INVALIDO", "Código de vínculo inválido.");
        }

        // 4. Quem está entrando já tem filhas? (violaria os 2 níveis)
        if (familiaService.ehMae(filha.getId())) {
            throw new BusinessException("JA_EH_MAE",
                    "Sua igreja já tem congregações vinculadas e não pode virar congregação de outra.");
        }

        if (filha.getIgrejaMae() != null) {
            throw new BusinessException("JA_VINCULADA",
                    "Sua igreja já faz parte de uma família. Saia dela antes de entrar em outra.");
        }

        filha.setIgrejaMae(mae);
        // Uma congregação não tem congregações — o código dela, se existir, perde o sentido.
        filha.setCodigoVinculo(null);
        filha.setCodigoGeradoEm(null);
        filha.setVinculadoEm(LocalDateTime.now());
        // getReferenceById devolve um proxy: grava a FK sem carregar o usuário do banco.
        filha.setVinculadoPor(usuarioRepository.getReferenceById(usuarioId));
        igrejaRepository.save(filha);

        log.info("Vínculo criado. filha_igreja_id={}, mae_igreja_id={}, por_usuario_id={}",
                filha.getId(), mae.getId(), usuarioId);
        return status(igrejaId);
    }

    /**
     * A mãe remove uma congregação da família.
     * Valida que a alvo é realmente filha de quem pediu — senão seria IDOR de escrita.
     */
    @Transactional
    public void desvincularCongregacao(UUID maeId, UUID filhaId) {
        Igreja filha = buscar(filhaId);

        if (filha.getIgrejaMae() == null || !filha.getIgrejaMae().getId().equals(maeId)) {
            log.warn("Tentativa de desvincular igreja de outra família. solicitante_igreja_id={}, alvo_igreja_id={}",
                    maeId, filhaId);
            throw new AcessoNegadoException();
        }

        limparVinculo(filha);
        igrejaRepository.save(filha);
        log.info("Vínculo desfeito pela mãe. filha_igreja_id={}, mae_igreja_id={}", filhaId, maeId);
    }

    /**
     * A congregação sai da família por conta própria. Quem consente pode revogar — a filha
     * expôs o financeiro dela por consentimento; se só a mãe pudesse desfazer, seria uma
     * armadilha (entra fácil, nunca mais sai).
     */
    @Transactional
    public void sairDaFamilia(UUID igrejaId) {
        Igreja filha = buscar(igrejaId);

        if (filha.getIgrejaMae() == null) {
            throw new BusinessException("SEM_VINCULO", "Sua igreja não faz parte de nenhuma família.");
        }

        UUID maeId = filha.getIgrejaMae().getId();
        limparVinculo(filha);
        igrejaRepository.save(filha);
        log.info("Vínculo desfeito pela filha. filha_igreja_id={}, mae_igreja_id={}", igrejaId, maeId);
    }

    /** Sair da família apaga também os metadados — senão sobrariam mentindo sobre um vínculo morto. */
    private void limparVinculo(Igreja filha) {
        filha.setIgrejaMae(null);
        filha.setVinculadoEm(null);
        filha.setVinculadoPor(null);
    }

    /** Endereço é opcional, então cidade/uf podem vir nulos — a tela trata. */
    private IgrejaResumo resumir(Igreja igreja, LocalDateTime vinculadoEm) {
        var endereco = igreja.getEndereco();
        return new IgrejaResumo(
                igreja.getId(),
                igreja.getNome(),
                endereco != null ? endereco.getCidade() : null,
                endereco != null ? endereco.getUf() : null,
                vinculadoEm);
    }

    private void travar(UUID igrejaId) {
        igrejaRepository.buscarComLock(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada"));
    }

    private Igreja buscar(UUID igrejaId) {
        return igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada"));
    }
}
