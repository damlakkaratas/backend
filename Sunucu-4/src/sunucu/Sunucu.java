package sunucu;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public class Sunucu extends JFrame {
    private JTextArea logArea; // Sunucu loglarını görüntülemek için kullanılan metin alanı

    private static final int PORT = 54321; // Sunucunun çalışacağı port numarası
    private Map<String, Integer> muzayedeUrunleri = new HashMap<>(); // Müzayedeye sunulan ürünler ve başlangıç fiyatlarını saklar
    private Map<String, Integer> teklifler = new HashMap<>(); // Her ürün için verilen teklifler
    private static Map<String, String> kazananKisi = new HashMap<>(); // Her ürün için teklifi kazanan kişi
    private static Map<String, Timer> timers = new HashMap<>(); // Her ürün için bir zamanlayıcı tutar
    private Map<String, Long> kalanSureler = new HashMap<>();

    public Sunucu() {
        setTitle("Online Müzayede Sunucusu"); // Pencere başlığını ayarlar
        setSize(400, 300); // Pencere boyutunu belirler
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Pencere kapatıldığında uygulama sonlanır
        setLocationRelativeTo(null); // Pencereyi ekranın ortasında açar

        logArea = new JTextArea(); // Logları göstermek için metin alanı
        logArea.setEditable(false); // Metin alanının düzenlenmesini engeller
        JScrollPane scrollPane = new JScrollPane(logArea); // Log alanına kaydırma çubuğu ekler
        add(scrollPane, BorderLayout.CENTER); // Scroll pane'i ana pencereye ekler

        startServer(); // Sunucuyu başlatan metod
        setVisible(true); // Pencereyi görünür yapar
    }

    private void startServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) { // Sunucu soketi oluşturulur ve belirtilen portta dinlemeye başlar
                log("Sunucu başlatıldı. Port: " + PORT); // Sunucunun başladığı bilgisini loga yazar

                while (true) { // Sonsuz döngüde istemci bağlantılarını kabul eder
                    Socket clientSocket = serverSocket.accept(); // İstemci bağlantısı kabul edilir
                    new ClientHandler(clientSocket).start(); // İstemci için yeni bir thread başlatılır
                }
            } catch (IOException e) {
                e.printStackTrace(); // Bağlantı hatası olursa hata yazdırılır
            }
        }).start();
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n")); // GUI thread'inde log mesajı ekler
    }

    private class ClientHandler extends Thread {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
    try {
        // İstemciye mesaj göndermek için PrintWriter oluşturuluyor.
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        // İstemciden mesaj okumak için BufferedReader oluşturuluyor.
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        String clientMessage;
        // İstemciden gelen mesajlar sürekli olarak okunuyor.
        while ((clientMessage = in.readLine()) != null) {
            // Mesajı boşluklara göre ayırma.
            String[] tokens = clientMessage.split(" ");
            // Komutu ilk token olarak al.
            String command = tokens[0];

            switch (command) {
                case "BASLA":
                    // Muzayede ürünü başlatma komutu
                    String urunAdi = tokens[1];
                    int baslangicFiyati = Integer.parseInt(tokens[2]);
                    // Ürünü ve başlangıç fiyatını haritaya ekler.
                    muzayedeUrunleri.put(urunAdi, baslangicFiyati);
                    // İstemciye yanıt gönderir
                    out.println("Muzayede baslatildi, urun adi: " + urunAdi + ". Urun acilis fiyati: " + baslangicFiyati);
                    // Log mesajı
                    log("Muzayede baslatildi " + urunAdi + " Baslangic fiyati " + baslangicFiyati);
                    break;

                case "TEKLIF":
                    // Teklif verir
                    String teklifEdenKisi = tokens[1];
                    String urunAdiTeklif = tokens[2];
                    int teklifMiktari = Integer.parseInt(tokens[3]);
                    
                    // Müzayedeye zamanlayıcıyı başlatir
                    startTimerForMuzayede(urunAdiTeklif);

                    // Ürünün mevcut olup olmadığını kontrol eder
                    if (!muzayedeUrunleri.containsKey(urunAdiTeklif)) {
                        out.println("Muzayede bulunamadi: ");
                    } else if (teklifMiktari <= muzayedeUrunleri.get(urunAdiTeklif)) {
                        out.println("Teklif reddedildi: Daha yuksek bir teklif giriniz.");
                    } else if (!teklifler.containsKey(urunAdiTeklif) || teklifler.get(urunAdiTeklif) < teklifMiktari) {
                        // Yeni teklifleri günceller
                        if (!teklifler.containsKey(urunAdiTeklif)) {
                            teklifler.put(urunAdiTeklif, muzayedeUrunleri.get(urunAdiTeklif));
                        }
                        muzayedeUrunleri.put(urunAdiTeklif, teklifMiktari);
                        teklifler.put(urunAdiTeklif, teklifMiktari);
                        kazananKisi.put(urunAdiTeklif, teklifEdenKisi);
                        // İstemciye yanıt gönderir
                        out.println("Teklif kabul edildi: " + urunAdiTeklif + " Fiyat " + teklifMiktari + " Teklif eden: " + teklifEdenKisi);
                        // Log mesajı
                        log("Teklif yapıldı: " + urunAdiTeklif + " Fiyat: " + teklifMiktari + " Teklif eden: " + teklifEdenKisi);
                    } else {
                        out.println("Teklif reddedildi: Daha yuksek bir teklif giriniz.");
                    }
                    break;

                case "SONLANDIR":
                    // Müzayedeyi sonlandırir
                    String urunAdiSonlandir = tokens[1];
                    if (teklifler.containsKey(urunAdiSonlandir)) {
                        // İstemciye sonuç gönderir
                        out.println("Muzayede sonlandirildi. Urun Adi: " + urunAdiSonlandir + " Satildigi para: " + teklifler.get(urunAdiSonlandir) + " Kazanan kisi: " + kazananKisi.get(urunAdiSonlandir));
                        // Log mesajı
                        log("Muzayede sonlandirildi. Urun : " + urunAdiSonlandir + " Satildigi para: " + teklifler.get(urunAdiSonlandir) + " Kazanan kisi: " + kazananKisi.get(urunAdiSonlandir));
                        // Zamanlayıcıyı iptal eder
                        cancelTimerForMuzayede(urunAdiSonlandir);
                        // Haritalardan ilgili verileri kaldırir
                        muzayedeUrunleri.remove(urunAdiSonlandir);
                        teklifler.remove(urunAdiSonlandir);
                        kazananKisi.remove(urunAdiSonlandir);
                        kalanSureler.remove(urunAdiSonlandir);
                    } else {
                        out.println("Muzayede bulunamadi.");
                    }
                    break;

                case "LISTE":
                    // Müzayede ürünleri listeler
                    StringBuilder liste = new StringBuilder();
                    for (Map.Entry<String, Integer> entry : muzayedeUrunleri.entrySet()) {
                        String listeUrunAdi = entry.getKey();
                        int fiyat = entry.getValue();
                        // Kalan süreyi hesaplar
                        long kalanSure = kalanSureler.getOrDefault(listeUrunAdi, 0L) + 10000 - System.currentTimeMillis();
                        kalanSure = (kalanSure > 0) ? (kalanSure + 999) / 1000 : 999;
                        liste.append(listeUrunAdi).append(":").append(fiyat).append(":").append(kalanSure).append("sn").append(",");
                    }
                    // İstemciye liste gönderir
                    out.println("[" + liste.toString() + "]");
                    break;

                default:
                    // Geçersiz komut durumunda istemciye hata mesajı gönderir
                    out.println("Gecersiz komut.");
                    break;
            }
        }
    } catch (IOException e) {
        e.printStackTrace();
    } finally {
        try {
            // İstemci soketini kapatir
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

        
private void startTimerForMuzayede(String urunAdi) {
    long baslangicZamani = System.currentTimeMillis();
    Timer timer = new Timer();
    timers.put(urunAdi, timer);
    timer.schedule(new TimerTask() {
        @Override
        public void run() {
            if (muzayedeUrunleri.containsKey(urunAdi)) {
                log("Muzayede zaman asimina ugradi: " + urunAdi);
                cancelTimerForMuzayede(urunAdi);
                
                // Zaman aşımında otomatik olarak SONLANDIR işlemi yapılır
                if (teklifler.containsKey(urunAdi)) {
                    String kazananKisiIsmi = kazananKisi.get(urunAdi);
                    int kazananFiyat = teklifler.get(urunAdi);
                    out.println("Muzayede sonlandirildi. Urun adi: " + urunAdi + " Satildigi para: " + kazananFiyat + " Kazanan kisi: " + kazananKisiIsmi);
                    log("Muzayede sonlandirildi. Urun adi: " + urunAdi + " Satildigi para: " + kazananFiyat + " Kazanan kisi: " + kazananKisiIsmi);

                    muzayedeUrunleri.remove(urunAdi);
                    teklifler.remove(urunAdi);
                    kazananKisi.remove(urunAdi);
                    kalanSureler.remove(urunAdi);
                } else {
                    out.println("Muzayede sonlandirildi. Urun adi: " + urunAdi + " Satildigi para: Baslangic fiyati");
                    log("Muzayede sonlandirildi. Urun adi: " + urunAdi + " Satildigi para: Baslangic fiyati");

                    muzayedeUrunleri.remove(urunAdi);
                    kalanSureler.remove(urunAdi);
                }
            }
        }
    }, 10000); // 10 saniye sonra zaman aşımı

    kalanSureler.put(urunAdi, baslangicZamani);
}


        private void cancelTimerForMuzayede(String urunAdi) {
            Timer timer = timers.get(urunAdi);
            if (timer != null) {
                timer.cancel(); // Zamanlayıcıyı iptal eder
                timer.purge(); // Zamanlayıcıdan bekleyen görevleri temizler
                timers.remove(urunAdi); // Zamanlayıcıyı siler
            }
        }
        
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Sunucu::new); // GUI başlatır ve sunucu nesnesi oluşturur
    }
}
