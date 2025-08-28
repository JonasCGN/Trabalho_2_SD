# 📸 Atividade 2 – Foto via Botão (Android → Servidor Python por Sockets)

Este projeto implementa um sistema simples de **captura e envio de fotos** de um aplicativo Android para um servidor Python usando **sockets TCP**.

---

## 🚀 Descrição

- O **app Android** permite tirar uma foto e enviá-la ao servidor.
- A **comunicação** segue um protocolo simples:
  - **4 bytes** iniciais → tamanho da imagem em bytes.
  - **N bytes seguintes** → conteúdo da foto em **JPEG**.
- O **servidor Python** recebe a foto, salva com **timestamp** em uma pasta e exibe a última imagem recebida em uma janela.

---

## 🛠️ Tecnologias Utilizadas

- **Android (Kotlin)**
  - [CameraX](https://developer.android.com/training/camerax) ou Intent nativa para captura.
  - Envio de imagem via **socket TCP**.
- **Python 3**
  - `socket` → para comunicação.
  - `opencv-python` ou `tkinter` → para exibição das imagens.
  - `os`, `datetime` → para salvar com timestamp.

---

## 📋 Requisitos

### Android
- Android Studio instalado.
- Permissões de **Câmera** e **Internet** no `AndroidManifest.xml`.

### Python
- Python 3.8+  
- Instalar dependências:
  ```bash
  pip install -r requirements.txt


## ⚙️ Como Executar

### 1. Servidor Python

Clone o repositório:

1-git clone https://github.com/seu-usuario/seu-projeto.git

2-cd seu-projeto

3. Instale as dependências:
   ```bash
   pip install -r requirements.txt

4-Execute o servidor Python

python main.py


## Aplicativo Android


-Abra o projeto no Android Studio (pasta app/).

-Conecte um dispositivo Android físico ou use um emulador com câmera.

-Execute o app.

-Insira o IP do servidor e a porta configurada no servidor.py.

-Toque em “Tirar e Enviar”.

-O servidor receberá a foto, salvará em imagens_recebidas/ com timestamp e abrirá uma janela exibindo a    última imagem.


## servidor

![Servidor recebendo imagem](server/received_images/image.png)







