package br.edu.alertas.gestao;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/representantes")
public class GestaoRepresentantesController {
    public record DadosRepresentante(String nomeCompleto, String entidadePublica, String login) { }
    private final GestaoRepresentantesService gestao;
    public GestaoRepresentantesController(GestaoRepresentantesService gestao) { this.gestao = gestao; }

    @GetMapping
    String listar(@RequestParam(defaultValue = "") String termo,
            @RequestParam(defaultValue = "") String entidadePublica,
            @RequestParam(defaultValue = "") String ativa, Model model) {
        Boolean situacao = null;
        if (!ativa.isBlank()) {
            if (ativa.equals("true") || ativa.equals("false")) { situacao = Boolean.valueOf(ativa); }
            else { model.addAttribute("erro", "Selecione uma situação válida."); }
        }
        model.addAttribute("termo", termo); model.addAttribute("entidadePublica", entidadePublica);
        model.addAttribute("ativa", ativa);
        model.addAttribute("representantes", gestao.pesquisar(termo, entidadePublica, situacao));
        return "gestao/representantes";
    }
    @GetMapping("/novo")
    String novo(Model model) {
        model.addAttribute("dados", new DadosRepresentante("", "", ""));
        return formulario(model, null);
    }
    @PostMapping("/novo")
    String cadastrar(@ModelAttribute("dados") DadosRepresentante dados,
            @RequestParam(defaultValue = "") String senhaTemporaria, Model model) {
        Map<String, String> erros = validar(dados);
        if (senhaTemporaria.codePointCount(0, senhaTemporaria.length()) < 8) {
            erros.put("senhaTemporaria", "Use no mínimo 8 caracteres.");
        }
        model.addAttribute("erros", erros);
        if (!erros.isEmpty()) { return formulario(model, null); }
        try {
            gestao.cadastrar(dados.nomeCompleto(), dados.entidadePublica(), dados.login(), senhaTemporaria);
            return "redirect:/admin/representantes?sucesso";
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            model.addAttribute("erro", "O login já pertence a outra conta. Escolha outro login.");
            return formulario(model, null);
        }
    }
    @GetMapping("/{id}/editar")
    String editar(@PathVariable Long id, Model model) {
        var conta = buscar(id);
        model.addAttribute("dados", new DadosRepresentante(conta.getNomeCompleto(), conta.getEntidadePublica(), conta.getLogin()));
        return formulario(model, id);
    }
    @PostMapping("/{id}/editar")
    String salvar(@PathVariable Long id, @ModelAttribute("dados") DadosRepresentante dados, Model model) {
        Map<String, String> erros = validar(dados); model.addAttribute("erros", erros);
        if (!erros.isEmpty()) { return formulario(model, id); }
        try {
            gestao.alterar(id, dados.nomeCompleto(), dados.entidadePublica(), dados.login());
            return "redirect:/admin/representantes?sucesso";
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            model.addAttribute("erro", "O login já pertence a outra conta. Escolha outro login.");
            return formulario(model, id);
        } catch (EntityNotFoundException exception) { throw naoEncontrado(); }
    }
    @PostMapping("/{id}/desativar")
    String desativar(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean confirmacao, Model model) {
        return mudarSituacao(id, confirmacao, false, model);
    }
    @PostMapping("/{id}/reativar")
    String reativar(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean confirmacao, Model model) {
        return mudarSituacao(id, confirmacao, true, model);
    }
    @PostMapping("/{id}/senha-temporaria")
    String senha(@PathVariable Long id, @RequestParam(defaultValue = "") String senhaTemporaria,
            @RequestParam(defaultValue = "false") boolean confirmacao, Model model) {
        buscar(id);
        if (!confirmacao || senhaTemporaria.codePointCount(0, senhaTemporaria.length()) < 8) {
            model.addAttribute("erroSenha", !confirmacao ? "Confirme a redefinição antes de continuar." : "Use no mínimo 8 caracteres.");
            return editar(id, model);
        }
        gestao.redefinirSenhaTemporaria(id, senhaTemporaria);
        return "redirect:/admin/representantes?sucesso";
    }
    private String mudarSituacao(Long id, boolean confirmacao, boolean ativa, Model model) {
        buscar(id);
        if (!confirmacao) {
            model.addAttribute("erro", "Confirme a mudança de situação antes de continuar.");
            return editar(id, model);
        }
        if (ativa) { gestao.reativar(id); } else { gestao.desativar(id); }
        return "redirect:/admin/representantes?sucesso";
    }
    private String formulario(Model model, Long id) {
        model.addAttribute("id", id);
        if (id != null) { model.addAttribute("conta", buscar(id)); }
        if (!model.containsAttribute("erros")) { model.addAttribute("erros", Map.of()); }
        return "gestao/representante-form";
    }
    private br.edu.alertas.representantes.Representante buscar(Long id) {
        try { return gestao.buscar(id); } catch (EntityNotFoundException exception) { throw naoEncontrado(); }
    }
    private ResponseStatusException naoEncontrado() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
    private Map<String, String> validar(DadosRepresentante dados) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (dados.nomeCompleto() == null || dados.nomeCompleto().isBlank()) { erros.put("nomeCompleto", "Informe o nome completo."); }
        if (dados.entidadePublica() == null || dados.entidadePublica().isBlank()) { erros.put("entidadePublica", "Informe a entidade pública."); }
        if (dados.login() == null || dados.login().isBlank()) { erros.put("login", "Informe o login."); }
        return erros;
    }
}
