# 🧠 Dynamic Study Planner API

> Um motor de otimização de planos de estudo construído com Inteligência Artificial (Algoritmos Genéticos) e fundamentado nas Teorias da Ciência da Aprendizagem.

![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-DDD%20%7C%20Stateless-blueviolet?style=for-the-badge)

## 📖 Sobre o Projeto

O **Dynamic Study Planner** resolve o problema crônico dos planos de estudo estáticos e genéricos ("tamanho único") utilizados na preparação para exames de alta performance (concursos públicos, vestibulares, certificações). 

O sistema opera através de uma **API RESTful Stateless** que recebe uma "fotografia" do estado atual do aluno (disponibilidade de tempo e lacunas de conhecimento) e as regras do edital da prova. Em segundos, ele processa uma otimização estratégica e um agendamento tático diário, gerando um plano realista e matematicamente superior.

O grande diferencial deste projeto é que a sua função objetivo (Fitness) e as suas heurísticas de agendamento não são arbitrárias, mas sim a tradução computacional direta de três teorias educacionais consagradas.

## 🔬 Fundamentação Teórica

A inteligência do sistema é baseada no "Tripé da Aprendizagem Eficiente":

1. **Teoria da Aprendizagem Significativa (David Ausubel):** O algoritmo foca na construção de pontes cognitivas (*subsunçores*). A prioridade de estudo é calculada cruzando o peso da disciplina no edital com as **lacunas de conhecimento (`knowledgeGaps`)** declaradas pelo aluno.
2. **A Curva de Esquecimento (Hermann Ebbinghaus):** Implementada nativamente através da `ReviewFocusedStrategy`. O sistema atua com **Repetição Espaçada**, priorizando estrategicamente a revisão da disciplina que está há mais tempo sem ser estudada na agenda diária.
3. **Teoria da Carga Cognitiva (John Sweller):** A `CognitiveLoadBalancingStrategy` utiliza o algoritmo de *Two Pointers Interleaving* para alternar blocos de estudo de alta e baixa densidade (`cognitiveLoad`), respeitando um orçamento de energia mental diário máximo calculado dinamicamente pela IA para evitar o esgotamento (*burnout*).

## 🚀 Principais Funcionalidades

* **Otimização Evolucionária:** Utiliza um Algoritmo Genético construído do zero (com Seleção por Torneio, Mutação *Creep* e *Crossover* Híbrido) para encontrar a melhor alocação de esforço em meses de estudo.
* **Agendamento Dinâmico:** Converte o plano macro em uma agenda diária tática, baseada na disponibilidade exata de horas do estudante por dia da semana.
* **Auto-Calibrável:** O sistema não exige do usuário a inserção de métricas técnicas (como limite de horas de esforço mental). A classe `CognitiveLoadCalculator` infere o limite de resistência diária cruzando a dificuldade do edital com a confiança do aluno.
* **Arquitetura Resiliente e *Stateless*:** Desenhado para a nuvem. Não guarda estado, o que significa que o aluno pode atualizar as suas lacunas semanalmente e receber um plano 100% re-otimizado ("re-planejamento do zero"), garantindo adaptabilidade total ao longo do tempo.

## 🛠️ Arquitetura e Tecnologias

Este projeto foi construído priorizando um design limpo e extensível:

* **Java 17+ & Spring Boot 3:** Base do projeto.
* **Domain-Driven Design (DDD):** Domínio isolado de regras de framework.
* **Design Patterns Aplicados:**
  * **Strategy:** Permite compor e alterar o comportamento do gerador de agenda e operadores genéticos.
  * **Injeção de Dependências (IoC):** Amplo uso dos containers do Spring para gerir calculadoras e serviços.

### 📂 Estrutura do Projeto

A organização de diretórios reflete a separação clara de responsabilidades:

```text
src/main/java/com/ia/project/dynamicstudyplanner/
├── api/                             # Camada de entrada e saída (REST)
│   ├── controller/
│   │   └── OptimizerController.java
│   ├── dto/
│   └── mapper/
├── config/                          # Configurações globais e de segurança
│   └── SecurityConfig.java
├── domain/                          # Entidades e modelos de domínio puros
│   ├── exam/
│   │   ├── Exam.java
│   │   ├── ExamType.java
│   │   ├── Subject.java
│   │   └── ThematicAxis.java
│   └── schedule/
│       ├── FullPlannerResult.java
│       ├── OptimizationResult.java
│       ├── StudentProfile.java
│       ├── StudyBlock.java
│       └── StudyPlan.java
├── ga/                              # Motor do Algoritmo Genético
│   ├── factory/
│   │   └── StudyPlanFactory.java
│   ├── strategy/
│   │   ├── crossover/
│   │   │   ├── CrossoverStrategy.java
│   │   │   ├── HybridCrossover.java
│   │   │   ├── RepairingCrossover.java
│   │   │   └── WeightedAverageCrossover.java
│   │   ├── mutation/
│   │   │   ├── AggressiveSwapMutation.java
│   │   │   ├── CreepMutation.java
│   │   │   ├── MutationStrategy.java
│   │   │   └── SwapMutation.java
│   │   └── selection/
│   │       ├── SelectionStrategy.java
│   │       └── TournamentSelection.java
│   ├── EvolutionContext.java
│   ├── GeneticAlgorithm.java
│   ├── GeneticAlgorithmBuilder.java
│   ├── Individual.java
│   └── Population.java
└── service/                         # Regras de negócio e orquestração
    ├── calculation/                 # Motores de inferência e métricas
    │   ├── BaselineCalculator.java
    │   ├── CognitiveLoadCalculator.java
    │   └── ImportanceCalculator.java
    ├── scheduler/
    │   └── strategy/                # Heurísticas de agendamento (Strategy Pattern)
    ├── DynamicStudyPlannerService.java
    ├── StudyOptimizerService.java
    └── StudyScheduleGenerator.java
