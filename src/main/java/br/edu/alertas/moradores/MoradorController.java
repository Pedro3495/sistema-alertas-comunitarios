package br.edu.alertas.moradores;

import br.edu.alertas.residencias.ResidenciaService;
import jakarta.persistence.EntityNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/moradores")
public class MoradorController {
    public record FormularioMorador(String nomeCompleto, String cpf, String celular, String residenciaId,
            String canalCredenciais, String preferenciaAlertas) { }
    private final MoradorService service;
    private final ResidenciaService residencias;

    public MoradorController(MoradorService service, ResidenciaService residencias) {
        this.service = service; this.residencias = residencias;
    }

    @GetMapping
    String listar(@RequestParam(defaultValue = "") String termo, Model model) {
        model.addAttribute("termo", termo);
        model.addAttribute("moradores", service.pesquisar(termo));
        return "moradores/lista";
    }

    @GetMapping("/novo")
    String novo(Model model) {
        model.addAttribute("dados", new FormularioMorador("", "", "", "", "", ""));
        model.addAttribute("erros", Map.of());
        return formulario(model);
    }

    @PostMapping("/novo")
    String cadastrar(@ModelAttribute("dados") FormularioMorador dados, Model model) {
        Map<String, String> erros = validar(dados);
        model.addAttribute("erros", erros);
        if (!erros.isEmpty()) { return formulario(model); }
        try {
            var morador = service.cadastrar(new DadosMorador(dados.nomeCompleto(), dados.cpf(), dados.celular(),
                    Long.valueOf(dados.residenciaId()), CanalCredenciais.valueOf(dados.canalCredenciais()),
                    PreferenciaAlertas.valueOf(dados.preferenciaAlertas())));
            return "redirect:/moradores/" + morador.getId();
        } catch (IllegalArgumentException exception) {
            String mensagem = exception.getMessage();
            String campo = mensagem.contains("CPF") ? "cpf" : mensagem.contains("celular") ? "celular" : "residenciaId";
            model.addAttribute("erros", Map.of(campo, mensagem));
        } catch (DataIntegrityViolationException exception) {
            model.addAttribute("erro", "O CPF ou o celular já pertence a outra conta, ou a residência deixou de existir.");
        }
        return formulario(model);
    }

    @GetMapping("/{id}")
    String detalhe(@PathVariable Long id, Model model) {
        model.addAttribute("morador", buscar(id));
        return "moradores/detalhe";
    }

    @PostMapping("/{id}/reenviar-senha")
    String reenviar(@PathVariable Long id, Model model) {
        try {
            service.reenviarSenhaTemporaria(id);
            return "redirect:/moradores/" + id;
        } catch (IllegalStateException exception) {
            model.addAttribute("erro", exception.getMessage());
            return detalhe(id, model);
        } catch (EntityNotFoundException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }

    private String formulario(Model model) {
        model.addAttribute("residencias", residencias.pesquisar(null, null, null, null, null));
        return "moradores/formulario";
    }

    private static Map<String, String> validar(FormularioMorador dados) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (vazio(dados.nomeCompleto())) { erros.put("nomeCompleto", "Informe o nome completo."); }
        if (!Morador.cpfValido(dados.cpf())) { erros.put("cpf", "Informe um CPF de 11 dígitos com verificadores válidos, só números."); }
        if (dados.celular() == null || !dados.celular().matches("[0-9]{11}")) {
            erros.put("celular", "Informe o celular com DDD e 11 dígitos, só números.");
        }
        if (vazio(dados.residenciaId()) || !dados.residenciaId().matches("[0-9]{1,18}")) {
            erros.put("residenciaId", "Selecione uma residência cadastrada.");
        }
        if (!valido(CanalCredenciais.class, dados.canalCredenciais())) {
            erros.put("canalCredenciais", "Escolha SMS ou WhatsApp para a senha temporária.");
        }
        if (!valido(PreferenciaAlertas.class, dados.preferenciaAlertas())) {
            erros.put("preferenciaAlertas", "Escolha SMS, WhatsApp ou ambos para os alertas.");
        }
        return erros;
    }

    private static <E extends Enum<E>> boolean valido(Class<E> tipo, String valor) {
        try { Enum.valueOf(tipo, valor); return true; }
        catch (IllegalArgumentException | NullPointerException exception) { return false; }
    }

    private Morador buscar(Long id) {
        try { return service.buscar(id); }
        catch (EntityNotFoundException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }

    private static boolean vazio(String texto) { return texto == null || texto.isBlank(); }
}
