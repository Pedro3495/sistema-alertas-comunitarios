package br.edu.alertas.acesso;

import br.edu.alertas.moradores.AcessoMorador;
import br.edu.alertas.moradores.AutenticacaoMoradorService;
import br.edu.alertas.moradores.RecuperacaoAcessoService;
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
    private static final String CELULAR_RECUPERACAO = "CELULAR_RECUPERACAO";
    private final AutenticacaoMoradorService acessoService;
    private final RecuperacaoAcessoService recuperacao;

    public AcessoMoradorController(AutenticacaoMoradorService acessoService, RecuperacaoAcessoService recuperacao) {
        this.acessoService = acessoService;
        this.recuperacao = recuperacao;
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

    // RF007: confirmação do telefone cadastrado e envio do código pelo canal de credenciais.
    @GetMapping("/recuperar")
    String recuperar() {
        return "acesso/recuperar";
    }

    @PostMapping("/recuperar")
    // Decisão de Pedro (23/11/2026): mesma resposta para qualquer telefone, mesmo com falha de envio (RS003).
    String enviarCodigo(@RequestParam(defaultValue = "") String celular, HttpServletRequest request) {
        String digitos = celular.replaceAll("[^0-9]", "");
        recuperacao.solicitarCodigo(digitos);
        request.getSession().setAttribute(CELULAR_RECUPERACAO, digitos);
        return "redirect:/morador/recuperar/codigo";
    }

    @GetMapping("/recuperar/codigo")
    String informarCodigo(HttpServletRequest request) {
        var session = request.getSession(false);
        return session == null || session.getAttribute(CELULAR_RECUPERACAO) == null
                ? "redirect:/morador/recuperar" : "acesso/redefinir-senha";
    }

    @PostMapping("/recuperar/codigo")
    String redefinirSenha(@RequestParam(defaultValue = "") String codigo, @RequestParam(defaultValue = "") String novaSenha,
            HttpServletRequest request, Model model) {
        var session = request.getSession(false);
        Object celular = session == null ? null : session.getAttribute(CELULAR_RECUPERACAO);
        if (celular == null) { return "redirect:/morador/recuperar"; }
        try {
            if (!recuperacao.redefinirSenha(celular.toString(), codigo, novaSenha)) {
                model.addAttribute("erroCodigo", true);
                return "acesso/redefinir-senha";
            }
        } catch (IllegalArgumentException exception) {
            model.addAttribute("erroSenha", "Use no mínimo 8 caracteres.");
            return "acesso/redefinir-senha";
        }
        session.removeAttribute(CELULAR_RECUPERACAO);
        return "redirect:/morador/entrar?senhaAlterada";
    }

    @GetMapping
    String inicio() {
        return "acesso/morador";
    }
}
