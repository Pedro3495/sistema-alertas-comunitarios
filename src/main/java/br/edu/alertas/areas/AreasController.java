package br.edu.alertas.areas;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.LinkedHashMap;

@Controller
@RequestMapping("/areas")
public class AreasController {
    public record DadosArea(String nome, String descricaoLocalizacaoLimites) { }
    private final GestaoAreasService gestao;
    public AreasController(GestaoAreasService gestao) { this.gestao = gestao; }
    @GetMapping
    String listar(@RequestParam(defaultValue = "") String nome, Model model) {
        model.addAttribute("nome", nome); model.addAttribute("areas", gestao.pesquisar(nome));
        return "areas/lista";
    }
    @GetMapping("/novo")
    String novo(Model model) {
        model.addAttribute("dados", new DadosArea("", "")); return formulario(model, null);
    }
    @PostMapping("/novo")
    String cadastrar(@ModelAttribute("dados") DadosArea dados, Model model) {
        if (!validar(dados, model)) { return formulario(model, null); }
        gestao.cadastrar(dados.nome(), dados.descricaoLocalizacaoLimites());
        return "redirect:/areas?sucesso";
    }
    @GetMapping("/{id}/editar")
    String editar(@PathVariable Long id, Model model) {
        var area = buscar(id); model.addAttribute("dados", new DadosArea(area.getNome(), area.getDescricaoLocalizacaoLimites()));
        return formulario(model, id);
    }
    @PostMapping("/{id}/editar")
    String salvar(@PathVariable Long id, @ModelAttribute("dados") DadosArea dados, Model model) {
        buscar(id);
        if (!validar(dados, model)) { return formulario(model, id); }
        gestao.alterar(id, dados.nome(), dados.descricaoLocalizacaoLimites());
        return "redirect:/areas?sucesso";
    }
    @PostMapping("/{id}/excluir")
    String excluir(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean confirmacao, Model model) {
        buscar(id);
        if (!confirmacao) {
            model.addAttribute("erro", "Confirme a exclusão antes de continuar."); return editar(id, model);
        }
        try { gestao.excluir(id); return "redirect:/areas?sucesso"; }
        catch (DataIntegrityViolationException exception) {
            model.addAttribute("erro", "Esta área está vinculada a outros registros e não pode ser excluída.");
            return editar(id, model);
        }
    }
    private boolean validar(DadosArea dados, Model model) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (dados.nome() == null || dados.nome().isBlank()) { erros.put("nome", "Informe o nome da área."); }
        if (dados.descricaoLocalizacaoLimites() == null || dados.descricaoLocalizacaoLimites().isBlank()) {
            erros.put("descricao", "Descreva a localização e os limites da área.");
        }
        model.addAttribute("erros", erros); return erros.isEmpty();
    }
    private String formulario(Model model, Long id) {
        model.addAttribute("id", id);
        if (!model.containsAttribute("erros")) { model.addAttribute("erros", Map.of()); }
        return "areas/formulario";
    }
    @ExceptionHandler(EntityNotFoundException.class)
    org.springframework.http.ResponseEntity<String> registroRemovido() {
        return org.springframework.http.ResponseEntity.status(HttpStatus.NOT_FOUND).body("Área de risco não encontrada.");
    }
    private AreaRisco buscar(Long id) {
        try { return gestao.buscar(id); }
        catch (EntityNotFoundException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }
}
