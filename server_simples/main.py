import socket
import cv2
import numpy as np
import os
import datetime
import threading

SAVE_DIR = "imagens_muitos_bonitas"
latest_image = None
lock = threading.Lock()

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.connect(("8.8.8.8", 80))
    ip = s.getsockname()[0]
    s.close()
    return ip

def handle_client(conn, addr):
    global latest_image
    
    with conn:
        while True:
            img_size_bytes = conn.recv(4)
            if not img_size_bytes:
                break

            img_size = int.from_bytes(img_size_bytes, byteorder="big")
            data = b""
            while len(data) < img_size:
                packet = conn.recv(min(img_size - len(data), 4096))
                data += packet

            np_data = np.frombuffer(data, np.uint8)
            img = cv2.imdecode(np_data, cv2.IMREAD_COLOR)

            timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"img_{timestamp}_{addr[0].replace('.', '_')}.jpg"
            filepath = os.path.join(SAVE_DIR, filename)
            cv2.imwrite(filepath, img)
            
            print(f"Imagem salva em {filepath}")
            with lock:
                latest_image = img.copy()

def server_thread(host="0.0.0.0", port=8080):
    local_ip = get_local_ip()
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((host, port))
        s.listen()
        print(f"Servidor escutando em {local_ip}:{port}")

        while True:
            conn, addr = s.accept()
            print(f"Conectado a: {addr}")
            threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()

def display_thread(window_name="Servidor de Imagens", display_size=(640, 480)):
    global latest_image
    cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
    cv2.resizeWindow(window_name, display_size[0], display_size[1])

    while True:
        with lock:
            if latest_image is not None:
                
                h, w = latest_image.shape[:2]
                scale = min(display_size[0] / w, display_size[1] / h)
                new_w, new_h = int(w * scale), int(h * scale)
                resized = cv2.resize(latest_image, (new_w, new_h), interpolation=cv2.INTER_AREA)

                canvas = np.zeros((display_size[1], display_size[0], 3), dtype=np.uint8)
                x_offset = (display_size[0] - new_w) // 2
                y_offset = (display_size[1] - new_h) // 2
                canvas[y_offset:y_offset+new_h, x_offset:x_offset+new_w] = resized

                cv2.imshow(window_name, canvas)

        if cv2.waitKey(1) & 0xFF == ord("q"):
            break
        
        if cv2.getWindowProperty(window_name, cv2.WND_PROP_VISIBLE) < 1:
            break

    cv2.destroyAllWindows()
    os._exit(0)


if __name__ == "__main__":
    os.makedirs(SAVE_DIR, exist_ok=True)
    threading.Thread(target=server_thread, daemon=True).start()
    display_thread()
