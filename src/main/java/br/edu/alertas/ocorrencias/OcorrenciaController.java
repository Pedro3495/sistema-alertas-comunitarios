package br.edu.alertas.ocorrencias;

import br.edu.alertas.areas.AreaRiscoRepository;
import br.edu.alertas.auditoria.UsuarioAtual;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/ocorrencias")
public class OcorrenciaController {
    private final OcorrenciaService service;
    private final AreaRiscoRepository areas;
    private final UsuarioAtual usuarios;
    public OcorrenciaController(OcorrenciaService service, AreaRiscoRepository areas, UsuarioAtual usuarios) {
        this.service = service; this.areas = areas; this.usuarios = usuarios;
    }

    @GetMapping("/novo")
    String novo(Model model) {
        var ator = usuarios.exigirRepresentante();
        model.addAttribute("areas", areas.findAll(Sort.by("id")));
        model.addAttribute("rascunhoChave", "REPRESENTANTE:" + ator.id());
        return "ocorrencias/formulario";
    }

    @PostMapping
    @ResponseBody
    Map<String, String> registrar(@RequestParam(defaultValue = "") String envioId,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(defaultValue = "") String especificacaoOutros,
            @RequestParam(defaultValue = "") String descricao,
            @RequestParam(defaultValue = "") String localizacao,
            @RequestParam(defaultValue = "") String areaId,
            @RequestParam(required = false) List<MultipartFile> fotos) {
        usuarios.exigirRepresentante();
        UUID chave;
        try { chave = UUID.fromString(envioId); }
        catch (IllegalArgumentException exception) { throw new ErroOcorrencia("envioId", "Reabra o formulário para preparar o envio."); }
        TipoOcorrencia tipoEscolhido;
        try { tipoEscolhido = TipoOcorrencia.valueOf(tipo); }
        catch (IllegalArgumentException exception) { throw new ErroOcorrencia("tipo", "Selecione o tipo de ocorrência."); }
        Long area;
        try { area = Long.valueOf(areaId); }
        catch (NumberFormatException exception) { throw new ErroOcorrencia("areaId", "Selecione uma área de risco cadastrada."); }
        List<byte[]> imagens = new ArrayList<>();
        if (fotos != null) {
            if (fotos.size() > 4) { throw new ErroOcorrencia("fotos", "Escolha de uma a quatro fotos como evidências."); }
            for (var foto : fotos) {
                if (foto.getSize() > FotoOcorrencia.LIMITE_BYTES) { throw new ErroOcorrencia("fotos", "Reduza a foto para até 5 MB antes de tentar novamente."); }
                try { imagens.add(foto.getBytes()); }
                catch (IOException exception) { throw new ErroOcorrencia("fotos", "Não foi possível ler a foto. Escolha outra e tente novamente."); }
            }
        }
        var salva = service.registrar(new DadosOcorrencia(chave, tipoEscolhido, especificacaoOutros, descricao,
                localizacao, area, imagens));
        return Map.of("protocolo", salva.getProtocolo().toString(), "detalheUrl", "/ocorrencias/" + salva.getProtocolo());
    }

    @GetMapping("/{protocolo}")
    String detalhe(@PathVariable UUID protocolo, Model model) {
        var ocorrencia = buscar(protocolo);
        model.addAttribute("ocorrencia", ocorrencia);
        var ator = usuarios.exigirRepresentante();
        if (ocorrencia.getRepresentanteId().equals(ator.id())) {
            model.addAttribute("rascunhoChave", "REPRESENTANTE:" + ator.id());
        }
        return "ocorrencias/detalhe";
    }

    @GetMapping("/{protocolo}/fotos/{ordem}")
    ResponseEntity<byte[]> foto(@PathVariable UUID protocolo, @PathVariable int ordem) {
        try {
            var foto = service.foto(protocolo, ordem);
            boolean heif = foto.getTipoMidia().equals("image/heic") || foto.getTipoMidia().equals("image/heif");
            String extensao = heif ? "heic" : foto.getTipoMidia().equals("image/png") ? "png" : "jpg";
            var disposition = (heif ? ContentDisposition.attachment() : ContentDisposition.inline())
                    .filename("foto-" + ordem + "." + extensao).build();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .contentType(MediaType.parseMediaType(foto.getTipoMidia())).body(foto.getConteudo());
        } catch (EntityNotFoundException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
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
