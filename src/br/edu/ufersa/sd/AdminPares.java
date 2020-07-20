package br.edu.ufersa.sd;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

import br.edu.ufersa.sd.contrato.Pares;
import br.edu.ufersa.sd.enums.EnumRecursoId;

/**
 * Singleton de estado da lista de pares conhecidos pela instância
 */
public enum AdminPares {
    INSTANCE();

    private Pares unidPar;
    private List<Pares> ListaPares;
    private Boolean iniciado = false;
    private Map<Instant, Pares> recursoRequerido1;
    private Map<Instant, Pares> recursoRequerido2;

    AdminPares() {
        this.ListaPares = new LinkedList<>();
        try {
            KeyPair keyPar = CryptoUtils.generateRSA();
            this.unidPar = new Pares(keyPar.getPublic(), keyPar.getPrivate());
            System.err.println("Entre com Seu Pares ID: " + this.unidPar.getId());
            this.recursoRequerido1 = new TreeMap<Instant, Pares>();
            this.recursoRequerido2 = new TreeMap<Instant, Pares>();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public Pares getUnidPares() {
        return this.unidPar;
    }

    public void add(Pares p) {
        this.ListaPares.add(p);
        System.err.println("Adicionado par para lista: " + p.getId());
        atualizacaoIniciada(this.ListaPares);
    }

    public void remover(Pares p) {
        this.ListaPares.remove(p);
        System.err.println("Removendo par da lista: " + p.getId());
    }

    public void exibirParesLista() {
        if (this.ListaPares.size() == 0)
            System.out.println("\nA lista está vázia\n");
        else {
            System.out.println("\nPares na lista: \n");
            for (Pares par : this.ListaPares) {
                System.out.println(par.getId() + "\n");
            }
        }
    }

    public List<Pares> getListaPares() {
        return ListaPares;
    }

    public void atualizacaoIniciada(List<Pares> listaPares) {
        if (!iniciado && listaPares.size() + 1 >= ConfigUtil.MINIMUM_PEERS)
            iniciado = true;
    }

    public Map<Instant, Pares> getRecursoRequerido(EnumRecursoId recurso) {
        if (recurso.equals(EnumRecursoId.RECURSO1))
            return recursoRequerido1;
        return recursoRequerido2;
    }

    public boolean isIniciado() {
        return iniciado;
    }
}
