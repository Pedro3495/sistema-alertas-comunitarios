package br.edu.alertas.ocorrencias;

public enum TipoOcorrencia {
    DESASTRE("Desastre"), OBRA("Obra"), BARREIRA_CONTENCAO("Situação relacionada a barreira de contenção"), OUTROS("Outros");
    private final String rotulo;
    TipoOcorrencia(String rotulo) { this.rotulo = rotulo; }
    public String getRotulo() { return rotulo; }
}
