package com.domus.api.modules.postagem;

import com.domus.api.modules.foto.Foto;
import com.domus.api.modules.foto.FotoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.postagem.dto.*;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostagemService {

    private final PostagemRepository postagemRepository;
    private final CurtidaPostagemRepository curtidaRepository;
    private final ComentarioPostagemRepository comentarioRepository;
    private final CurtidaComentarioRepository curtidaComentarioRepository;
    private final IgrejaRepository igrejaRepository;
    private final PessoaRepository pessoaRepository;
    private final FotoRepository fotoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;
    private final FamiliaIgrejaService familiaIgrejaService;

    @Transactional(readOnly = true)
    public List<PostagemResponse> listarMural(UUID igrejaId, UUID pessoaId, String perfilUsuario) {
        Set<UUID> restoDaFamilia = obterRestoDaFamilia(igrejaId);
        return postagemRepository.findMuralAvisos(igrejaId, restoDaFamilia).stream()
                .map(p -> toResponse(p, igrejaId, pessoaId, perfilUsuario))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PostagemResponse> listarFeed(UUID igrejaId, UUID pessoaId, String perfilUsuario, TipoPostagem tipoFilter, Pageable pageable) {
        Set<UUID> restoDaFamilia = obterRestoDaFamilia(igrejaId);
        return postagemRepository.findFeed(igrejaId, restoDaFamilia, tipoFilter, pageable)
                .map(p -> toResponse(p, igrejaId, pessoaId, perfilUsuario));
    }

    @Transactional
    public PostagemResponse criarPostagem(UUID igrejaId, UUID autorPessoaId, String perfilUsuario, CriarPostagemRequest request) {
        boolean temConteudo = request.conteudo() != null && !request.conteudo().isBlank();
        boolean temFoto = request.fotoId() != null;
        if (!temConteudo && !temFoto) {
            throw new BusinessException("A postagem deve conter texto ou uma foto.");
        }

        if (request.oficial() && !"ADMIN_IGREJA".equals(perfilUsuario) && !"LIDER".equals(perfilUsuario)) {
            throw new BusinessException("Apenas administradores e líderes podem publicar avisos oficiais no mural.");
        }

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        Pessoa autor = pessoaRepository.findById(autorPessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa autor não encontrada."));

        Foto foto = null;
        if (request.fotoId() != null) {
            foto = fotoRepository.findById(request.fotoId()).orElse(null);
        }

        boolean restritoPropriaIgreja = request.restritoPropriaIgreja() == null || request.restritoPropriaIgreja();

        Postagem post = Postagem.builder()
                .igreja(igreja)
                .autorPessoa(autor)
                .tipo(request.tipo())
                .oficial(request.oficial())
                .titulo(request.titulo())
                .conteudo(request.conteudo())
                .foto(foto)
                .versiculoRef(request.versiculoRef())
                .fixado(request.fixado())
                .restritoPropriaIgreja(restritoPropriaIgreja)
                .build();

        Postagem salva = postagemRepository.save(post);
        return toResponse(salva, igrejaId, autorPessoaId, perfilUsuario);
    }

    @Transactional
    public PostagemResponse alternarCurtida(UUID igrejaId, UUID pessoaId, String perfilUsuario, UUID postagemId, TipoReacao tipoReacao) {
        Set<UUID> restoDaFamilia = obterRestoDaFamilia(igrejaId);
        Postagem post = postagemRepository.findByIdAndFamilia(postagemId, igrejaId, restoDaFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Postagem não encontrada."));

        Optional<CurtidaPostagem> existente = curtidaRepository.findByPostagemIdAndPessoaId(postagemId, pessoaId);

        if (existente.isPresent()) {
            CurtidaPostagem c = existente.get();
            if (c.getTipoReacao() == tipoReacao) {
                curtidaRepository.delete(c);
            } else {
                c.setTipoReacao(tipoReacao);
                curtidaRepository.save(c);
            }
        } else {
            Pessoa pessoa = pessoaRepository.findById(pessoaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));

            CurtidaPostagem nova = CurtidaPostagem.builder()
                    .postagem(post)
                    .pessoa(pessoa)
                    .tipoReacao(tipoReacao)
                    .build();
            curtidaRepository.save(nova);

            // Notificar autor da postagem se não for a própria pessoa
            if (!post.getAutorPessoa().getId().equals(pessoaId)) {
                usuarioRepository.findByPessoaId(post.getAutorPessoa().getId()).ifPresent(u ->
                        notificacaoService.criar(
                                TipoNotificacao.NOVA_INTERACAO_POSTAGEM,
                                post.getIgreja().getId(),
                                u.getId(),
                                pessoa.getNome() + " reagiu à sua postagem.",
                                "/inicio"
                        )
                );
            }
        }

        return toResponse(post, igrejaId, pessoaId, perfilUsuario);
    }

    @Transactional
    public ComentarioResponse comentar(UUID igrejaId, UUID pessoaId, String perfilUsuario, UUID postagemId, String conteudo, UUID paiComentarioId) {
        Set<UUID> restoDaFamilia = obterRestoDaFamilia(igrejaId);
        Postagem post = postagemRepository.findByIdAndFamilia(postagemId, igrejaId, restoDaFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Postagem não encontrada."));

        Pessoa autor = pessoaRepository.findById(pessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));

        ComentarioPostagem pai = null;
        if (paiComentarioId != null) {
            pai = comentarioRepository.findById(paiComentarioId).orElse(null);
        }

        ComentarioPostagem comentario = ComentarioPostagem.builder()
                .postagem(post)
                .autorPessoa(autor)
                .conteudo(conteudo)
                .paiComentario(pai)
                .build();

        ComentarioPostagem salvo = comentarioRepository.save(comentario);

        // Notificar autor da postagem se não for a própria pessoa
        if (!post.getAutorPessoa().getId().equals(pessoaId)) {
            usuarioRepository.findByPessoaId(post.getAutorPessoa().getId()).ifPresent(u ->
                    notificacaoService.criar(
                            TipoNotificacao.NOVA_INTERACAO_POSTAGEM,
                            post.getIgreja().getId(),
                            u.getId(),
                            autor.getNome() + " comentou na sua postagem.",
                            "/inicio"
                    )
            );
        }

        return toComentarioResponse(salvo, 0, false, igrejaId, pessoaId, perfilUsuario);
    }

    @Transactional
    public ComentarioResponse alternarCurtidaComentario(UUID igrejaId, UUID pessoaId, String perfilUsuario, UUID comentarioId) {
        ComentarioPostagem comentario = comentarioRepository.findById(comentarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentário não encontrado."));

        Set<UUID> familiaCompleta = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        if (!familiaCompleta.contains(comentario.getPostagem().getIgreja().getId())) {
            throw new BusinessException("Comentário não pertence a esta rede de igrejas.");
        }

        var curtidaExistente = curtidaComentarioRepository.findByComentarioIdAndPessoaId(comentarioId, pessoaId);
        boolean curtidoPorMim;
        if (curtidaExistente.isPresent()) {
            curtidaComentarioRepository.delete(curtidaExistente.get());
            curtidoPorMim = false;
        } else {
            Pessoa pessoa = pessoaRepository.findById(pessoaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));
            CurtidaComentario nova = CurtidaComentario.builder()
                    .comentario(comentario)
                    .pessoa(pessoa)
                    .build();
            curtidaComentarioRepository.save(nova);
            curtidoPorMim = true;

            // Notificar autor do comentário se não for a própria pessoa
            if (!comentario.getAutorPessoa().getId().equals(pessoaId)) {
                usuarioRepository.findByPessoaId(comentario.getAutorPessoa().getId()).ifPresent(u ->
                        notificacaoService.criar(
                                TipoNotificacao.NOVA_INTERACAO_POSTAGEM,
                                comentario.getPostagem().getIgreja().getId(),
                                u.getId(),
                                pessoa.getNome() + " curtiu o seu comentário.",
                                "/inicio"
                        )
                );
            }
        }

        long totalCurtidas = curtidaComentarioRepository.countByComentarioId(comentarioId);
        return toComentarioResponse(comentario, totalCurtidas, curtidoPorMim, igrejaId, pessoaId, perfilUsuario);
    }

    @Transactional
    public PostagemResponse atualizarPostagem(UUID igrejaId, UUID pessoaId, String perfilUsuario, UUID postagemId, CriarPostagemRequest request) {
        Set<UUID> restoDaFamilia = obterRestoDaFamilia(igrejaId);
        Postagem post = postagemRepository.findByIdAndFamilia(postagemId, igrejaId, restoDaFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Postagem não encontrada."));

        boolean ehAutor = post.getAutorPessoa().getId().equals(pessoaId);
        boolean ehAdminOuLiderDaIgrejaCriadora = post.getIgreja().getId().equals(igrejaId) &&
                ("ADMIN_IGREJA".equals(perfilUsuario) || "LIDER".equals(perfilUsuario));

        if (!ehAutor && !ehAdminOuLiderDaIgrejaCriadora) {
            throw new BusinessException("Você não tem permissão para editar esta postagem.");
        }

        boolean temConteudo = request.conteudo() != null && !request.conteudo().isBlank();
        boolean temFoto = request.fotoId() != null || (post.getFoto() != null && request.fotoId() == null && request.conteudo() != null);
        if (!temConteudo && !temFoto && request.fotoId() == null) {
            throw new BusinessException("A postagem deve conter texto ou uma foto.");
        }

        Foto foto = post.getFoto();
        if (request.fotoId() != null) {
            foto = fotoRepository.findById(request.fotoId()).orElse(null);
        } else if (request.fotoId() == null) {
            foto = null;
        }

        post.setTipo(request.tipo());
        post.setTitulo(request.titulo());
        post.setConteudo(request.conteudo());
        post.setFoto(foto);
        if (request.versiculoRef() != null) {
            post.setVersiculoRef(request.versiculoRef());
        }
        if (request.restritoPropriaIgreja() != null) {
            post.setRestritoPropriaIgreja(request.restritoPropriaIgreja());
        }

        Postagem salva = postagemRepository.save(post);
        return toResponse(salva, igrejaId, pessoaId, perfilUsuario);
    }

    @Transactional
    public ComentarioResponse atualizarComentario(UUID igrejaId, UUID pessoaId, String perfilUsuario, UUID postagemId, UUID comentarioId, String conteudo) {
        ComentarioPostagem comentario = comentarioRepository.findById(comentarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentário não encontrado."));

        if (!comentario.getPostagem().getId().equals(postagemId)) {
            throw new BusinessException("Comentário não pertence a esta postagem.");
        }

        boolean ehAutor = comentario.getAutorPessoa().getId().equals(pessoaId);
        if (!ehAutor) {
            throw new BusinessException("Você não tem permissão para editar este comentário.");
        }

        comentario.setConteudo(conteudo);
        ComentarioPostagem salvo = comentarioRepository.save(comentario);
        long totalCurtidas = curtidaComentarioRepository.countByComentarioId(comentarioId);
        boolean curtidoPorMim = curtidaComentarioRepository.existsByComentarioIdAndPessoaId(comentarioId, pessoaId);
        return toComentarioResponse(salvo, totalCurtidas, curtidoPorMim, igrejaId, pessoaId, perfilUsuario);
    }

    @Transactional
    public void deletarComentario(UUID igrejaId, UUID pessoaId, String perfilUsuario, UUID postagemId, UUID comentarioId) {
        ComentarioPostagem comentario = comentarioRepository.findById(comentarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentário não encontrado."));

        if (!comentario.getPostagem().getId().equals(postagemId)) {
            throw new BusinessException("Comentário não pertence a esta postagem.");
        }

        boolean ehAutorComentario = comentario.getAutorPessoa().getId().equals(pessoaId);
        boolean ehAutorPostagem = comentario.getPostagem().getAutorPessoa().getId().equals(pessoaId);
        boolean ehAdminOuLiderDaIgrejaCriadora = comentario.getPostagem().getIgreja().getId().equals(igrejaId) &&
                ("ADMIN_IGREJA".equals(perfilUsuario) || "LIDER".equals(perfilUsuario));

        if (!ehAutorComentario && !ehAutorPostagem && !ehAdminOuLiderDaIgrejaCriadora) {
            throw new BusinessException("Você não tem permissão para excluir este comentário.");
        }

        comentarioRepository.delete(comentario);
    }

    @Transactional
    public void deletarPostagem(UUID igrejaId, UUID pessoaId, String perfilUsuario, UUID postagemId) {
        Set<UUID> restoDaFamilia = obterRestoDaFamilia(igrejaId);
        Postagem post = postagemRepository.findByIdAndFamilia(postagemId, igrejaId, restoDaFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Postagem não encontrada."));

        boolean ehAutor = post.getAutorPessoa().getId().equals(pessoaId);
        boolean ehAdminOuLiderDaIgrejaCriadora = post.getIgreja().getId().equals(igrejaId) &&
                ("ADMIN_IGREJA".equals(perfilUsuario) || "LIDER".equals(perfilUsuario));

        if (!ehAutor && !ehAdminOuLiderDaIgrejaCriadora) {
            throw new BusinessException("Você não tem permissão para excluir esta postagem.");
        }

        postagemRepository.delete(post);
    }

    private Set<UUID> obterRestoDaFamilia(UUID igrejaId) {
        Set<UUID> familiaCompleta = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Set<UUID> restoDaFamilia = new HashSet<>(familiaCompleta);
        restoDaFamilia.remove(igrejaId);
        return restoDaFamilia;
    }

    private PostagemResponse toResponse(Postagem p, UUID igrejaIdContexto, UUID pessoaIdContexto, String perfilUsuario) {
        long totalCurtidas = curtidaRepository.countByPostagemId(p.getId());
        long totalComentarios = comentarioRepository.countByPostagemId(p.getId());

        TipoReacao minhaReacao = null;
        if (pessoaIdContexto != null) {
            minhaReacao = curtidaRepository.findByPostagemIdAndPessoaId(p.getId(), pessoaIdContexto)
                    .map(CurtidaPostagem::getTipoReacao)
                    .orElse(null);
        }

        List<ComentarioResponse> comentarios = comentarioRepository.findByPostagemIdOrderByCriadoEmDesc(p.getId())
                .stream()
                .map(c -> {
                    long totalCurtidasComentario = curtidaComentarioRepository.countByComentarioId(c.getId());
                    boolean curtidoPorMim = pessoaIdContexto != null && curtidaComentarioRepository.existsByComentarioIdAndPessoaId(c.getId(), pessoaIdContexto);
                    return toComentarioResponse(c, totalCurtidasComentario, curtidoPorMim, igrejaIdContexto, pessoaIdContexto, perfilUsuario);
                })
                .toList();

        boolean ehAutor = p.getAutorPessoa().getId().equals(pessoaIdContexto);
        boolean ehAdminOuLiderDaIgrejaCriadora = p.getIgreja().getId().equals(igrejaIdContexto) &&
                ("ADMIN_IGREJA".equals(perfilUsuario) || "LIDER".equals(perfilUsuario));

        boolean podeEditar = ehAutor || ehAdminOuLiderDaIgrejaCriadora;
        boolean podeDeletar = ehAutor || ehAdminOuLiderDaIgrejaCriadora;

        return PostagemResponse.from(p, totalCurtidas, totalComentarios, minhaReacao, comentarios, podeEditar, podeDeletar);
    }

    private ComentarioResponse toComentarioResponse(ComentarioPostagem c, long totalCurtidas, boolean curtidoPorMim, UUID igrejaIdContexto, UUID pessoaIdContexto, String perfilUsuario) {
        boolean ehAutorComentario = c.getAutorPessoa().getId().equals(pessoaIdContexto);
        boolean ehAutorPostagem = c.getPostagem().getAutorPessoa().getId().equals(pessoaIdContexto);
        boolean ehAdminOuLiderDaIgrejaCriadora = c.getPostagem().getIgreja().getId().equals(igrejaIdContexto) &&
                ("ADMIN_IGREJA".equals(perfilUsuario) || "LIDER".equals(perfilUsuario));

        boolean podeDeletar = ehAutorComentario || ehAutorPostagem || ehAdminOuLiderDaIgrejaCriadora;
        return ComentarioResponse.from(c, totalCurtidas, curtidoPorMim, podeDeletar);
    }
}
