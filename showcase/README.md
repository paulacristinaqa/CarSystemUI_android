# CarSystemUI Showcase

## ATEP integration

The showcase includes the Vehicle Gateway and a `VehiclePropertySource` boundary.
Changed properties from either the deterministic simulator or an Android
Automotive `CarPropertyManager`/VHAL source are persisted locally and sent
idempotently to the public ATEP telemetry API. Configuration, security boundaries,
delivery behavior, and tests are documented in
[ATEP Vehicle Gateway](../docs/ATEP_VEHICLE_GATEWAY.md).

If immediate delivery fails, one connectivity-constrained WorkManager job per
vehicle retries the persistent queue with bounded exponential backoff. Closing
the activity does not remove the queued events or the scheduled work.

The gateway card now reports when all eight background attempts are exhausted.
Rejected events are listed individually with their property, value, timestamp,
original identifier, and rejection reason. The operator may retry one event
without changing its identity or discard only the selected local record.

Select the source in the user-level Gradle properties file:

```properties
VEHICLE_PROPERTY_SOURCE=simulator
```

Use `aaos` for an Automotive emulator/device or `auto` to detect the device type.
AAOS mode is read-only and removes the local controls so real observations cannot
be confused with simulator evidence.

Aplicativo educacional executável que apresenta, de forma simulada, conceitos
visuais do CarSystemUI e de um veículo elétrico.

Este módulo não substitui o CarSystemUI de plataforma e não possui suas
permissões privilegiadas. O código real continua na raiz do repositório e é
compilado com Soong dentro de uma árvore AOSP.

## Abrir no Android Studio

Abra diretamente o diretório `showcase/`, aguarde a sincronização do Gradle,
selecione um dispositivo e escolha **Run > Run 'app'**.

## Linha de comando

```bash
cd showcase
./gradlew assembleDebug
```

O APK de debug será produzido em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Estado atual

A aplicação contém um simulador educacional do ciclo de energia do veículo:

```text
Desligado -> Acessórios -> Ignição ligada -> Pronto para conduzir
```

Os botões permitem avançar pela sequência ou retornar ao estado desligado. A
tela também apresenta velocidade, marcha e carga da bateria simuladas. No modo
`aaos`, os sinais compatíveis passam a vir de `CarService`/VHAL e a tela deixa os
controles locais indisponíveis.

Quando o veículo chega ao estado **Pronto para conduzir**, uma segunda área
permite selecionar `P`, `R`, `N` ou `D` e alterar a velocidade em passos de
10 km/h. O simulador aplica intertravamentos didáticos:

- só permite trocar a marcha com velocidade igual a zero;
- só permite acelerar em `D` ou `R`;
- não permite desligar até parar e retornar a `P`;
- limita a velocidade simulada ao intervalo de 0 a 120 km/h.

Essas regras foram simplificadas para estudo e não representam a especificação
completa de um veículo real.

A central de alertas combina outros sinais simulados:

- porta aberta com o veículo parado gera alerta de **atenção**;
- porta aberta em movimento eleva o alerta para **crítico**;
- cinto desafivelado é considerado quando o veículo está pronto;
- mais de um alerta pode permanecer visível ao mesmo tempo;
- desligar o sistema não fecha a porta nem afivela o cinto automaticamente.

Os interruptores em **Condições para teste** servem para preparar essas
combinações de maneira reproduzível.

O bloco **Bateria e recarga EV** acrescenta uma simulação específica de veículo
elétrico:

- a bateria começa em 78% e cada aceleração consome 1%;
- o controle de consumo manual permite preparar valores-limite;
- bateria em 20% ou menos gera atenção, e em 10% ou menos gera alerta crítico;
- adicionar carga exige que o carregador esteja conectado;
- o conector só pode ser ativado em `P` e a 0 km/h;
- com o carregador conectado, seleção de marcha e movimento ficam bloqueados;
- a carga permanece sempre entre 0% e 100%.

O botão de recarga representa apenas a mudança de um valor local. Não simula
tensão, corrente, temperatura, tempo de carga, BMS ou protocolo de carregamento.

## Como o código está organizado

O exemplo permanece pequeno para facilitar o estudo:

- `VehiclePowerState` define os estados, textos e cores possíveis;
- `next()` contém a regra de transição para o próximo estado;
- `Gear` define as marchas disponíveis;
- `VehicleSimulationState` reúne energia, marcha e velocidade;
- `CarSystemUIShowcaseApp()` guarda o estado atual e processa as ações;
- `VehicleStatusScreen()` organiza as áreas visuais;
- `PowerStateCard()`, `SimulatedSignals()` e `PowerControls()` têm uma
  responsabilidade visual cada.
- `DrivingControls()` apresenta as marchas, aceleração, frenagem e mensagens de
  intertravamento.
- `activeAlerts()` transforma combinações de sinais em alertas ordenados;
- `AlertCenter()` mostra o resultado, enquanto `VehicleConditionControls()`
  prepara porta e cinto para os cenários manuais.
- `ChargingControls()` prepara carga e conexão, enquanto os intertravamentos de
  `DrivingControls()` impedem condução com o cabo conectado ou bateria vazia.

O código agora está separado em três arquivos principais:

- `VehicleModels.kt`: estados, marchas, alertas e dados do domínio simulado;
- `VehicleSimulatorViewModel.kt`: valida ações, altera o estado e registra os
  oito eventos mais recentes;
- `MainActivity.kt`: observa o estado e desenha os componentes Compose.

Esse fluxo segue uma direção: a tela envia uma ação ao `ViewModel`, o
`ViewModel` aplica a regra e publica um novo estado, e a tela é redesenhada. O
estado permanece durante recriações da Activity, como rotação, mas não é salvo
após encerramento do processo ou limpeza dos dados do aplicativo.

O botão **Reiniciar simulação** restaura todos os valores iniciais e cria um
novo primeiro evento. O histórico é diagnóstico educacional local: não é
telemetria, log de auditoria nem dado recebido de um veículo.

Essa separação ajuda a localizar defeitos e prepara o código para, no futuro,
receber dados de uma camada de simulação ou integração sem acoplar a tela
diretamente ao veículo.

## Validação manual desta entrega

1. Inicie o aplicativo pelo botão **Run** do Android Studio.
2. Confirme que o estado inicial é **Desligado** e que marcha e bateria exibem
   um traço.
3. Pressione **Ligar acessórios** e confira o novo estado.
4. Pressione **Ligar ignição** e confira marcha `P` e bateria `78%`.
5. Pressione **Deixar pronto** e confira **Pronto para conduzir**.
6. Confirme que o botão de avanço fica desabilitado nesse estado.
7. Pressione **Desligar veículo** e confira o retorno ao estado inicial.
8. Gire a tela ou use outra resolução e observe se todo o conteúdo continua
   acessível por rolagem.

Para validar a condução simulada e seus bloqueios, siga também o caso
`CT-SHOW-002` no caderno de testes.

Para estudar combinação de condições, prioridade e tabela de decisão, execute
depois o `CT-SHOW-003`.

Para praticar limites e estados de recarga de um veículo elétrico, execute o
`CT-SHOW-004`.

Para validar arquitetura, ciclo de vida e rastreabilidade das ações, execute o
`CT-SHOW-005`.

Os testes automatizados do módulo cobrem o gateway, o mapeamento de telemetria,
a fila persistente e as duas implementações de `VehiclePropertySource`.

A verificação Windows mais recente executou os 18 testes sem falhas, gerou o
APK de debug e concluiu o lint com zero erros e 14 avisos não bloqueantes.

Para validar inspeção, reenvio idempotente, descarte seletivo e esgotamento do
trabalho em segundo plano, execute também o `CT-SHOW-009`.
