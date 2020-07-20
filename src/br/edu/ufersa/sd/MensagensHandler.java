package br.edu.ufersa.sd;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import br.edu.ufersa.sd.contrato.Mensagem;
import br.edu.ufersa.sd.contrato.Pares;
import br.edu.ufersa.sd.enums.EnumRecursoId;
import br.edu.ufersa.sd.enums.EnumRecursoSituacao;


/**
 * Classe de troca de mensagens da instância
 */
class MensagensHandler {
    private final AsyncMessagesHandler asyncMessagesHandler;
    private AtomicReference<MulticastSocket> mSocket;
    private AtomicReference<Instant> UltimaSolicitacaoRecurso;
    private List<Mensagem> requisicaoRespostas;

    MensagensHandler() throws IOException {
        mSocket = new AtomicReference<>();
        mSocket.set(new MulticastSocket(ConfigUtil.DEFAULT_PORT));
        asyncMessagesHandler = new AsyncMessagesHandler();
        UltimaSolicitacaoRecurso = new AtomicReference<Instant>();
    }

    /**
     * Método para enviar mensagem via socket Multicast
     * @param datagramSocket
     * @param mensagem
     */
    private static void envioMensagem(MulticastSocket datagramSocket, Mensagem mensagem) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(mensagem);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            DatagramPacket pacoteData = new DatagramPacket(byteArray, byteArray.length, 
            		InetAddress.getByName(ConfigUtil.DEFAULT_HOST), ConfigUtil.DEFAULT_PORT);
            datagramSocket.send(pacoteData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Método de inicialização do fluxo onde:
     * - o socket se registra no grupo multicast;
     * - inicia a thread de tratamento assíncrono de mensagens;
     * - manda mensagem de cumprimento a todos os pares.
     *
     * @throws IOException
     */
    void start() throws IOException {
        this.mSocket.get().joinGroup(InetAddress.getByName(ConfigUtil.DEFAULT_HOST));
        this.asyncMessagesHandler.start();
        this.cumprimento();
    }

    /**
     * Método de encerramento do fluxo onde:
     * - manda mensagem de adeus a todos os pares;
     * - interrompe a thread de tratamento assíncrono de mensagens;
     * - o socket sai do grupo multicast;
     * - o socket multicast é encerrado.
     */
    void close() throws IOException {
        this.mSocket.get().leaveGroup(InetAddress.getByName(ConfigUtil.DEFAULT_HOST));
        this.mSocket.get().close();
    }

    /**
     * método que trata a liberação de um recurso.
     */
    public void recursoLiberado(EnumRecursoId recursoId) {
        envioMensagem(this.mSocket.get(), 
        		new Mensagem(Mensagem.MensagemTipo.RECURSO_LIBERAR, 
        		AdminPares.INSTANCE.getUnidPares(), recursoId));
    }

    /**
     * método que trata a requisição de um request.
     */
    public void resourceRequest(EnumRecursoId recursoID) {
        // Atualiza o estado desse peer sobre esse recurso para REQUISITADO
        AdminPares.INSTANCE.getUnidPares().getRecursoIniciado().put(recursoID, EnumRecursoSituacao.REQUERIDO);

        UltimaSolicitacaoRecurso.set(Instant.now());

        requisicaoRespostas = new LinkedList<Mensagem>();

        // Envia uma mensagem de requisição do recurso
        mRecursoRequisitado(recursoID, UltimaSolicitacaoRecurso.get());

        while (requisicaoRespostas.size() != AdminPares.INSTANCE.getListaPares().size()) {
        	//Segundos entre requisição e resposta
            if (ChronoUnit.SECONDS.between(UltimaSolicitacaoRecurso.get(), Instant.now()) >= ConfigUtil.MAXIMUM_DELTA_SEC) { 
                List<Pares> paresRespostasRecebidas = new LinkedList<Pares>();
                List<Pares> paresNRespostasRecebidas = AdminPares.INSTANCE.getListaPares();
                for (Mensagem mgs : requisicaoRespostas) {
                    paresRespostasRecebidas.add(mgs.getParDestino());
                }
                
                //removendo da lista de pares aqueles que responderam nós temos os que não responderam
                for (Pares p : paresRespostasRecebidas) {
                    paresNRespostasRecebidas.remove(p); 
                }
                // agora removemos esses pares
                for (Pares p : paresNRespostasRecebidas) {
                    envioMensagem(this.mSocket.get(), new Mensagem(Mensagem.MensagemTipo.REQUISICAO_DEIXAR, p));
                }
                break;
            }
        }
        // Excluimos todos os ausentes, agora vereficamos as respostas
        Boolean areAllReleased = requisicaoRespostas.stream().allMatch(requestAnswers -> requestAnswers.getSituacao().equals(EnumRecursoSituacao.LIBERADO));
        if (areAllReleased) {
            // Se todos responderam released, esse peer pode pegar o recurso
            AdminPares.INSTANCE.getUnidPares().getRecursoIniciado().put(recursoID, EnumRecursoSituacao.MANTIDO);
            putOnQueue(recursoID, UltimaSolicitacaoRecurso.get());
            System.out.println("Mudando " + recursoID + " para MANTIDO");
          //Se algum está em MANTIDO
        } else if (requisicaoRespostas.stream().anyMatch(respostaRequisicao -> respostaRequisicao.getSituacao().equals(EnumRecursoSituacao.MANTIDO))) { 
            System.out.println("\nO Recurso é utilizado por um outro processo, coloque Solicitação na fila");
            
          //Se teve uma batalha de menor tempo de indicação, o que perdeu 
          //vai vir como liberada porque liberou, entao tem que mudar pra procurada novamente
            AdminPares.INSTANCE.getUnidPares().getRecursoIniciado().put(recursoID, EnumRecursoSituacao.REQUERIDO);
            putOnQueue(recursoID, UltimaSolicitacaoRecurso.get());
        }
    }

    /**
     * método que constrói e envia mensagem de cumprimento aos pares.
     */
    private void cumprimento() {
        envioMensagem(this.mSocket.get(), 
        		new Mensagem(Mensagem.MensagemTipo.REQUISICAO_CUMPRI, 
        				AdminPares.INSTANCE.getUnidPares()));
    }

    /**
     * método que constrói e envia mensagem de mudança na fila de um resource.
     */
    private void mFilaAdiciona(EnumRecursoId recursoID, Instant timestamp) {
        envioMensagem(this.mSocket.get(), 
        		new Mensagem(Mensagem.MensagemTipo.FILA_ADD, 
        				AdminPares.INSTANCE.getUnidPares(), 
        				recursoID, timestamp));
    }

    /**
     * método que constrói e envia mensagem de saída aos pares.
     */
    void fechamento() {
        if (AdminPares.INSTANCE.getListaPares().size() == 0) {
            try {
                this.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            this.liberaFila(EnumRecursoId.RECURSO1);
            this.liberaFila(EnumRecursoId.RECURSO2);
            envioMensagem(this.mSocket.get(), 
            		new Mensagem(Mensagem.MensagemTipo.REQUISICAO_DEIXAR, 
            				AdminPares.INSTANCE.getUnidPares()));
        }
    }

    public void liberaFila(EnumRecursoId recursoID) {
        Map<Instant, Pares> filaRecursos = AdminPares.INSTANCE.getRecursoRequerido(recursoID);
        Map.Entry<Instant, Pares> Principal;
        
        //Verifica se n ta vazia
        if (!filaRecursos.isEmpty()) { 
            Iterator<Map.Entry<Instant, Pares>> iterator = filaRecursos.entrySet().iterator();
            
            //Pega nova cabeça da fila
            Principal = iterator.next();
            Pares par = AdminPares.INSTANCE.getUnidPares();
            
            //Se o cabeça da fila for a gente, muda pra MANTIDO
            if (Principal.getValue().equals(par)) { 
                this.recursoLiberado(recursoID);
            } else {
                while (iterator.hasNext()) {
                    Map.Entry<Instant, Pares> i = iterator.next();

                    // está no corpo da fila como interessado
                    if(i.getValue().equals(par)) {
                        envioMensagem(mSocket.get(), 
                        		new Mensagem(Mensagem.MensagemTipo.FILA_REMOVE, 
                        				AdminPares.INSTANCE.getUnidPares(),
                        				recursoID, i.getKey()));
                    }
                }
            }
        }
    }

    /**
     * método que constrói e envia mensagem de requisição de recurso aos pares.
     */
    private void mRecursoRequisitado(EnumRecursoId recursoID, Instant timestamp) {
        envioMensagem(this.mSocket.get(), 
        		new Mensagem(Mensagem.MensagemTipo.REQUISICAO_RECURSO, 
        				AdminPares.INSTANCE.getUnidPares(), 
        				recursoID, timestamp, AdminPares.INSTANCE.getUnidPares().getAssinatura()));
    }

    /**
     * método que verifica se n teve 2 peers requisitando o recurso ao msm instante.
     */
    private void putOnQueue(EnumRecursoId recursoID, Instant timestamp) {
    	
    	//Se não é duplicado, adiciona sem problemas
        if (!AdminPares.INSTANCE.getRecursoRequerido(recursoID).containsKey(timestamp)) 
        //Coloca na fila Se ja tem um lá
        	AdminPares.INSTANCE.getRecursoRequerido(recursoID).put(timestamp, AdminPares.INSTANCE.getUnidPares());
        else {
            timestamp = timestamp.plus(1, ChronoUnit.NANOS);
            AdminPares.INSTANCE.getRecursoRequerido(recursoID).put(timestamp, AdminPares.INSTANCE.getUnidPares());
        }
        
      //avisa outros peers para atualizarem sua fila
        mFilaAdiciona(recursoID, UltimaSolicitacaoRecurso.get());
    }

    /**
     * Handler assíncrono de eventos no grupo multicast.
     */
    class AsyncMessagesHandler extends Thread {
    	
        @Override
        public void run() {
            try {
            	//criando o buffer de 
                byte[] buffer = new byte[4096];
                
                // enquanto o socket não for fechado no foreground, rode...
                while (!mSocket.get().isClosed()) { 
                	
                	 // cria referencia do pacote
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                    
                    // recebe dados dentro de pacote
                    mSocket.get().receive(pacote);
                    
                    // armazena bytes do pacote dentro de array
                    byte[] data = pacote.getData();
                    // cria fluxo de bytes
                    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data); 
                    
                    // cria input stream de objeto
                    ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream); 
                    
                    // converte array de bytes do pacote em objeto
                    Mensagem mensagemRecebida = (Mensagem) objectInputStream.readObject(); 

                    System.err.println("Mensagem recebida: " + mensagemRecebida);

                    // se a mensagem capturada é do própria instancia, pule
                    if (mensagemRecebida.parOrigem.equals(AdminPares.INSTANCE.getUnidPares()))
                        continue;
                    
                    // verifica tipo da mensagem
                    switch (mensagemRecebida.mensagemTipo) { 
                    	
                    	// mensagem de requisição de cumprimento
                        case REQUISICAO_CUMPRI: { 
                            handleGreetingRequest(mensagemRecebida);
                            break;
                        }
                        // mensagem de resposta cumprimento
                        case RESPOSTA_CUMPRI: { 
                            handleGreetingResponse(mensagemRecebida);
                            break;
                        }
                        // mensagem de "saindo - deixando" do par
                        case REQUISICAO_DEIXAR: {
                            handleLeaveRequest(mensagemRecebida);
                            break;
                        }
                        case RESPOSTA_DEIXAR: {
                            handleLeaveResponse(mensagemRecebida);
                            break;
                        }
                        // mensagem de requisição de recurso
                        case REQUISICAO_RECURSO: {
                            handleResourceRequest(mensagemRecebida);
                            break;
                        }
                        // mensagem de resposta a req. de recurso
                        case REQUISICAO_RESPOSTA: { 
                            handleResourceResponse(mensagemRecebida);
                            break;
                        }
                        //mensagem para liberar recurso
                        case RECURSO_LIBERAR: {
                            handleResourceRelease(mensagemRecebida);
                            break;
                        }
                        //mensagem para adicionar na fila requisição de recurso
                        case FILA_ADD: {
                            handleFilaAdd(mensagemRecebida);
                            break;
                        }
                        //mensagem para remover da fila requisição de recurso
                        case FILA_REMOVE: {
                            handleFilaRemove(mensagemRecebida);
                            break;
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        
        // se mensagem é destinada a esta instância adicione quem mandou a mensagem.
        private void handleResourceResponse(Mensagem mensagemRecebida) {
            if (!mensagemRecebida.parDestino.equals(AdminPares.INSTANCE.getUnidPares())) 
                return;

            System.out.println("Receive response from " + mensagemRecebida.parOrigem);
            requisicaoRespostas.add(mensagemRecebida);
        }

        private void handleResourceRequest(Mensagem mensagemRecebida) {
            if (!verifySignature(mensagemRecebida)) return;
            
            // Guarda qual dos dois recursos é o request
            EnumRecursoId recursoRequisitado = mensagemRecebida.getRecurso();
            //verifica em que situação o estado está para este peer
            EnumRecursoSituacao EstadoRequisicaoRecurso = 
            		AdminPares.INSTANCE.getUnidPares().getRecursoIniciado().get(recursoRequisitado); 
            // envia mensagem de resposta a requisição
            envioMensagem(mSocket.get(), new Mensagem(Mensagem.MensagemTipo.REQUISICAO_RESPOSTA, 
            		AdminPares.INSTANCE.getUnidPares(), mensagemRecebida.parOrigem, recursoRequisitado, 
            		EstadoRequisicaoRecurso, Instant.now())); 
            System.out.println("RECURSO_REQUISITADO para  " + recursoRequisitado + "\n");
        }

        private boolean verifySignature(Mensagem mensagemRecebida) {
            Pares ParProp = mensagemRecebida.parOrigem;
            Optional<Pares> primeiro = AdminPares.INSTANCE.getListaPares().stream().filter(par -> par.getId().equals(ParProp.getId())).findFirst();
            PublicKey truePubKey = primeiro.get().getPublicKey();
            try {
                CryptoUtils.checkSignature(truePubKey, mensagemRecebida.assinatura);
                System.err.println(String.format("assinatura válida:  %s ", mensagemRecebida.parOrigem.getId()));
            } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | NoSuchAlgorithmException | NoSuchPaddingException e) {
                System.err.println(String.format("Assinatura inválida : %s ", mensagemRecebida.parOrigem.getId()));
                return false;
            }
            return true;
        }

        private void handleResourceRelease(Mensagem mensagemRecebida) {
        	// Guarda qual dos dois recursos é o liberado
            EnumRecursoId recursoRequisitado = mensagemRecebida.getRecurso();
            
            //Pega o cabeça da fila
            Map.Entry<Instant, Pares> Principal = AdminPares.INSTANCE.getRecursoRequerido(recursoRequisitado).entrySet().iterator().next();
            
            //Remove o cabeça da fila que tinha o recurso
            AdminPares.INSTANCE.getRecursoRequerido(recursoRequisitado).remove(Principal.getKey());
            
            //Verifica se n ta vazia
            if (!AdminPares.INSTANCE.getRecursoRequerido(recursoRequisitado).isEmpty()) { 
            	
            	//Pega novo cabeça da fila
                Principal = AdminPares.INSTANCE.getRecursoRequerido(recursoRequisitado).entrySet().iterator().next();
                
                //Se o cabeça da fila for a gente, muda pra MANTIDO
                if (Principal.getValue().equals(AdminPares.INSTANCE.getUnidPares())) { 
                    AdminPares.INSTANCE.getUnidPares().getRecursoIniciado().put(recursoRequisitado, EnumRecursoSituacao.MANTIDO);
                    System.out.println("Está agora com " + recursoRequisitado);
                }
            }
        }

        private void handleLeaveRequest(Mensagem mensagemRecebida) {
            // remova o par da lista de pares online
            AdminPares.INSTANCE.remover(mensagemRecebida.parOrigem);
            envioMensagem(mSocket.get(), new Mensagem(Mensagem.MensagemTipo.RESPOSTA_DEIXAR, 
            		AdminPares.INSTANCE.getUnidPares(), 
            		mensagemRecebida.parOrigem));
        }

        private void handleLeaveResponse(Mensagem messageReceived) {
            if (!messageReceived.parDestino.equals(AdminPares.INSTANCE.getUnidPares())) return;
            	AdminPares.INSTANCE.getListaPares().remove(messageReceived.parOrigem);
            	
            if (AdminPares.INSTANCE.getListaPares().size() == 0) {
                try {
                    close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleGreetingResponse(Mensagem mensagemRecebida) {
            // se mensagem é destinada a esta instância adicione quem mandou a mensagem.
            if (!mensagemRecebida.parDestino.equals(AdminPares.INSTANCE.getUnidPares())) return;
            	AdminPares.INSTANCE.add(mensagemRecebida.parOrigem);
        }

        
        private void handleGreetingRequest(Mensagem mensagemRecebida) {
            AdminPares.INSTANCE.add(mensagemRecebida.parOrigem);
            
            // envia mensagem de auto-apresentação destinada ao novo par
            envioMensagem(mSocket.get(), new Mensagem(Mensagem.MensagemTipo.RESPOSTA_CUMPRI, 
            		AdminPares.INSTANCE.getUnidPares(), 
            		mensagemRecebida.parOrigem)); 
        }

        private void handleFilaAdd(Mensagem mensagemRecebida) {
        	// Guarda sobre qual dos dois recursos é a msg
            EnumRecursoId recurso = mensagemRecebida.getRecurso();
            // Qual peer enviou a msg
            Pares par = mensagemRecebida.getParOrigem();
            //Qual timestamp
            Instant time = mensagemRecebida.getTimestamp(); 
            //adiciona a fila
            AdminPares.INSTANCE.getRecursoRequerido(recurso).put(time, par);
        }

        private void handleFilaRemove(Mensagem mensagemRecebida) {
        	// Guarda sobre qual dos dois recursos é a msg
            EnumRecursoId recurso = mensagemRecebida.getRecurso();
            // Qual peer enviou a msg
            Pares par = mensagemRecebida.getParOrigem();
            //Qual timestamp
            Instant time = mensagemRecebida.getTimestamp();
            //adiciona a fila
            AdminPares.INSTANCE.getRecursoRequerido(recurso).remove(time, par);
        }
    }
}