package com.domus.api.modules.evento.convite;

import com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoService;
import com.domus.api.modules.evento.convite.DTOs.ConvitePublicoResponse;
import com.domus.api.modules.evento.convite.DTOs.EntrarConviteRequest;
import com.domus.api.modules.evento.convite.DTOs.GerarConviteResponse;
import com.domus.api.modules.evento.convite.ConviteService.ConviteResolvido;
import com.domus.api.modules.evento.inscricao.DTOs.ConvidadoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.evento.local.DTOs.LocalEventoResponse;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.foto.TamanhoFoto;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class ConviteController {

    private final ConviteService conviteService;
    private final InscricaoService inscricaoService;
    private final CampoPersonalizadoService campoPersonalizadoService;
    private final FotoService fotoService;
    private final UsuarioAutenticado usuarioAutenticado;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/eventos/{eventoId}/inscricoes/minha/convite")
    public ResponseEntity<GerarConviteResponse> gerarConvite(@PathVariable UUID eventoId) {
        var usuario = usuarioAutenticado.get();
        String token = conviteService.gerarToken(eventoId, usuario.getPessoa().getId(), usuario.getIgreja().getId());
        return ResponseEntity.ok(new GerarConviteResponse(token, frontendUrl + "/convite/" + token));
    }

    @GetMapping("/convites/{token}")
    public ResponseEntity<ConvitePublicoResponse> consultar(@PathVariable String token) {
        ConviteResolvido resolvido = conviteService.resolver(token);
        var evento = resolvido.evento();
        var convidante = resolvido.convidante();

        String localNome = null;
        String localEndereco = null;
        if (evento.getLocal() != null) {
            LocalEvento local = evento.getLocal();
            LocalEventoResponse localResp = LocalEventoResponse.from(local);
            localNome = local.getNome();
            localEndereco = localResp.endereco();
        } else if (evento.getLocalTexto() != null) {
            localNome = evento.getLocalTexto();
        }

        Integer vagasRestantes = evento.getVagas() == null ? null
                : Math.max(0, evento.getVagas() - (int) inscricaoService.contarPessoasConfirmadas(evento.getId()));

        var campos = campoPersonalizadoService.listarParaResponder(evento.getId(), evento.getIgreja().getId(), null);

        return ResponseEntity.ok(new ConvitePublicoResponse(
                evento.getId(), evento.getTitulo(), evento.getDescricao(),
                evento.getInicioEm(), evento.getFimEm(),
                localNome, localEndereco,
                evento.getFoto() != null ? evento.getFoto().getId() : null,
                evento.getIgreja().getNome(),
                evento.getIgreja().getLogoFoto() != null ? evento.getIgreja().getLogoFoto().getId() : null,
                convidante != null ? convidante.getNome() : null,
                convidante != null && convidante.getFoto() != null ? convidante.getFoto().getId() : null,
                vagasRestantes, evento.getPreco(), campos, evento.isRequerInscricao()
        ));
    }

    /** Exceção pontual e controlada à regra "foto nunca é URL pública": só serve exatamente
     *  as 3 fotos que {@link #consultar} já expõe pra este token (banner do evento, logo da
     *  igreja, foto de quem convidou) — qualquer outro id, mesmo válido no sistema, é 404
     *  aqui. A posse do token não vira permissão geral de ver fotos. */
    @GetMapping("/convites/{token}/fotos/{fotoId}")
    public ResponseEntity<byte[]> foto(@PathVariable String token, @PathVariable UUID fotoId,
                                        @RequestParam(defaultValue = "DISPLAY") TamanhoFoto tamanho) {
        ConviteResolvido resolvido = conviteService.resolver(token);
        var evento = resolvido.evento();
        var convidante = resolvido.convidante();
        var igreja = evento.getIgreja();

        boolean ehFotoDoEvento = evento.getFoto() != null && evento.getFoto().getId().equals(fotoId);
        boolean ehLogoDaIgreja = igreja.getLogoFoto() != null && igreja.getLogoFoto().getId().equals(fotoId);
        boolean ehFotoDoConvidante = convidante.getFoto() != null && convidante.getFoto().getId().equals(fotoId);

        if (!ehFotoDoEvento && !ehLogoDaIgreja && !ehFotoDoConvidante) {
            throw new ResourceNotFoundException("Foto não encontrada.");
        }

        byte[] bytes = fotoService.ler(fotoId, tamanho, igreja.getId());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(bytes);
    }

    @PostMapping("/convites/{token}/entrar")
    public ResponseEntity<ConvidadoResponse> entrar(@PathVariable String token,
                                                      @Valid @RequestBody EntrarConviteRequest data) {
        ConviteResolvido resolvido = conviteService.resolver(token);
        var evento = resolvido.evento();

        // inscritoPorUsuarioId = null: quem preencheu este formulário não tem usuário logado
        // (fluxo público/anônimo) — "inscrito por" dele é sempre "ele mesmo". gerarLink
        // sempre false: é a própria pessoa se auto-inscrevendo, sempre "paga agora" (mesma
        // regra do titular em inscreverInterno).
        var resultado = inscricaoService.inscreverConvidado(
                evento.getId(), evento.getIgreja().getId(), data.nome(), data.telefone(),
                resolvido.convidante().getId(), null, null, false);

        if (data.respostas() != null && !data.respostas().isEmpty()) {
            campoPersonalizadoService.responderComoConvidado(
                    resultado.inscricao().getId(), data.respostas(), evento.getIgreja().getId());
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConvidadoResponse.from(resultado.inscricao(), resultado.cobranca()));
    }
}
