package br.edu.alertas.gestao;

import br.edu.alertas.moradores.CaixaSaidaSimulada;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/** Demonstração local: mostra ao administrador as mensagens do envio simulado. 404 quando desligada. */
@Controller
class SimuladorMensagensController {
    private final CaixaSaidaSimulada caixa;

    SimuladorMensagensController(CaixaSaidaSimulada caixa) { this.caixa = caixa; }

    @GetMapping("/admin/simulador/mensagens")
    String mensagens(Model model) {
        if (!caixa.ativa()) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        model.addAttribute("mensagens", caixa.mensagens());
        return "gestao/simulador";
    }
}
