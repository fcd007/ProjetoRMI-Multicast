package br.edu.ufersa.sd.contrato;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import br.edu.ufersa.sd.enums.EnumRecursoId;
import br.edu.ufersa.sd.enums.EnumRecursoSituacao;

@SuppressWarnings("serial")
public class Mensagem implements Serializable {
    public Pares parOrigem;
    public Pares parDestino;
    public MensagemTipo mensagemTipo;
    public EnumRecursoId recurso;
    public EnumRecursoSituacao situacao;
    public byte[] assinatura;

    Instant timestamp;

    public Mensagem(MensagemTipo mensagemTipo, Pares parOrigem) {
        this.mensagemTipo = mensagemTipo;
        this.parOrigem = parOrigem;
    }

    public Mensagem(MensagemTipo mensagemTipo, Pares parOrigem, Pares parDestino) {
        this.mensagemTipo = mensagemTipo;
        this.parOrigem = parOrigem;
        this.parDestino = parDestino;
    }
    
    //Mensagem de requisição de recurso ou mudança na fila
    public Mensagem(
    		MensagemTipo mensagemTipo, 
    		Pares parOrigem, 
    		EnumRecursoId recurso, 
    		Instant timestamp) { 
        this.mensagemTipo = mensagemTipo;
        this.parOrigem = parOrigem;
        this.recurso = recurso;
        this.timestamp = timestamp;
    }

    //Mensagem de liberação de recurso
    public Mensagem(MensagemTipo mensagemTipo, Pares parOrigem, EnumRecursoId recurso) { 
        this.mensagemTipo = mensagemTipo;
        this.parOrigem = parOrigem;
        this.recurso = recurso;
    }

    //Mensagem de resposta a requisição de recurso
    public Mensagem(
    		MensagemTipo 
    		mensagemTipo, 
    		Pares parOrigem, 
    		Pares parDestino, 
    		EnumRecursoId recurso, 
    		EnumRecursoSituacao situacao, 
    		Instant timestamp) {
        this.mensagemTipo = mensagemTipo;
        this.parOrigem = parOrigem;
        this.parDestino = parDestino;
        this.recurso = recurso;
        this.situacao = situacao;
        this.timestamp = timestamp;
    }

    //Mensagem de requisição de recurso ou mudança na fila
    public Mensagem(
    		MensagemTipo mensagemTipo, 
    		Pares parOrigem, 
    		EnumRecursoId recurso, 
    		Instant timestamp, 
    		byte[] assinatura) { 
        this.mensagemTipo = mensagemTipo;
        this.parOrigem = parOrigem;
        this.recurso = recurso;
        this.timestamp = timestamp;
        this.assinatura = assinatura;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Mensagem messagem = (Mensagem) o;
        return Objects.equals(parOrigem, messagem.parOrigem) &&
                Objects.equals(parDestino, messagem.parDestino) &&
                mensagemTipo == messagem.mensagemTipo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parOrigem, parDestino, mensagemTipo);
    }

    @Override
    public String toString() {
        return String.format("Messagem { parOrigem=%s, parDestino=%s, mensagemTipo=%s }", parOrigem, parDestino, mensagemTipo);
    }

    public EnumRecursoId getRecurso() {
        return recurso;
    }

    public Pares getParDestino() {
        return parDestino;
    }

    public Pares getParOrigem() {
        return parOrigem;
    }

    public EnumRecursoSituacao getSituacao() {
        return situacao;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public enum MensagemTipo {
        REQUISICAO_CUMPRI, 
        RESPOSTA_CUMPRI, 
        REQUISICAO_DEIXAR, 
        RESPOSTA_DEIXAR, 
        REQUISICAO_RECURSO, 
        REQUISICAO_RESPOSTA, 
        RECURSO_LIBERAR, 
        FILA_ADD, 
        FILA_REMOVE
    }
}
