package com.domus.api.modules.igreja.familia;

import com.domus.api.modules.igreja.CodigoConviteCongregacao;
import com.domus.api.modules.igreja.CodigoConviteRepository;
import com.domus.api.modules.igreja.CodigoConviteService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.StatusAssinatura;
import com.domus.api.modules.igreja.exception.PlanoLimiteExcedidoException;
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
import java.util.Optional;
import java.util.UUID;

/** Formação e desmanche da família de igrejas. A filha digita o código da mãe — a mãe apenas gera e consente em receber. */
@Service
@Slf4j
@RequiredArgsConstructor
public class VinculoService {

    private final IgrejaRepository igrejaRepository;
    private final FamiliaIgrejaService familiaService;
    private final CodigoVinculoGenerator gerador;
    private final UsuarioRepository usuarioRepository;
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
    private final CodigoConviteService codigoConviteService;
    private final CodigoConviteRepository codigoConviteRepository;
    private final com.domus.api.modules.usuario.PrincipalCacheService principalCacheService;

    /** Rótulo plural do agrupamento de igrejas na cópia visível: o padrão do front é "Unidades",
     *  mas a igreja pode ter customizado (ex.: "Congregações", "Redes"). Domínio, rotas e nomes de
     *  classe continuam "família"/"congregação" — só o texto que a pessoa lê acompanha o rótulo. */
    private static final String CONGREGACAO_PLURAL_PADRAO = "Unidades";

    private static String grupoDe(Igreja sede) {
        String custom = sede.getCongregacaoNomePlural();
        String plural = custom != null && !custom.isBlank() ? custom : CONGREGACAO_PLURAL_PADRAO;
        return "grupo de " + plural.toLowerCase();
    }

    @Transactional(readOnly = true)
    public VinculoStatusResponse status(UUID igrejaId) {
        Igreja igreja = buscar(igrejaId);

        Igreja mae = igreja.getIgrejaMae();
        if (mae != null) {
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

    /** Gera (ou rotaciona) o código de vínculo. Rotacionar invalida o anterior — defesa contra vazamento. Gerar código não torna a igreja mãe. */
    @Transactional
    public String gerarCodigo(UUID igrejaId) {
        Igreja igreja = buscar(igrejaId);

        if (igreja.getIgrejaMae() != null) {
            throw new BusinessException("JA_EH_FILHA",
                    "Sua igreja é uma congregação e não pode ter congregações próprias.");
        }

        String codigo = codigoConviteService.gerarCodigo(igreja).codigo();

        igreja.setCodigoVinculo(codigo);
        igreja.setCodigoGeradoEm(LocalDateTime.now());
        igrejaRepository.save(igreja);
        log.info("Código de vínculo gerado. igreja_id={}, codigo={}", igrejaId, codigo);
        return codigo;
    }

    /** A filha entra na família digitando o código da mãe. As validações de hierarquia são feitas aqui — sem trigger no banco. */
    @Transactional
    public VinculoStatusResponse entrarNaFamilia(UUID igrejaId, UUID usuarioId, String codigoDigitado) {
        String codigo = gerador.normalizar(codigoDigitado);
        if (codigo == null) {
            throw new BusinessException("CODIGO_INVALIDO", "Código de vínculo inválido.");
        }

        Optional<CodigoConviteCongregacao> conviteOpt = codigoConviteRepository.findByCodigo(codigo);
        UUID maeId;
        if (conviteOpt.isPresent()) {
            CodigoConviteCongregacao convite = conviteOpt.get();
            if (convite.getUsadoEm() != null) {
                throw new BusinessException("CODIGO_INVALIDO", "Código de vínculo inválido.");
            }
            maeId = convite.getMatriz().getId();
        } else {
            maeId = igrejaRepository.findByCodigoVinculo(codigo)
                    .map(Igreja::getId)
                    .orElseThrow(() -> new BusinessException("CODIGO_INVALIDO", "Código de vínculo inválido."));
        }

        if (maeId.equals(igrejaId)) {
            throw new BusinessException("AUTO_VINCULO",
                    "Você não pode vincular sua igreja a ela mesma.");
        }

        // Trava as duas linhas antes de validar: sem isso, dois vínculos concorrentes (A entra em B e B entra em A ao mesmo tempo) passam validação nos dois sentidos e quebram a regra dos 2 níveis. Ordem por UUID evita deadlock.
        UUID primeiro = igrejaId.compareTo(maeId) <= 0 ? igrejaId : maeId;
        UUID segundo  = igrejaId.compareTo(maeId) <= 0 ? maeId : igrejaId;
        travar(primeiro);
        travar(segundo);

        // Reler DEPOIS do lock: só agora o estado é confiável para decidir.
        Igreja mae = buscar(maeId);
        Igreja filha = buscar(igrejaId);

        // O código pode ter sido rotacionado entre a busca e o lock.
        if (conviteOpt.isEmpty() && !codigo.equals(mae.getCodigoVinculo())) {
            throw new BusinessException("CODIGO_INVALIDO", "Código de vínculo inválido.");
        }

        if (mae.getIgrejaMae() != null) {
            // Mensagem propositalmente igual à de código inexistente: distinguir as duas
            // transformaria o endpoint num oráculo sobre igrejas de terceiros.
            throw new BusinessException("CODIGO_INVALIDO", "Código de vínculo inválido.");
        }

        if (familiaService.ehMae(filha.getId())) {
            throw new BusinessException("JA_EH_MAE",
                    "Sua igreja já tem congregações vinculadas e não pode virar congregação de outra.");
        }

        if (filha.getIgrejaMae() != null) {
            throw new BusinessException("JA_VINCULADA",
                    "Sua igreja já faz parte de um grupo. Saia dele antes de entrar em outro.");
        }

        long congregacoesAtuais = igrejaRepository.countByIgrejaMaeId(mae.getId());
        if (mae.getPlano() != null && congregacoesAtuais >= mae.getPlano().getLimiteCongregacoes()) {
            throw new PlanoLimiteExcedidoException("A igreja matriz atingiu o limite de congregações do plano atual.");
        }

        filha.setIgrejaMae(mae);
        // Uma congregação não tem congregações — o código dela, se existir, perde o sentido.
        filha.setCodigoVinculo(null);
        filha.setCodigoGeradoEm(null);
        filha.setVinculadoEm(LocalDateTime.now());
        // getReferenceById devolve um proxy: grava a FK sem carregar o usuário do banco.
        filha.setVinculadoPor(usuarioRepository.getReferenceById(usuarioId));
        filha.setStatusAssinatura(StatusAssinatura.ATIVA);
        if (mae.getPlano() != null) {
            filha.setPlano(mae.getPlano());
        }
        igrejaRepository.save(filha);

        conviteOpt.ifPresent(c -> {
            c.setUsadoEm(LocalDateTime.now());
            c.setIgrejaFilha(filha);
            codigoConviteRepository.save(c);
        });

        List<com.domus.api.modules.usuario.Usuario> adminsDaSede = usuarioRepository
                .findByIgrejaIdAndRole_NomeAndAtivoTrue(mae.getId(),
                        com.domus.api.shared.security.Perfil.ADMIN_IGREJA.name());
        for (var admin : adminsDaSede) {
            notificacaoService.criar(
                    com.domus.api.modules.notificacao.TipoNotificacao.PEDIDO_VINCULO_FAMILIA,
                    mae.getId(), admin.getId(),
                    filha.getNome() + " pediu pra entrar no seu " + grupoDe(mae) + ".",
                    "/configuracoes/igrejas-vinculadas");
        }

        log.info("Vínculo criado. filha_igreja_id={}, mae_igreja_id={}, por_usuario_id={}",
                filha.getId(), mae.getId(), usuarioId);
        usuarioRepository.findIdsAtivosPorIgreja(filha.getId()).forEach(principalCacheService::invalidar);
        return status(igrejaId);
    }

    /** A mãe remove uma congregação. Valida que a alvo é filha de quem pediu. */
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

    /** A congregação sai da família. Quem consentiu pode revogar — ambos os lados podem desfazer o vínculo. */
    @Transactional
    public void sairDaFamilia(UUID igrejaId) {
        Igreja filha = buscar(igrejaId);

        if (filha.getIgrejaMae() == null) {
            throw new BusinessException("SEM_VINCULO", "Sua igreja não faz parte de nenhum grupo.");
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
        filha.setPlano(null);
        filha.setStatusAssinatura(com.domus.api.modules.igreja.StatusAssinatura.PAUSADA);
        filha.setMotivoSuspensao("DESVINCULADA_MATRIZ");
        filha.setMpPreapprovalId(null);
        usuarioRepository.findIdsAtivosPorIgreja(filha.getId()).forEach(principalCacheService::invalidar);
    }

    /** Endereço é opcional, então cidade/uf podem vir nulos — a tela trata. */
    private IgrejaResumo resumir(Igreja igreja, LocalDateTime vinculadoEm) {
        var endereco = igreja.getEndereco();
        LocalDateTime dataVinculo = vinculadoEm != null ? vinculadoEm : igreja.getCreatedAt();
        return new IgrejaResumo(
                igreja.getId(),
                igreja.getNome(),
                endereco != null ? endereco.getCidade() : null,
                endereco != null ? endereco.getUf() : null,
                dataVinculo);
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
