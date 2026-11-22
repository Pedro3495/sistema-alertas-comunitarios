package br.edu.alertas.moradores;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** RF001 regra 6: o morador altera no aplicativo o canal de credenciais e a preferência de alertas. */
@Controller
@RequestMapping("/morador/preferencias")
public class PreferenciasMoradorController {
    private final MoradorService moradores;

    public PreferenciasMoradorController(MoradorService moradores) { this.moradores = moradores; }

    @GetMapping
    String formulario(Model model) {
        var morador = moradores.proprio();
        return pagina(model, morador.getCanalCredenciais().name(), morador.getPreferenciaAlertas().name());
    }

    @PostMapping
    String salvar(@RequestParam(defaultValue = "") String canalCredenciais,
            @RequestParam(defaultValue = "") String preferenciaAlertas, Model model) {
        try {
            moradores.alterarPreferencias(CanalCredenciais.valueOf(canalCredenciais), PreferenciaAlertas.valueOf(preferenciaAlertas));
        } catch (IllegalArgumentException exception) { // valor fora das opções: valueOf também lança IllegalArgumentException
            model.addAttribute("erro", "Escolha SMS ou WhatsApp para a senha e SMS, WhatsApp ou ambos para os alertas.");
            return pagina(model, canalCredenciais, preferenciaAlertas);
        }
        return "redirect:/morador/preferencias?salvas";
    }

    private static String pagina(Model model, String canal, String preferencia) {
        model.addAttribute("canalCredenciais", canal);
        model.addAttribute("preferenciaAlertas", preferencia);
        return "acesso/preferencias";
    }
}
