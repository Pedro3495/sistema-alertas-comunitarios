package br.edu.alertas.ocorrencias;

// RF010 (alerta), aviso de alteração (RF009 regra 4 / RF011 regra 4) e aviso de encerramento (RF012 regras 2 e 3).
public enum TipoComunicado {
    ALERTA("Alerta"), ALTERACAO("Aviso de alteração"), ENCERRAMENTO("Aviso de encerramento");
    private final String rotulo;
    TipoComunicado(String rotulo) { this.rotulo = rotulo; }
    public String getRotulo() { return rotulo; }
}
