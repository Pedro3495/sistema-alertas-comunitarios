package br.edu.alertas.acesso;

import br.edu.alertas.representantes.AcessoRepresentante;
import br.edu.alertas.representantes.AutenticacaoRepresentanteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AcessoController {
    public static final String LOGIN_TENTADO = "LOGIN_TENTADO";
    private final AutenticacaoRepresentanteService acessoService;

    public AcessoController(AutenticacaoRepresentanteService acessoService) {
        this.acessoService = acessoService;
    }

    @GetMapping("/entrar")
    String entrar(HttpServletRequest request, Model model) {
        var session = request.getSession(false);
        if (session != null) {
            model.addAttribute("loginTentado", session.getAttribute(LOGIN_TENTADO));
            session.removeAttribute(LOGIN_TENTADO);
        }
        return "acesso/entrar";
    }

    @GetMapping("/trocar-senha")
    String trocarSenha() {
        return "acesso/trocar-senha";
    }

    @PostMapping("/trocar-senha")
    String salvarSenha(@AuthenticationPrincipal AcessoRepresentante acesso,
            @RequestParam(defaultValue = "") String novaSenha, HttpServletRequest request, Model model) {
        try {
            acessoService.trocarSenha(acesso, novaSenha);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("erroSenha", "Use no mínimo 8 caracteres.");
            return "acesso/trocar-senha";
        } catch (IllegalStateException exception) {
            encerrarSessao(request);
            return "redirect:/entrar?expirado";
        }
        encerrarSessao(request);
        return "redirect:/entrar?senhaAlterada";
    }

    @GetMapping("/representante")
    String representante(@AuthenticationPrincipal AcessoRepresentante acesso, Model model) {
        model.addAttribute("login", acesso.login());
        return "acesso/representante";
    }

    private void encerrarSessao(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
