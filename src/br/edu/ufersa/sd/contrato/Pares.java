package br.edu.ufersa.sd.contrato;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import br.edu.ufersa.sd.CryptoUtils;
import br.edu.ufersa.sd.enums.EnumRecursoId;
import br.edu.ufersa.sd.enums.EnumRecursoSituacao;

import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@SuppressWarnings("serial")
public class Pares implements Serializable {
    private String id;
    private PublicKey publicKey;
    private transient PrivateKey privateKey;
    private Map<EnumRecursoId, EnumRecursoSituacao> recursoSituacaoAtual;
    private byte[] assinatura;

    public Pares(PublicKey publicKey, PrivateKey privateKey) {
        this.id = UUID.randomUUID().toString().substring(0, 4);
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.recursoSituacaoAtual = new HashMap<EnumRecursoId, EnumRecursoSituacao>();

        byte[] idBytes = this.id.getBytes();
        try {
            this.assinatura = CryptoUtils.sign(this.privateKey, idBytes);
        } catch (InvalidKeyException | 
        		NoSuchPaddingException | 
        		IllegalBlockSizeException | 
        		BadPaddingException | 
        		NoSuchAlgorithmException e) 
        {	
            e.printStackTrace();
        }

        //Adiciona estados iniciais de cada um dos 2 recursos
        this.recursoSituacaoAtual.put(EnumRecursoId.RECURSO1,
                EnumRecursoSituacao.LIBERADO);
        this.recursoSituacaoAtual.put(EnumRecursoId.RECURSO2,
                EnumRecursoSituacao.LIBERADO);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getId() {
        return id;
    }

    public Map<EnumRecursoId, EnumRecursoSituacao> getRecursoIniciado() {
        return recursoSituacaoAtual;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pares par = (Pares) o;
        return Objects.equals(id, par.id) &&
                Objects.equals(publicKey, par.publicKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, publicKey, privateKey);
    }

    @Override
    public String toString() {
        return String.format("Par { id='%s'}", id);
    }

    public byte[] getAssinatura() {
        return assinatura;
    }
}
