package sunucu;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Istemci extends JFrame {
    private JTextField userInputField; // Kullanıcıdan giriş almak için metin alanı
    private JTextArea logArea; // Sunucudan gelen cevapları göstermek için metin alanı

    private static final String SERVER_IP = "localhost"; // Sunucunun IP adresi
    private static final int SERVER_PORT = 54321; // Sunucunun port numarası

    public Istemci() {
        setTitle("Online Muzayede Sistemi"); // Pencere başlığı
        setSize(400, 300); // Pencere boyutu
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Pencere kapatıldığında uygulama sonlansın
        setLocationRelativeTo(null); // Pencereyi ekranın ortasına yerleştirir

        setLayout(new BorderLayout()); // Pencere düzenini ayarlar

        // Komutlar için düğmeler oluşturma
        JPanel buttonPanel = new JPanel(new GridLayout(4, 1)); // 4 komut için düğme paneli
        JButton baslaButton = new JButton("Muzayede Baslat");
        JButton teklifButton = new JButton("Teklif Yap");
        JButton sonlandirButton = new JButton("Muzayedeyi Sonlandir");
        JButton listeButton = new JButton("Aktif Muzayedeleri Listele");

        // Düğmelere tıklama olayları ekle
        baslaButton.addActionListener(e -> sendToServer("BASLA " + getUserInput("Urun adi") + " " + getUserInput("Baslangic Fiyati")));
        teklifButton.addActionListener(e -> sendToServer("TEKLIF " + getUserInput("Teklif Eden Kisi") + " " + getUserInput("Urun Adi") + " " + getUserInput("Teklif Miktari")));
        sonlandirButton.addActionListener(e -> sendToServer("SONLANDIR " + getUserInput("Urun Adi")));
        listeButton.addActionListener(e -> sendToServer("LISTE"));

        buttonPanel.add(baslaButton);
        buttonPanel.add(teklifButton);
        buttonPanel.add(sonlandirButton);
        buttonPanel.add(listeButton);

        add(buttonPanel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false); // Kullanıcının log alanını düzenlemesini engeller
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        setVisible(true); // Pencereyi görünür yapar

        startClient(); // İstemciyi başlatir
    }

    private String getUserInput(String message) {
        return JOptionPane.showInputDialog(this, message); // Kullanıcıdan bilgi isteme dialogu
    }

    private void startClient() {
        new Thread(() -> {
            try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                String serverMessage;
                while ((serverMessage = in.readLine()) != null) {
                    if (serverMessage.startsWith("[")) { // Eğer mesaj bir liste ise
                        handleListResponse(serverMessage);
                    } else {
                        log("Sunucudan gelen cevap: " + serverMessage);
                    }
                }
            } catch (IOException e) {
                log("Sunucuya bağlanırken hata oluştu: " + e.getMessage());
            }
        }).start();
    }
    private void handleListResponse(String serverMessage) {
        String[] muzayedeListesi = serverMessage.replace("[", "").replace("]", "").split(",");
        StringBuilder formattedList = new StringBuilder();
        for (String muzayede : muzayedeListesi) {
            String[] urunBilgisi = muzayede.split(":");
            formattedList.append(urunBilgisi[0]).append(":").append(urunBilgisi[1]).append("\n");
        }
        log("Mevcut Muzayede Listesi ve Fiyatlari:\n" + formattedList.toString());
    }

    private void sendToServer(String message) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(message); // Sunucuya mesaj gönderir
            String serverResponse = in.readLine(); // Sunucudan gelen cevabı okur
            log("Sunucudan gelen cevap: " + serverResponse); // Log alanına sunucudan gelen cevabı yazar

        } catch (IOException e) {
            log("Sunucuya mesaj gönderirken hata oluştu: " + e.getMessage()); // Bağlantı kurulamazsa veya mesaj gönderilemezse hata loglar
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n")); // Güvenli bir şekilde GUI thread'inde log alanına mesaj ekler
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Istemci::new); // GUI'yi başlat ve istemci nesnesi oluşturur
    }
}
