package com.domus.api.modules.evento.convite;

import com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoService;
import com.domus.api.modules.evento.convite.DTOs.ConvitePublicoResponse;
import com.domus.api.modules.evento.convite.DTOs.EntrarConviteRequest;
import com.domus.api.modules.evento.convite.DTOs.GerarConviteResponse;
import com.domus.api.modules.evento.inscricao.DTOs.ConvidadoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.evento.local.DTOs.LocalEventoResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ConviteController {

    private final ConviteService conviteService;
    private final InscricaoService inscricaoService;
    private final CampoPersonalizadoService campoPersonalizadoService;
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
        InscricaoEvento inscricaoConvidante = conviteService.resolverInscricaoConvidante(token);
        var evento = inscricaoConvidante.getEvento();
        var convidante = inscricaoConvidante.getPessoa();

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
                vagasRestantes, evento.getPreco(), campos
        ));
    }

    @PostMapping("/convites/{token}/entrar")
    public ResponseEntity<ConvidadoResponse> entrar(@PathVariable String token,
                                                      @Valid @RequestBody EntrarConviteRequest data) {
        InscricaoEvento inscricaoConvidante = conviteService.resolverInscricaoConvidante(token);
        var evento = inscricaoConvidante.getEvento();
        UUID convidadoPorPessoaId = inscricaoConvidante.getPessoa() != null
                ? inscricaoConvidante.getPessoa().getId() : null;

        var inscricao = inscricaoService.inscreverConvidado(
                evento.getId(), evento.getIgreja().getId(), data.nome(), data.telefone(), convidadoPorPessoaId);

        if (data.respostas() != null && !data.respostas().isEmpty()) {
            campoPersonalizadoService.responderComoConvidado(
                    inscricao.getId(), data.respostas(), evento.getIgreja().getId());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(ConvidadoResponse.from(inscricao));
    }
}
