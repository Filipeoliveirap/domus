package com.domus.api.modules.evento.convite;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.SituacaoEvento;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
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
    /** Separador entre eventoId e pessoaId dentro do valor guardado no Redis. */
    private static final String SEPARADOR = ":";
    /** Margem quando o evento não tem fim declarado (usa início + margem como referência de encerramento/TTL). */
    private static final Duration MARGEM_SEM_FIM = Duration.ofHours(6);
    private static final Duration TTL_MINIMO = Duration.ofHours(1);
    private final SecureRandom secureRandom = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final EventoRepository eventoRepository;
    private final PessoaRepository pessoaRepository;

    /** Convidante (quem clicou "Compartilhar") NÃO precisa estar inscrito no evento — decisão
     *  do brainstorm: mesmo respondendo "não vou participar", ela consegue gerar e compartilhar
     *  o link. O convite mapeia (evento, pessoa) diretamente, nunca uma InscricaoEvento. */
    public String gerarToken(UUID eventoId, UUID pessoaId, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        Pessoa convidante = pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));

        String token = gerarTokenAleatorio();
        Duration ttl = calcularTtl(evento);
        redisTemplate.opsForValue().set(chave(token), evento.getId() + SEPARADOR + convidante.getId(), ttl);
        return token;
    }

    /** Resolve o token pro evento e a pessoa que convidou — valida existência/expiração
     *  (404, "convite inválido") e evento já encerrado (EVENTO_ENCERRADO). */
    public ConviteResolvido resolver(String token) {
        String valor = redisTemplate.opsForValue().get(chave(token));
        if (valor == null) {
            throw new ResourceNotFoundException("Este convite não é mais válido.");
        }

        String[] partes = valor.split(SEPARADOR, 2);
        UUID eventoId = UUID.fromString(partes[0]);
        UUID pessoaId = UUID.fromString(partes[1]);

        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new ResourceNotFoundException("Este convite não é mais válido."));
        Pessoa convidante = pessoaRepository.findById(pessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Este convite não é mais válido."));

        if (evento.getSituacao() == SituacaoEvento.ENCERRADO) {
            throw new BusinessException("EVENTO_ENCERRADO", "Este evento já aconteceu.");
        }

        return new ConviteResolvido(evento, convidante);
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

    public record ConviteResolvido(Evento evento, Pessoa convidante) {}
}
