package br.edu.alertas.ocorrencias;

public class ErroOcorrencia extends IllegalArgumentException {
    private final String campo;
    public ErroOcorrencia(String campo, String mensagem) { super(mensagem); this.campo = campo; }
    public String getCampo() { return campo; }
}
