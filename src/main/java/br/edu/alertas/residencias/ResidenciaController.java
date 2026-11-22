package br.edu.alertas.residencias;

import jakarta.persistence.EntityNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/residencias")
public class ResidenciaController {
    public record FormularioResidencia(String ruaLogradouro, String numero, String bairro, String municipio,
            String complemento, String pontoReferencia, String areaId) {
        DadosResidencia dados() {
            return new DadosResidencia(ruaLogradouro, numero, bairro, municipio, complemento, pontoReferencia,
                    converterArea(areaId));
        }
    }
    private final ResidenciaService service;

    public ResidenciaController(ResidenciaService service) { this.service = service; }

    @GetMapping
    String listar(@RequestParam(defaultValue = "") String rua, @RequestParam(defaultValue = "") String numero,
            @RequestParam(defaultValue = "") String bairro, @RequestParam(defaultValue = "") String municipio,
            @RequestParam(defaultValue = "") String areaId, Model model) {
        model.addAttribute("rua", rua); model.addAttribute("numero", numero);
        model.addAttribute("bairro", bairro); model.addAttribute("municipio", municipio);
        model.addAttribute("areaId", areaId);
        var areas = service.areasDisponiveis();
        model.addAttribute("areas", areas);
        model.addAttribute("areaFiltroIndisponivel", !areaId.isBlank()
                && areas.stream().noneMatch(area -> area.getId().toString().equals(areaId)));
        try {
            model.addAttribute("residencias", service.pesquisar(rua, numero, bairro, municipio,
                    areaId.isBlank() ? null : converterArea(areaId)));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("erro", "Selecione uma área de risco válida.");
            model.addAttribute("residencias", List.of());
        }
        return "residencias/lista";
    }

    @GetMapping("/novo")
    String novo(Model model) {
        model.addAttribute("dados", new FormularioResidencia("", "", "", "", "", "", ""));
        return formulario(model, null);
    }

    @PostMapping("/novo")
    String cadastrar(@ModelAttribute("dados") FormularioResidencia dados, Model model) {
        if (!validar(dados, model)) { return formulario(model, null); }
        try {
            service.cadastrar(dados.dados());
            return "redirect:/residencias?sucesso";
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            erroArea(model);
            return formulario(model, null);
        }
    }

    @GetMapping("/{id}/editar")
    String editar(@PathVariable Long id, Model model) {
        Residencia residencia = buscar(id);
        model.addAttribute("dados", new FormularioResidencia(residencia.getRuaLogradouro(), residencia.getNumero(),
                residencia.getBairro(), residencia.getMunicipio(), residencia.getComplemento(),
                residencia.getPontoReferencia(), residencia.getAreaId().toString()));
        return formulario(model, id);
    }

    @PostMapping("/{id}/editar")
    String alterar(@PathVariable Long id, @ModelAttribute("dados") FormularioResidencia dados, Model model) {
        buscar(id);
        if (!validar(dados, model)) { return formulario(model, id); }
        try {
            service.alterar(id, dados.dados());
            return "redirect:/residencias?sucesso";
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            erroArea(model);
            return formulario(model, id);
        } catch (EntityNotFoundException exception) { throw naoEncontrada(); }
    }

    @PostMapping("/{id}/excluir")
    String excluir(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean confirmacao,
            Model model) {
        buscar(id);
        if (!confirmacao) {
            model.addAttribute("erro", "Confirme a exclusão antes de continuar.");
            return editar(id, model);
        }
        try {
            service.excluir(id);
            return "redirect:/residencias?sucesso";
        } catch (DataIntegrityViolationException exception) {
            model.addAttribute("erro", "Esta residência possui vínculos e não pode ser excluída.");
            return editar(id, model);
        } catch (EntityNotFoundException exception) { throw naoEncontrada(); }
    }

    private String formulario(Model model, Long id) {
        model.addAttribute("id", id);
        if (id != null) { buscar(id); }
        var areas = service.areasDisponiveis();
        model.addAttribute("areas", areas);
        var dados = (FormularioResidencia) model.getAttribute("dados");
        String selecionada = dados.areaId();
        model.addAttribute("areaIndisponivel", selecionada != null && !selecionada.isBlank()
                && areas.stream().noneMatch(area -> area.getId().toString().equals(selecionada)));
        if (!model.containsAttribute("erros")) { model.addAttribute("erros", Map.of()); }
        return "residencias/formulario";
    }

    private boolean validar(FormularioResidencia dados, Model model) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (vazio(dados.ruaLogradouro())) { erros.put("ruaLogradouro", "Informe a rua ou logradouro."); }
        if (vazio(dados.numero())) { erros.put("numero", "Informe o número ou indique sem número."); }
        if (vazio(dados.bairro())) { erros.put("bairro", "Informe o bairro."); }
        if (vazio(dados.municipio())) { erros.put("municipio", "Informe o município."); }
        try { converterArea(dados.areaId()); }
        catch (IllegalArgumentException exception) { erros.put("areaId", "Selecione uma área de risco cadastrada."); }
        model.addAttribute("erros", erros);
        return erros.isEmpty();
    }

    private void erroArea(Model model) {
        model.addAttribute("erros", Map.of("areaId", "A área selecionada não está disponível. Selecione outra área."));
    }

    private Residencia buscar(Long id) {
        try { return service.buscar(id); }
        catch (EntityNotFoundException exception) { throw naoEncontrada(); }
    }

    private static boolean vazio(String texto) { return texto == null || texto.isBlank(); }
    private static Long converterArea(String valor) {
        if (vazio(valor)) { throw new IllegalArgumentException("Selecione uma área de risco cadastrada."); }
        try { return Long.valueOf(valor); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Selecione uma área de risco cadastrada."); }
    }
    private ResponseStatusException naoEncontrada() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
