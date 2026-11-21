package br.edu.alertas.auditoria;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@Controller
public class AuditoriaController {
    private final AuditoriaService auditoria;
    public AuditoriaController(AuditoriaService auditoria) { this.auditoria = auditoria; }
    @GetMapping({"/auditoria", "/admin/auditoria"})
    String consultar(@RequestParam(defaultValue = "") String responsavel,
            @RequestParam(defaultValue = "") String acao, @RequestParam(defaultValue = "") String inicio,
            @RequestParam(defaultValue = "") String fim, HttpServletRequest request, Model model) {
        model.addAttribute("responsavel", responsavel); model.addAttribute("acao", acao);
        model.addAttribute("inicio", inicio); model.addAttribute("fim", fim);
        model.addAttribute("opcoes", auditoria.responsaveis());
        model.addAttribute("administrador", request.getServletPath().startsWith("/admin/"));
        try {
            model.addAttribute("registros", auditoria.pesquisar(responsavel, acao, instante(inicio), instante(fim)));
        } catch (DateTimeParseException exception) {
            model.addAttribute("erro", "Informe datas e horários válidos em UTC.");
            model.addAttribute("registros", List.of());
        } catch (IllegalArgumentException exception) {
            model.addAttribute("erro", exception.getMessage()); model.addAttribute("registros", List.of());
        }
        return "gestao/auditoria";
    }
    private Instant instante(String valor) { return valor.isBlank() ? null : LocalDateTime.parse(valor).toInstant(ZoneOffset.UTC); }
}
