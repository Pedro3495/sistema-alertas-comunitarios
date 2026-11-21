package br.edu.alertas.gestao;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AcessoAdministradorController {
    @GetMapping("/admin/entrar")
    String entrar(HttpServletRequest request, Model model) {
        var session = request.getSession(false);
        if (session != null) {
            model.addAttribute("loginTentado", session.getAttribute("LOGIN_ADMIN_TENTADO"));
            session.removeAttribute("LOGIN_ADMIN_TENTADO");
        }
        return "gestao/entrar";
    }
}
