package com.domus.api.modules.evento.convite;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.SituacaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConviteService {

    private static final String PREFIXO = "convite:";
    /** Margem quando o evento não tem fim declarado (usa início + margem como referência de encerramento/TTL). */
    private static final Duration MARGEM_SEM_FIM = Duration.ofHours(6);
    private static final Duration TTL_MINIMO = Duration.ofHours(1);
    private final SecureRandom secureRandom = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;

    /** Gera um token novo (não idempotente) pra inscrição de {@code pessoaId} no evento. */
    public String gerarToken(UUID eventoId, UUID pessoaId, UUID igrejaId) {
        InscricaoEvento inscricao = inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)
                .filter(i -> i.getIgreja().getId().equals(igrejaId))
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));

        String token = gerarTokenAleatorio();
        Duration ttl = calcularTtl(inscricao.getEvento());
        redisTemplate.opsForValue().set(chave(token), inscricao.getId().toString(), ttl);
        return token;
    }

    /** Resolve o token e devolve a inscrição de quem convidou — valida existência/expiração
     *  (CONVITE_INVALIDO), inscrição cancelada (CONVITE_INVALIDO) e evento já encerrado
     *  (EVENTO_ENCERRADO). */
    public InscricaoEvento resolverInscricaoConvidante(String token) {
        String inscricaoIdTexto = redisTemplate.opsForValue().get(chave(token));
        if (inscricaoIdTexto == null) {
            throw new ResourceNotFoundException("Este convite não é mais válido.");
        }

        InscricaoEvento inscricao = inscricaoRepository.findById(UUID.fromString(inscricaoIdTexto))
                .orElseThrow(() -> new ResourceNotFoundException("Este convite não é mais válido."));

        if (inscricao.getStatus() != StatusInscricao.CONFIRMADA) {
            throw new ResourceNotFoundException("Este convite não é mais válido.");
        }
        if (inscricao.getEvento().getSituacao() == SituacaoEvento.ENCERRADO) {
            throw new BusinessException("EVENTO_ENCERRADO", "Este evento já aconteceu.");
        }

        return inscricao;
    }

    private Duration calcularTtl(Evento evento) {
        LocalDateTime referencia = evento.getFimEm() != null
                ? evento.getFimEm()
                : evento.getInicioEm().plus(MARGEM_SEM_FIM);
        Duration ate = Duration.between(LocalDateTime.now(), referencia);
        return ate.compareTo(TTL_MINIMO) > 0 ? ate : TTL_MINIMO;
    }

    private String gerarTokenAleatorio() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String chave(String token) {
        return PREFIXO + token;
    }
}
