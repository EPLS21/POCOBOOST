# PocoBoost F7

App que escaneia o POCO F7 (temperatura, throttling térmico, RAM, armazenamento, apps pesados, animações) e aplica otimizações sem root.

## Gerar o APK (escolha uma opção)

### Opção A: GitHub (sem instalar nada no PC)
1. Crie uma conta e um repositório novo em github.com.
2. Envie o **conteúdo** desta pasta para o repositório (a pasta `.github` precisa ficar na raiz).
3. Abra a aba **Actions** > **Build APK**. Se não começar sozinho, clique em **Run workflow**.
4. Quando ficar verde, abra a execução e baixe **PocoBoost-APK** (zip com o `app-debug.apk`).
5. Passe o APK para o celular e instale (permita "instalar apps desconhecidos").

### Opção B: Android Studio
Abra esta pasta no Android Studio, aguarde o Gradle sincronizar e use **Build > Build APK(s)**.

## Ativar tudo (uma vez)
1. No POCO F7: Configurações > Sobre o telefone > toque 7x em "Versão do MIUI/HyperOS" para liberar o modo desenvolvedor.
2. Em Opções do desenvolvedor, ative **Depuração USB** e conecte ao PC com o `adb` instalado.
3. Abra o app, role até "Comandos ADB" e rode o **passo 1** no PC. Isso libera os botões de animação e o scan de apps.
4. Rode os passos 2 a 4 quando quiser acelerar. O passo 2 demora alguns minutos.

## O que esperar (sem exagero)
- O app **não faz overclock** e não passa do limite do hardware. Sem root isso não existe, e o throttling térmico protege o chip e a bateria.
- Ele **encontra a causa** do calor/travada (carregando, app pesado, RAM/armazenamento cheios, bateria, tela em 120 Hz) e **aplica o que o Android permite** (animações, recompilação de apps, limpeza de cache).
- Os comandos do app são reversíveis e não desativam apps do sistema.

## HyperOS: ajustes manuais que ajudam
- Configurações > Bateria > modo **Desempenho** só para jogos (esquenta mais).
- Configurações > Apps > Gerenciar apps: tire "Início automático" dos apps que você não precisa.
- Tela > Taxa de atualização: 90 Hz já é bem fluido e esquenta bem menos que 120 Hz.
