package br.edu.ufersa.sd;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Scanner;

import br.edu.ufersa.sd.contrato.Pares;
import br.edu.ufersa.sd.enums.EnumRecursoId;
import br.edu.ufersa.sd.enums.EnumRecursoSituacao;

public class Application {
    private MensagensHandler messagesHandler;

    public Application() throws IOException {
        this.messagesHandler = new MensagensHandler();
    }

    public static void main(String[] args) {
        try {
            Application app = new Application();
            app.start();
            app.cli();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void start() {
        try {
            this.messagesHandler.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void cli() throws IOException {
        Scanner scanner = new Scanner(System.in);
        boolean sairPrograma = false;
        String solicitacao;
        do {
            System.out.println("\nSolicitação:");
            System.out.println("lista");
            System.out.println("recurso1");
            System.out.println("recurso2");
            
            //Se par tem posse do recurso 1
            if(isRecursoRetido(EnumRecursoId.RECURSO1)) 
                System.out.println("Livre recurso1");
            
            //Se par tem posse do recurso 2
            if(isRecursoRetido(EnumRecursoId.RECURSO2))
                System.out.println("Livre recurso2");
            System.out.println("fim");
            System.out.println("Tipo da seleção: ");
            
            solicitacao = scanner.nextLine();

            switch (solicitacao.trim().toLowerCase()) {
                case "lista": {
                    AdminPares.INSTANCE.exibirParesLista();
                    break;
                }
                case "recurso1": {
                    if (!AdminPares.INSTANCE.isIniciado())
                        System.out.println("Minímo " + ConfigUtil.MINIMUM_PEERS + " pares para iniciar ");
                    else {
                        this.messagesHandler.resourceRequest(EnumRecursoId.RECURSO1);
                    }
                    break;
                }
                case "recurso2": {
                    if (!AdminPares.INSTANCE.isIniciado())
                        System.out.println("Minímo " + ConfigUtil.MINIMUM_PEERS + " pares para iniciar ");
                    else {
                        this.messagesHandler.resourceRequest(EnumRecursoId.RECURSO2);
                    }
                    break;
                }
                case "livre recurso1": {
                	
                	//Checa se par realmente tem posse do recurso01
                    if(isRecursoRetido(EnumRecursoId.RECURSO1)) { 
                        AdminPares.INSTANCE.getUnidPares().getRecursoIniciado().put(EnumRecursoId.RECURSO1, EnumRecursoSituacao.LIBERADO);
                        
                        //Pega o cabeça da fila que tem o recurso
                        Map.Entry <Instant,Pares> Principal = AdminPares.INSTANCE.getRecursoRequerido(EnumRecursoId.RECURSO1).entrySet().iterator().next();
                        
                        //Remove ele proprio da cabeça da fila
                        AdminPares.INSTANCE.getRecursoRequerido(EnumRecursoId.RECURSO1).remove(Principal.getKey());
                        
                        //avisar os outros por multicast que liberou
                        this.messagesHandler.recursoLiberado(EnumRecursoId.RECURSO1);
                    }
                    else {
                        System.out.println("\nVocê não tem o recurso01 para liberar!\n");
                    }
                    break;
                }
                case "livre recurso02": {
                	//Checa par realmente tem posse do recurso02
                    if(isRecursoRetido(EnumRecursoId.RECURSO2)) { 
                        AdminPares.INSTANCE.getUnidPares().getRecursoIniciado().put(EnumRecursoId.RECURSO2, EnumRecursoSituacao.LIBERADO);
                        
                        //Pega o cabeça da fila que tem o recurso
                        Map.Entry <Instant,Pares> Principal = 
                        		AdminPares.INSTANCE.getRecursoRequerido(EnumRecursoId.RECURSO2).entrySet().iterator().next();
                      //Remove ele proprio da cabeça da fila
                        AdminPares.INSTANCE.getRecursoRequerido(EnumRecursoId.RECURSO2).remove(Principal.getKey());
                      //avisar os outros por multicast que liberou
                        this.messagesHandler.recursoLiberado(EnumRecursoId.RECURSO2);
                    }
                    else {
                        System.out.println("\nVocê não possui o recurso02 para liberar\n");
                    }
                    break;
                }
                case "fim": {
                    this.close();
                    sairPrograma = true;
                    break;
                }
                default:
                    System.out.println("seleção inválida ou desconhecida.");
                    break;
            }
        } while (!sairPrograma);
        
        //fechar recurso
        scanner.close();
    }

    private void close() throws IOException {
        this.messagesHandler.fechamento();
    }

    private boolean isRecursoRetido(EnumRecursoId recursoId){
    	//Se par tem posse do recurso
        if(AdminPares.INSTANCE.getUnidPares().getRecursoIniciado().get(recursoId) == EnumRecursoSituacao.MANTIDO) 
            return true;
        return false;
    }
}
