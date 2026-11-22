package br.edu.alertas.acesso;

import br.edu.alertas.moradores.AcessoMorador;
import br.edu.alertas.moradores.AutenticacaoMoradorService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/morador")
public class AcessoMoradorController {
    public static final String CPF_TENTADO = "CPF_MORADOR_TENTADO";
    private final AutenticacaoMoradorService acessoService;

    public AcessoMoradorController(AutenticacaoMoradorService acessoService) {
        this.acessoService = acessoService;
    }

    @ModelAttribute("base")
    String base() {
        return "/morador";
    }

    @GetMapping("/entrar")
    String entrar(HttpServletRequest request, Model model) {
        var session = request.getSession(false);
        if (session != null) {
            model.addAttribute("cpfTentado", session.getAttribute(CPF_TENTADO));
            session.removeAttribute(CPF_TENTADO);
        }
        return "acesso/morador-entrar";
    }

    @GetMapping("/trocar-senha")
    String trocarSenha() {
        return "acesso/trocar-senha";
    }

    @PostMapping("/trocar-senha")
    String salvarSenha(@AuthenticationPrincipal AcessoMorador acesso,
            @RequestParam(defaultValue = "") String novaSenha, HttpServletRequest request, Model model) {
        try {
            acessoService.trocarSenha(acesso, novaSenha);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("erroSenha", "Use no mínimo 8 caracteres.");
            return "acesso/trocar-senha";
        } catch (IllegalStateException exception) {
            AcessoController.encerrarSessao(request);
            return "redirect:/morador/entrar?expirado";
        }
        AcessoController.encerrarSessao(request);
        return "redirect:/morador/entrar?senhaAlterada";
    }

    @GetMapping
    String inicio() {
        return "acesso/morador";
    }
}
