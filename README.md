# ProjetoRMI-Multicast

Projeto desenvolvido para disciplina de Sistemas Distribuídos - Java - RMI - Algoritmos distribuídos
## UNIVERSIDADE FEDERAL RURAL DO SEMIÁRIDO - UFERSA
Centro de Ciências Exatas e Naturais - CCEN
Departamento de Computação - DC
Disciplina: Sistemas Distribuídos
Prof.: Paulo Henrique Lopes Silva
Prática offline 5

## Pratica offline5
Ricart-Agrawala
# Sobre o trabalho
Arquitetura de Processos Pares, Comunicação em Grupo, Exclusão Mútua e
Segurança.

## Instruções: Implemente um algoritmo para exclusão mútua distribuída.
## Alternativas:
* Algoritmo distribuído (Ricart-Agrawala) ou; 
* Algoritmo token ring. *
Protocolo de nível de aplicação para executar uma seção crítica:
* entrar(): verifica a possibilidade de entrar na seção crítica (bloqueia, se necessário). 
* acessarRecurso(): acessa recurso compartilhado. 
* Considere um arquivo em que processos fazem operações de leitura e escrita.
* liberarRecurso(): sair da seção crítica. 
## Requisitos básicos que o algoritmo deve ter:
* Segurança: no máximo um processo por vez pode ser executado na seção crítica. 
* Imparcialidade: evitar inanição de processos. 
* Número mínimo de processos para testar o algoritmo: 4.
