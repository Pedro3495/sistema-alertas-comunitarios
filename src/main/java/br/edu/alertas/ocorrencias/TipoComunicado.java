package br.edu.alertas.ocorrencias;

// RF010 (alerta) e aviso de alteração (RF009 regra 4 / RF011 regra 4). O aviso de encerramento virá com o RF012.
public enum TipoComunicado {
    ALERTA("Alerta"), ALTERACAO("Aviso de alteração");
    private final String rotulo;
    TipoComunicado(String rotulo) { this.rotulo = rotulo; }
    public String getRotulo() { return rotulo; }
}
