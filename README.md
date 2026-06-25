# Reserva de Salas — SiReS UA Companion 📱✨

O **Reserva de Salas** é um wrapper móvel nativo e inteligente para o portal de reservas de salas de estudo **SiReS UA**. Ele foi desenvolvido em **Kotlin** e **Jetpack Compose**, proporcionando uma experiência móvel fluida, imersiva, resiliente a falhas de rede e com integração nativa avançada para agilizar o dia a dia dos estudantes.

---

## 🚀 Principais Funcionalidades

### 1. 🔍 Leitor Nativo de QR Code (ML Kit + CameraX)
*   **Integração de Alto Desempenho:** Utiliza o **Android Jetpack CameraX** para gerenciar a câmara do dispositivo com eficiência de recursos e o **Google ML Kit Barcode Scanning** para detectar e processar instantaneamente códigos QR.
*   **Segmentação Rápida:** O analisador de imagem está configurado especificamente para formatos de código QR (`Barcode.FORMAT_QR_CODE`), otimizando a velocidade de leitura e o tempo de resposta.
*   **Interface Própria (Scanner Overlay):** Exibe um retículo de foco com efeitos visuais modernos e elegantes sobre a pré-visualização da câmara, orientando o utilizador para um escaneamento perfeito.

### 2. 🔌 Ponte JavaScript Dinâmica (`AndroidBridge`)
*   **Comunicação Bidirecional:** A aplicação expõe uma interface nativa chamada `AndroidBridge` para a página Web em execução no WebView.
*   **Abertura a partir da Web:** Ao clicar no botão de scanner no portal Web, a página chama `AndroidBridge.startQRScanner()`, abrindo instantaneamente a câmara nativa.
*   **Injeção Automatizada de Resultados:** Quando um código QR é lido com sucesso pela câmara, a aplicação executa o método JavaScript global `window.handleScannedQR(scannedURL)`, redirecionando automaticamente o utilizador para a sala correspondente dentro do portal.

### 3. 🌐 Resiliência de Rede & Modo Offline Moderno
*   **Monitorização Ativa de Ligação:** A aplicação deteta mudanças no estado de rede em tempo real.
*   **Tela de Fallback Elegante:** Caso a conexão à Internet falhe, a aplicação substitui o WebView por um ecrã nativo que apresenta o **Modo Offline da Biblioteca Central** com salas simuladas e dados locais.
*   **Botão de Reconexão:** Permite tentar novamente a ligação com um único clique de forma elegante.

---

## 🛠️ Stack Tecnológica

*   **Linguagem:** [Kotlin](https://kotlinlang.org/) (100% nativo)
*   **Framework UI:** [Jetpack Compose](https://developer.android.com/compose) com Material Design 3
*   **Câmara:** [Android Jetpack CameraX](https://developer.android.com/training/camerax) (Core, Lifecycle, View)
*   **Inteligência Artificial & Visão:** [Google ML Kit Barcode Scanning](https://developers.google.com/ml-kit/vision/barcode-scanning)
*   **Navegação e Web:** `Android System WebView` com suporte avançado a Cookies, LocalStorage e Interfaces JS.

---

## 📦 Estrutura de Código Relevante

*   **`MainActivity.kt`:** Configuração do WebView, da ponte de comunicação `AndroidBridge` (`WebAppInterface`) e gestão do estado de conectividade de rede.
*   **`QRScannerActivity.kt`:** UI de digitalização em Jetpack Compose, tratamento de permissões da câmara em tempo real e analisador de imagem em segundo plano (`BarcodeAnalyzer`).
*   **`AndroidManifest.xml`:** Declaração de permissões de Internet e Câmara (`android.permission.CAMERA`), além do registo do esquema de deep links (`reservasalas://room`).

---

## ⚙️ Requisitos do Sistema

*   **SDK Mínimo:** Android Oreo (API 26+)
*   **Permissões Necessárias:**
    *   `android.permission.INTERNET` (Acesso ao portal SiReS UA)
    *   `android.permission.ACCESS_NETWORK_STATE` (Monitorização de conexão)
    *   `android.permission.CAMERA` (Leitura de códigos QR físicos)
