import socket
import cv2
import numpy as np
import tkinter as tk
from tkinter import ttk, scrolledtext
from PIL import Image, ImageTk
import threading
import datetime
import os

class ImageServerGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("Servidor de Imagens - Trabalho SD")
        self.root.geometry("1200x700")
        
        # Lista para armazenar o histórico de imagens
        self.image_history = []
        self.current_image_index = 0
        
        # Criar diretório para salvar imagens
        self.images_dir = "received_images"
        if not os.path.exists(self.images_dir):
            os.makedirs(self.images_dir)
        
        self.setup_gui()
        self.server_running = False
        
        # Carregar imagens existentes na pasta
        self.load_existing_images()
        
        # Inicializar exibição se houver imagens
        if self.image_history:
            self.current_image_index = len(self.image_history) - 1  # Última imagem
            self.display_current_image()
            self.update_counter()
            self.history_info_label.config(text=f"Total de imagens recebidas: {len(self.image_history)}")
        
        # Iniciar servidor automaticamente
        self.start_server()
        
    def setup_gui(self):
        # Frame principal
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # Configurar grid
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)
        main_frame.columnconfigure(0, weight=2)
        main_frame.columnconfigure(1, weight=1)
        main_frame.rowconfigure(0, weight=1)

        # Frame da imagem
        image_frame = ttk.LabelFrame(main_frame, text="Imagem Atual", padding="5")
        image_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S), pady=5, padx=(0, 10))
        image_frame.columnconfigure(0, weight=1)
        image_frame.rowconfigure(0, weight=1)
        
        # Label para exibir imagem
        self.image_label = ttk.Label(image_frame, text="Aguardando imagens...", anchor="center", background="lightgray")
        self.image_label.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # Frame de controles
        controls_frame = ttk.Frame(image_frame)
        controls_frame.grid(row=1, column=0, sticky=(tk.W, tk.E), pady=5)
        
        # Botões de navegação
        ttk.Button(controls_frame, text="← Anterior", command=self.prev_image).pack(side=tk.LEFT, padx=5)
        ttk.Button(controls_frame, text="Próxima →", command=self.next_image).pack(side=tk.LEFT, padx=5)
        
        self.image_counter_label = ttk.Label(controls_frame, text="0/0")
        self.image_counter_label.pack(side=tk.LEFT, padx=10)
        
        # Status do servidor
        self.status_label = ttk.Label(controls_frame, text="Servidor: Iniciando...", font=("Arial", 9))
        self.status_label.pack(side=tk.RIGHT, padx=5)
        
        # Frame do histórico expandido
        history_frame = ttk.LabelFrame(main_frame, text="Histórico de Imagens Recebidas", padding="5")
        history_frame.grid(row=0, column=1, sticky=(tk.W, tk.E, tk.N, tk.S))
        history_frame.columnconfigure(0, weight=1)
        history_frame.rowconfigure(0, weight=1)
        
        # Frame interno para organizar melhor o histórico
        history_inner_frame = ttk.Frame(history_frame)
        history_inner_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        history_inner_frame.columnconfigure(0, weight=1)
        history_inner_frame.rowconfigure(1, weight=1)
        
        # Informações do histórico
        self.history_info_label = ttk.Label(history_inner_frame, text="Total de imagens: 0", font=("Arial", 10, "bold"))
        self.history_info_label.grid(row=0, column=0, sticky=(tk.W, tk.E), pady=(0, 5))
        
        # Lista do histórico com mais detalhes
        self.history_text = scrolledtext.ScrolledText(history_inner_frame, width=50, height=35, font=("Consolas", 9))
        self.history_text.grid(row=1, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # Botões de ação do histórico
        history_buttons_frame = ttk.Frame(history_inner_frame)
        history_buttons_frame.grid(row=2, column=0, sticky=(tk.W, tk.E), pady=5)
        
        ttk.Button(history_buttons_frame, text="Limpar Histórico", command=self.clear_history).pack(side=tk.LEFT, padx=5)
        ttk.Button(history_buttons_frame, text="Salvar Log", command=self.save_log).pack(side=tk.LEFT, padx=5)
        ttk.Button(history_buttons_frame, text="Abrir Pasta", command=self.open_images_folder).pack(side=tk.RIGHT, padx=5)
        
    def load_existing_images(self):
        """Carrega imagens existentes na pasta ao iniciar o servidor"""
        try:
            if not os.path.exists(self.images_dir):
                return
                
            # Buscar arquivos de imagem na pasta
            image_files = []
            for filename in os.listdir(self.images_dir):
                if filename.lower().endswith(('.jpg', '.jpeg', '.png')):
                    image_files.append(filename)
            
            # Ordenar por data de modificação (mais antigos primeiro)
            image_files.sort(key=lambda x: os.path.getmtime(os.path.join(self.images_dir, x)))
            
            for filename in image_files:
                filepath = os.path.join(self.images_dir, filename)
                try:
                    # Carregar imagem
                    img = cv2.imread(filepath)
                    if img is not None:
                        # Converter BGR para RGB
                        img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
                        
                        # Extrair informações do nome do arquivo
                        # Formato esperado: img_YYYYMMDD_HHMMSS_IP.jpg
                        parts = filename.replace('.jpg', '').split('_')
                        if len(parts) >= 4:
                            date_part = parts[1]
                            time_part = parts[2]
                            ip_part = parts[3].replace('_', '.')
                            
                            # Converter timestamp
                            timestamp_str = f"{date_part}_{time_part}"
                            timestamp = datetime.datetime.strptime(timestamp_str, "%Y%m%d_%H%M%S")
                            timestamp_display = timestamp.strftime("%Y-%m-%d %H:%M:%S")
                            
                            # Criar entrada no histórico
                            file_size = os.path.getsize(filepath)
                            image_info = {
                                'image': img_rgb,
                                'timestamp': timestamp_display,
                                'timestamp_file': timestamp_str,
                                'addr': (ip_part, 'unknown'),
                                'filename': filename,
                                'filepath': filepath,
                                'file_size': file_size,
                                'image_size': img.shape
                            }
                            self.image_history.append(image_info)
                            
                            # Adicionar ao histórico visual
                            height, width, channels = img.shape
                            file_size_kb = file_size / 1024
                            
                            history_entry = (f"═══════════════════════════════════════════════════\n"
                                           f"📷 IMAGEM #{len(self.image_history)} (Carregada)\n"
                                           f"⏰ Data/Hora: {timestamp_display}\n"
                                           f"🌐 Cliente: {ip_part}\n"
                                           f"📁 Arquivo: {filename}\n"
                                           f"📐 Resolução: {width}x{height}px\n"
                                           f"💾 Tamanho: {file_size_kb:.1f} KB\n"
                                           f"═══════════════════════════════════════════════════\n\n")
                            
                            self.history_text.insert(tk.END, history_entry)
                            
                except Exception as e:
                    print(f"Erro ao carregar imagem {filename}: {e}")
                    
            if self.image_history:
                print(f"Carregadas {len(self.image_history)} imagens existentes")
                self.history_text.see(tk.END)
                
        except Exception as e:
            print(f"Erro ao carregar imagens existentes: {e}")
        
    def start_server(self):
        self.server_running = True
        self.status_label.config(text="Servidor: Rodando na porta 8080")
        self.server_thread = threading.Thread(target=self.run_server, daemon=True)
        self.server_thread.start()
    
    def stop_server(self):
        self.server_running = False
        self.status_label.config(text="Servidor: Parado")
    
    def run_server(self):
        HOST = '0.0.0.0'
        PORT = 8080
        
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            s.bind((HOST, PORT))
            s.listen()
            s.settimeout(1.0)  # Timeout para verificar se deve parar
            
            while self.server_running:
                try:
                    conn, addr = s.accept()
                    self.handle_client(conn, addr)
                except socket.timeout:
                    continue
                except Exception as e:
                    print(f"Erro no servidor: {e}")
                    break
    
    def handle_client(self, conn, addr):
        try:
            with conn:
                while self.server_running:
                    # Receber tamanho da imagem
                    img_size_bytes = conn.recv(4)
                    if not img_size_bytes:
                        break
                    
                    img_size = int.from_bytes(img_size_bytes, byteorder='big')
                    
                    # Receber dados da imagem
                    data = b''
                    while len(data) < img_size:
                        packet = conn.recv(min(img_size - len(data), 4096))
                        if not packet:
                            break
                        data += packet
                    
                    if len(data) == img_size:
                        # Processar imagem
                        self.process_received_image(data, addr)
                    
        except Exception as e:
            print(f"Erro ao processar cliente {addr}: {e}")
    
    def process_received_image(self, data, addr):
        try:
            # Decodificar imagem
            np_data = np.frombuffer(data, np.uint8)
            img = cv2.imdecode(np_data, cv2.IMREAD_COLOR)
            
            if img is not None:
                # Converter BGR para RGB
                img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
                
                # Salvar imagem
                timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                timestamp_file = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
                filename = f"img_{timestamp_file}_{addr[0].replace('.', '_')}.jpg"
                filepath = os.path.join(self.images_dir, filename)
                cv2.imwrite(filepath, img)
                
                # Calcular tamanho do arquivo
                file_size = os.path.getsize(filepath)
                
                # Adicionar ao histórico
                image_info = {
                    'image': img_rgb,
                    'timestamp': timestamp,
                    'timestamp_file': timestamp_file,
                    'addr': addr,
                    'filename': filename,
                    'filepath': filepath,
                    'file_size': file_size,
                    'image_size': img.shape
                }
                self.image_history.append(image_info)
                
                # Atualizar GUI na thread principal - corrigir lambda
                self.root.after(0, self.update_gui, image_info)
            else:
                print("Falha ao decodificar a imagem")
                
        except Exception as e:
            print(f"Erro ao processar imagem: {e}")
            import traceback
            traceback.print_exc()
                
        except Exception as e:
            print(f"Erro ao processar imagem: {e}")
    
    def update_gui(self, image_info):
        # Atualizar para mostrar a nova imagem automaticamente
        self.current_image_index = len(self.image_history) - 1
        self.display_current_image()
        
        # Adicionar ao histórico com mais detalhes
        height, width, channels = image_info['image_size']
        file_size_kb = image_info['file_size'] / 1024
        
        history_entry = (f"═══════════════════════════════════════════════════\n"
                        f"📷 IMAGEM #{len(self.image_history)} (Nova)\n"
                        f"⏰ Data/Hora: {image_info['timestamp']}\n"
                        f"🌐 Cliente: {image_info['addr'][0]}:{image_info['addr'][1]}\n"
                        f"📁 Arquivo: {image_info['filename']}\n"
                        f"📐 Resolução: {width}x{height}px\n"
                        f"💾 Tamanho: {file_size_kb:.1f} KB\n"
                        f"═══════════════════════════════════════════════════\n\n")
        
        self.history_text.insert(tk.END, history_entry)
        self.history_text.see(tk.END)
        
        # Atualizar contador e informações
        self.update_counter()
        self.history_info_label.config(text=f"Total de imagens recebidas: {len(self.image_history)}")
        
        # Atualizar status
        self.status_label.config(text=f"Servidor: Nova imagem de {image_info['addr'][0]}")
        
        # Voltar ao status normal após 3 segundos
        self.root.after(3000, lambda: self.status_label.config(text="Servidor: Rodando na porta 8080"))
    
    def clear_history(self):
        """Limpa o histórico visual (mas mantém os arquivos salvos)"""
        self.history_text.delete(1.0, tk.END)
        self.history_info_label.config(text="Histórico limpo - Total de imagens: 0")
    
    def save_log(self):
        """Salva o log do histórico em um arquivo"""
        try:
            log_filename = f"log_{datetime.datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
            with open(log_filename, 'w', encoding='utf-8') as f:
                f.write("=== LOG DO SERVIDOR DE IMAGENS ===\n")
                f.write(f"Gerado em: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
                f.write(f"Total de imagens: {len(self.image_history)}\n\n")
                f.write(self.history_text.get(1.0, tk.END))
            print(f"Log salvo em: {log_filename}")
        except Exception as e:
            print(f"Erro ao salvar log: {e}")
    
    def open_images_folder(self):
        """Abre a pasta onde as imagens são salvas"""
        import subprocess
        import platform
        try:
            if platform.system() == "Windows":
                subprocess.run(["explorer", self.images_dir])
            elif platform.system() == "Darwin":  # macOS
                subprocess.run(["open", self.images_dir])
            else:  # Linux
                subprocess.run(["xdg-open", self.images_dir])
        except Exception as e:
            print(f"Erro ao abrir pasta: {e}")
    
    def display_current_image(self):
        if not self.image_history:
            self.image_label.config(image="", text="Aguardando imagens...")
            return
        
        try:
            image_info = self.image_history[self.current_image_index]
            img = image_info['image']
            
            # Redimensionar imagem para caber na tela
            pil_img = Image.fromarray(img)
            
            # Calcular novo tamanho mantendo proporção
            display_size = (500, 400)
            pil_img.thumbnail(display_size, Image.Resampling.LANCZOS)
            
            # Converter para PhotoImage
            photo = ImageTk.PhotoImage(pil_img)
            
            # Atualizar label
            self.image_label.config(image=photo, text="")
            self.image_label.image = photo  # Manter referência
            
        except Exception as e:
            print(f"Erro ao exibir imagem: {e}")
            self.image_label.config(image="", text=f"Erro ao exibir imagem: {e}")
    
    def prev_image(self):
        if self.image_history and self.current_image_index > 0:
            self.current_image_index -= 1
            self.display_current_image()
            self.update_counter()
    
    def next_image(self):
        if self.image_history and self.current_image_index < len(self.image_history) - 1:
            self.current_image_index += 1
            self.display_current_image()
            self.update_counter()
    
    def update_counter(self):
        if self.image_history:
            total = len(self.image_history)
            current = self.current_image_index + 1
            self.image_counter_label.config(text=f"{current}/{total}")
        else:
            self.image_counter_label.config(text="0/0")

if __name__ == "__main__":
    root = tk.Tk()
    app = ImageServerGUI(root)
    root.mainloop()