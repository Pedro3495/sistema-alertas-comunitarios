package br.edu.alertas.ocorrencias;

import br.edu.alertas.areas.AreaRiscoRepository;
import br.edu.alertas.auditoria.UsuarioAtual;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

// Representante: /ocorrencias (qualquer área). Morador: /morador/ocorrencias (área da residência, RF006 regra 4).
@Controller
public class OcorrenciaController {
    private final OcorrenciaService service;
    private final AreaRiscoRepository areas;
    private final UsuarioAtual usuarios;
    private final AlertaService alertas;
    public OcorrenciaController(OcorrenciaService service, AreaRiscoRepository areas, UsuarioAtual usuarios, AlertaService alertas) {
        this.service = service; this.areas = areas; this.usuarios = usuarios; this.alertas = alertas;
    }

    // RF008: pesquisa por protocolo e filtros por tipo e situação; área só para o representante.
    @GetMapping("/ocorrencias")
    String lista(@RequestParam(defaultValue = "") String protocolo, @RequestParam(required = false) TipoOcorrencia tipo,
            @RequestParam(required = false) SituacaoOcorrencia situacao, @RequestParam(required = false) Long areaId, Model model) {
        var chave = filtros(protocolo, tipo, situacao, model);
        model.addAttribute("areas", areas.findAll(Sort.by("nome")));
        model.addAttribute("areaId", areaId);
        model.addAttribute("ocorrencias", service.pesquisar(chave, tipo, situacao, areaId));
        return "ocorrencias/lista";
    }

    @GetMapping("/morador/ocorrencias")
    String listaDoMorador(@RequestParam(defaultValue = "") String protocolo, @RequestParam(required = false) TipoOcorrencia tipo,
            @RequestParam(required = false) SituacaoOcorrencia situacao, Model model) {
        var chave = filtros(protocolo, tipo, situacao, model);
        model.addAttribute("morador", true);
        model.addAttribute("ocorrencias", service.pesquisarDoMorador(chave, tipo, situacao));
        return "ocorrencias/lista";
    }

    // Protocolo inválido vira o UUID nulo (nunca gerado por randomUUID), então a busca volta vazia com aviso.
    private static UUID filtros(String protocolo, TipoOcorrencia tipo, SituacaoOcorrencia situacao, Model model) {
        model.addAttribute("protocolo", protocolo);
        model.addAttribute("tipo", tipo);
        model.addAttribute("situacao", situacao);
        model.addAttribute("tipos", TipoOcorrencia.values());
        model.addAttribute("situacoes", SituacaoOcorrencia.values());
        if (protocolo.isBlank()) { return null; }
        try { return UUID.fromString(protocolo.strip()); }
        catch (IllegalArgumentException exception) {
            model.addAttribute("erro", "Protocolo inválido. Confira o código completo informado no registro.");
            return new UUID(0, 0);
        }
    }

    @GetMapping("/ocorrencias/novo")
    String novo(Model model) {
        var ator = usuarios.exigirRepresentante();
        model.addAttribute("areas", areas.findAll(Sort.by("id")));
        model.addAttribute("rascunhoChave", "REPRESENTANTE:" + ator.id());
        return "ocorrencias/formulario";
    }

    @GetMapping("/morador/ocorrencias/novo")
    String novoDoMorador(Model model) {
        var morador = service.moradorAtual();
        model.addAttribute("morador", true);
        model.addAttribute("areas", List.of(Map.of("id", morador.getAreaId(), "nome", morador.getAreaNome())));
        model.addAttribute("rascunhoChave", "MORADOR:" + morador.getId());
        return "ocorrencias/formulario";
    }

    @PostMapping("/ocorrencias")
    @ResponseBody
    Map<String, String> registrar(@RequestParam(defaultValue = "") String envioId,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(defaultValue = "") String especificacaoOutros,
            @RequestParam(defaultValue = "") String descricao,
            @RequestParam(defaultValue = "") String localizacao,
            @RequestParam(defaultValue = "") String areaId,
            @RequestParam(required = false) List<MultipartFile> fotos) {
        usuarios.exigirRepresentante();
        Long area;
        try { area = Long.valueOf(areaId); }
        catch (NumberFormatException exception) { throw new ErroOcorrencia("areaId", "Selecione uma área de risco cadastrada."); }
        var salva = service.registrar(dados(envioId, tipo, especificacaoOutros, descricao, localizacao, area, fotos));
        return Map.of("protocolo", salva.getProtocolo().toString(), "detalheUrl", "/ocorrencias/" + salva.getProtocolo());
    }

    // Nenhum campo de área é lido: o servidor usa a residência atual do morador.
    @PostMapping("/morador/ocorrencias")
    @ResponseBody
    Map<String, String> registrarDoMorador(@RequestParam(defaultValue = "") String envioId,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(defaultValue = "") String especificacaoOutros,
            @RequestParam(defaultValue = "") String descricao,
            @RequestParam(defaultValue = "") String localizacao,
            @RequestParam(required = false) List<MultipartFile> fotos) {
        usuarios.exigirMorador();
        var salva = service.registrarPorMorador(dados(envioId, tipo, especificacaoOutros, descricao, localizacao, null, fotos));
        return Map.of("protocolo", salva.getProtocolo().toString(), "detalheUrl", "/morador/ocorrencias/" + salva.getProtocolo());
    }

    private static DadosOcorrencia dados(String envioId, String tipo, String especificacaoOutros, String descricao,
            String localizacao, Long area, List<MultipartFile> fotos) {
        UUID chave;
        try { chave = UUID.fromString(envioId); }
        catch (IllegalArgumentException exception) { throw new ErroOcorrencia("envioId", "Reabra o formulário para preparar o envio."); }
        TipoOcorrencia tipoEscolhido;
        try { tipoEscolhido = TipoOcorrencia.valueOf(tipo); }
        catch (IllegalArgumentException exception) { throw new ErroOcorrencia("tipo", "Selecione o tipo de ocorrência."); }
        List<byte[]> imagens = new ArrayList<>();
        if (fotos != null) {
            if (fotos.size() > 4) { throw new ErroOcorrencia("fotos", "Escolha de uma a quatro fotos como evidências."); }
            for (var foto : fotos) {
                if (foto.getSize() > FotoOcorrencia.LIMITE_BYTES) { throw new ErroOcorrencia("fotos", "Reduza a foto para até 5 MB antes de tentar novamente."); }
                try { imagens.add(foto.getBytes()); }
                catch (IOException exception) { throw new ErroOcorrencia("fotos", "Não foi possível ler a foto. Escolha outra e tente novamente."); }
            }
        }
        return new DadosOcorrencia(chave, tipoEscolhido, especificacaoOutros, descricao, localizacao, area, imagens);
    }

    @GetMapping("/ocorrencias/{protocolo}")
    String detalhe(@PathVariable UUID protocolo, Model model) {
        var ocorrencia = buscar(protocolo);
        model.addAttribute("ocorrencia", ocorrencia);
        var ator = usuarios.exigirRepresentante();
        if (ator.id().equals(ocorrencia.getRepresentanteId())) {
            model.addAttribute("rascunhoChave", "REPRESENTANTE:" + ator.id());
        }
        model.addAttribute("verificacoes", service.verificacoes(protocolo));
        model.addAttribute("alteracoes", service.alteracoes(protocolo));
        model.addAttribute("encerramento", service.encerramento(protocolo).orElse(null));
        model.addAttribute("areas", areas.findAll(Sort.by("nome")));
        model.addAttribute("tipos", TipoOcorrencia.values());
        if (!model.containsAttribute("alteracao")) { // RF011: o formulário abre com os valores atuais
            model.addAttribute("alteracao", Map.of("tipo", ocorrencia.getTipo().name(),
                    "especificacaoOutros", Objects.toString(ocorrencia.getEspecificacaoOutros(), ""),
                    "descricao", ocorrencia.getDescricao(), "localizacao", ocorrencia.getLocalizacao(),
                    "areaId", ocorrencia.getAreaId().toString(), "justificativa", "", "orientacoes", ""));
        }
        model.addAttribute("comunicados", alertas.daOcorrencia(protocolo));
        return "ocorrencias/detalhe";
    }

    // RF009: formulário HTML com CSRF; erro volta à tela com 400 e o texto digitado.
    @PostMapping("/ocorrencias/{protocolo}/verificacao")
    String verificar(@PathVariable UUID protocolo, @RequestParam(defaultValue = "") String situacao,
            @RequestParam(defaultValue = "") String justificativa, @RequestParam(defaultValue = "") String orientacoes,
            Model model, HttpServletResponse response, RedirectAttributes redirect) {
        usuarios.exigirRepresentante();
        SituacaoOcorrencia decisao = null;
        try { decisao = SituacaoOcorrencia.valueOf(situacao); } catch (IllegalArgumentException ignorada) { } // o domínio rejeita nulo
        try {
            service.verificar(protocolo, decisao, justificativa, orientacoes);
        } catch (EntityNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ErroOcorrencia exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("erroVerificacao", exception);
            model.addAttribute("decisao", situacao);
            model.addAttribute("justificativa", justificativa);
            model.addAttribute("orientacoesVerificacao", orientacoes);
            return detalhe(protocolo, model);
        }
        redirect.addFlashAttribute("verificada", true);
        return "redirect:/ocorrencias/" + protocolo;
    }

    // RF011: formulário HTML com CSRF, só para representantes; erro volta à tela com 400 e os valores digitados.
    @PostMapping("/ocorrencias/{protocolo}/alteracao")
    String alterar(@PathVariable UUID protocolo, @RequestParam(defaultValue = "") String tipo,
            @RequestParam(defaultValue = "") String especificacaoOutros, @RequestParam(defaultValue = "") String descricao,
            @RequestParam(defaultValue = "") String localizacao, @RequestParam(defaultValue = "") String areaId,
            @RequestParam(defaultValue = "") String justificativa, @RequestParam(defaultValue = "") String orientacoes,
            Model model, HttpServletResponse response, RedirectAttributes redirect) {
        usuarios.exigirRepresentante();
        TipoOcorrencia tipoEscolhido = null;
        Long area = null;
        try { tipoEscolhido = TipoOcorrencia.valueOf(tipo); } catch (IllegalArgumentException ignorada) { } // o domínio rejeita nulo
        try { area = Long.valueOf(areaId); } catch (NumberFormatException ignorada) { }
        try {
            service.alterar(protocolo, tipoEscolhido, especificacaoOutros, descricao, localizacao, area, justificativa, orientacoes);
        } catch (EntityNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ErroOcorrencia exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("erroAlteracao", exception);
            model.addAttribute("alteracao", Map.of("tipo", tipo, "especificacaoOutros", especificacaoOutros, "descricao", descricao,
                    "localizacao", localizacao, "areaId", areaId, "justificativa", justificativa, "orientacoes", orientacoes));
            return detalhe(protocolo, model);
        }
        redirect.addFlashAttribute("alterada", true);
        return "redirect:/ocorrencias/" + protocolo;
    }

    // RF012: formulário HTML com CSRF, só para representantes; erro volta à tela com 400 e o texto digitado.
    @PostMapping("/ocorrencias/{protocolo}/encerramento")
    String encerrar(@PathVariable UUID protocolo, @RequestParam(defaultValue = "") String justificativa,
            @RequestParam(defaultValue = "") String resumo, @RequestParam(defaultValue = "") String orientacoes,
            Model model, HttpServletResponse response, RedirectAttributes redirect) {
        usuarios.exigirRepresentante();
        try {
            service.encerrar(protocolo, justificativa, resumo, orientacoes);
        } catch (EntityNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ErroOcorrencia exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("erroEncerramento", exception);
            model.addAttribute("encerramentoForm", Map.of("justificativa", justificativa, "resumo", resumo, "orientacoes", orientacoes));
            return detalhe(protocolo, model);
        }
        redirect.addFlashAttribute("encerrada", true);
        return "redirect:/ocorrencias/" + protocolo;
    }

    // RF010 regra 1: ação explícita do representante, com CSRF; erro volta à tela com 400 e o texto digitado.
    @PostMapping("/ocorrencias/{protocolo}/alertas")
    String emitirAlerta(@PathVariable UUID protocolo, @RequestParam(defaultValue = "") String titulo,
            @RequestParam(defaultValue = "") String mensagem, @RequestParam(defaultValue = "") String orientacoes,
            Model model, HttpServletResponse response, RedirectAttributes redirect) {
        usuarios.exigirRepresentante();
        Comunicado alerta;
        try {
            alerta = alertas.emitir(protocolo, titulo, mensagem, orientacoes);
        } catch (EntityNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ErroOcorrencia exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("erroAlerta", exception);
            model.addAttribute("alerta", Map.of("titulo", titulo, "mensagem", mensagem, "orientacoes", orientacoes));
            return detalhe(protocolo, model);
        }
        redirect.addFlashAttribute("envioAlerta", alerta.getFalhas());
        return "redirect:/ocorrencias/" + protocolo;
    }

    // RF010 regra 4: reenvio manual apenas para quem falhou nos dois canais.
    @PostMapping("/ocorrencias/{protocolo}/alertas/{id}/reenvio")
    String reenviarAlerta(@PathVariable UUID protocolo, @PathVariable Long id, RedirectAttributes redirect) {
        usuarios.exigirRepresentante();
        try { redirect.addFlashAttribute("envioAlerta", alertas.reenviar(protocolo, id).getFalhas()); }
        catch (EntityNotFoundException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        return "redirect:/ocorrencias/" + protocolo;
    }

    @GetMapping("/morador/alertas")
    String alertasDoMorador(Model model) {
        model.addAttribute("morador", true);
        model.addAttribute("alertas", alertas.doMorador());
        return "ocorrencias/alertas";
    }

    @GetMapping("/morador/ocorrencias/{protocolo}")
    String detalheDoMorador(@PathVariable UUID protocolo, Model model) {
        Ocorrencia ocorrencia;
        try { ocorrencia = service.buscarDoMorador(protocolo); }
        catch (EntityNotFoundException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        model.addAttribute("morador", true);
        model.addAttribute("ocorrencia", ocorrencia);
        // Só o autor limpa o rascunho; relato de terceiro (RF008 regra 2) não expõe dados do envio.
        var ator = usuarios.exigirMorador();
        if (ator.id().equals(ocorrencia.getMoradorId())) { model.addAttribute("rascunhoChave", "MORADOR:" + ator.id()); }
        return "ocorrencias/detalhe";
    }

    @GetMapping("/ocorrencias/{protocolo}/fotos/{ordem}")
    ResponseEntity<byte[]> foto(@PathVariable UUID protocolo, @PathVariable int ordem) {
        try { return foto(service.foto(protocolo, ordem)); }
        catch (EntityNotFoundException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }

    @GetMapping("/morador/ocorrencias/{protocolo}/fotos/{ordem}")
    ResponseEntity<byte[]> fotoDoMorador(@PathVariable UUID protocolo, @PathVariable int ordem) {
        try { return foto(service.fotoDoMorador(protocolo, ordem)); }
        catch (EntityNotFoundException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }

    private static ResponseEntity<byte[]> foto(FotoOcorrencia foto) {
        boolean heif = foto.getTipoMidia().equals("image/heic") || foto.getTipoMidia().equals("image/heif");
        String extensao = heif ? "heic" : foto.getTipoMidia().equals("image/png") ? "png" : "jpg";
        var disposition = (heif ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename("foto-" + foto.getOrdem() + "." + extensao).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(foto.getTipoMidia())).body(foto.getConteudo());
    }

    @ExceptionHandler(ErroOcorrencia.class)
    ResponseEntity<Map<String, String>> erro(ErroOcorrencia exception) {
        return ResponseEntity.badRequest().body(Map.of("campo", exception.getCampo(), "erro", exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> areaRemovida() {
        return ResponseEntity.badRequest().body(Map.of("campo", "areaId", "erro", "A área selecionada não está disponível. Selecione outra área."));
    }

    private Ocorrencia buscar(UUID protocolo) {
        try { return service.buscar(protocolo); }
        catch (EntityNotFoundException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }
}
