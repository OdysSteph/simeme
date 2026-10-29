# SiMeme — Real-Time Solana Meme Coin Trading Simulation Engine

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-brightgreen?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue?style=flat-square&logo=postgresql)
![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-purple?style=flat-square)
![Solana](https://img.shields.io/badge/Network-Solana-14F195?style=flat-square&logo=solana)

**SiMeme** adalah backend engine simulasi *paper trading* meme coin jaringan **Solana** secara *real-time* (terinspirasi dari platform terminal DEX seperti **GMGN.ai** dan **BullX**).

Engine ini memungkinkan pengguna untuk berlatih trading meme coin menggunakan saldo virtual (**$1,000 USD** modal awal) dengan harga pasar asli dari jaringan Solana menggunakan **Birdeye Data Services API**, lengkap dengan simulasi *price impact (slippage)* berdasarkan likuiditas pool, potongan *trading fee*, pelacakan *Unrealized & Realized PnL* secara langsung, serta fitur penambahan token kustom hanya dengan menempelkan (*paste*) **Contract Address (CA)**.

---

## Daftar Isi

- [Latar Belakang](#-latar-belakang)
- [Fitur Utama](#-fitur-utama)
- [Arsitektur & Alur Data](#-arsitektur--alur-data)
- [Keterbatasan Sistem (Known Limitations)](#-keterbatasan-sistem-known-limitations)
- [Tech Stack](#-tech-stack)
- [Prasyarat & Instalasi](#-prasyarat--instalasi)
- [Dokumentasi REST API](#-dokumentasi-rest-api)
- [Dokumentasi WebSocket (STOMP)](#-dokumentasi-websocket-stomp)
- [Struktur Proyek](#-struktur-proyek)

---

## Latar Belakang

Perdagangan *meme coin* di jaringan Solana dikenal memiliki volatilitas ekstrem, biaya transaksi, serta efek *slippage* yang signifikan. **SiMeme** dibangun sebagai mesin simulasi (*paper trading engine*) agar pengguna dapat merasakan pengalaman eksekusi order beli/jual secara *real-time* tanpa risiko finansial nyata.

Sistem ini menggabungkan penyimpanan *in-memory cache* (`ConcurrentHashMap`) untuk pembacaan harga berkecepatan tinggi, database relasional (**PostgreSQL**) dengan *Optimistic Locking* untuk integritas saldo dan riwayat transaksi, serta **Spring STOMP WebSocket** untuk memancarkan (*broadcast*) perubahan harga dan *Profit/Loss (PnL)* langsung ke layar pengguna.

---

## Fitur Utama

1. **Sinkronisasi Harga Pasar Real-Time (Birdeye API)**
   Mengambil harga spot USD (`value`) dan likuiditas (`liquidity`) token Solana secara langsung dari endpoint `GET /defi/price` Birdeye API.
2. **Custom Token Import via Contract Address (Paste CA)**
   Pengguna dapat menambahkan token Solana apa pun ke dalam engine hanya dengan melakukan *paste* Contract Address (CA). Engine secara otomatis memvalidasi harga ke Birdeye dan melengkapi metadata koin (`symbol`, `name`, `decimals`, `logoUri`) melalui Birdeye Metadata V3 & fallback publik **DexScreener API**.
3. **Simulasi Mekanisme DEX Nyata (Slippage & Trading Fee)**
   - **Platform Fee**: Potongan biaya transaksi sebesar `1%` (`0.01`) pada setiap eksekusi order `BUY` maupun `SELL`.
   - **Dynamic Slippage & Price Impact**: Menggunakan *base slippage* `0.3%` ditambah rasio nilai order terhadap total likuiditas pool (`amountUsd / liquidityUsd`). Semakin besar ukuran order dibandingkan likuiditas koin, semakin tinggi *price impact* yang diterima.
4. **Kalkulasi Portofolio & PnL Real-Time**
   - Menghitung **Average Buy Price** secara otomatis saat pengguna melakukan akumulasi (*averaging down/up*).
   - Menghitung **Unrealized PnL ($ dan %)** secara *real-time* setiap kali harga pasar bergerak.
   - Mencatat **Realized PnL ($)** secara akurat saat pengguna menjual sebagian (`1%–99%`) atau seluruh (`100%`) kepemilikan token.
5. **Low-Latency In-Memory Price Cache & Concurrency Control**
   - Harga koin disimpan di RAM melalui `PriceCacheService` (`ConcurrentHashMap`) sehingga eksekusi order tidak terhambat *query* disk.
   - Entitas `Wallet` dilengkapi anotasi `@Version` (*JPA Optimistic Locking*) untuk mencegah *race condition* atau saldo ganda saat pengguna melakukan klik beli/jual secara cepat.
6. **Auto-Seeding Data Awal**
   Saat pertama kali dijalankan, `DataSeeder` otomatis mendaftarkan 10 meme coin populer Solana (`BONK`, `WIF`, `POPCAT`, `BOME`, `MEW`, `PNUT`, `GOAT`, `TRUMP`, `AI16Z`, `FWOG`) serta akun demo `trader_satu` dengan modal awal **$1,000.00 USD**.

---

## Arsitektur & Alur Data

```mermaid
flowchart LR
    subgraph External APIs
        BE["Birdeye API\n(GET /defi/price)"]
        DS["DexScreener API\n(Metadata Fallback)"]
    end

    subgraph SiMeme Spring Boot Engine
        BMS["BirdeyeMarketService\n(Round-Robin Scheduler)"]
        PCS["PriceCacheService\n(In-Memory ConcurrentHashMap)"]
        TS["TradingService\n(Buy / Sell / Slippage / PnL)"]
        MBS["MarketBroadcastService\n(STOMP Broadcaster)"]
        DB[("PostgreSQL\n(Users, Wallets, Tokens,\nPositions, TradeHistory)")]
    end

    subgraph Frontend Client
        UI["Web Client (React/Vue/Next)"]
    end

    BE -->|Spot Price & Liquidity| BMS
    DS -->|Token Symbol, Name, Logo| BMS
    BMS -->|Update RAM Cache| PCS
    BMS -->|Persist Token & Last Price| DB
    BMS -->|Trigger Broadcast| MBS
    PCS -->|Read Instant Price| TS
    PCS -->|Read Instant Price| MBS
    TS <-->|ACID Transaction + Optimistic Lock| DB
    MBS ==>|WS /topic/prices\nWS /topic/portfolio/{user}| UI
    UI -->|REST /api/trade/*\nREST /api/tokens/*| TS
```

---

## Keterbatasan Sistem (Known Limitations)

Karena proyek ini dirancang agar dapat dijalankan secara gratis menggunakan **Birdeye Standard (Free) Tier API**, terdapat beberapa batasan teknis yang perlu diperhatikan:

1. **Delay Pembaruan Harga Beberapa Detik (Round-Robin Polling)**
   - Paket gratis (*Standard Tier*) Birdeye membatasi penggunaan hanya pada endpoint *single-token* (`GET /defi/price`) dengan batas **Rate Limit 1 Request Per Second (1 RPS)** dan tidak mengizinkan akses ke endpoint *batch* (`GET /defi/multi_price`) maupun *Birdeye WebSocket Stream*.
   - Untuk menghindari *error* `HTTP 429 Too Many Requests` dan `HTTP 403 Forbidden`, `BirdeyeMarketService` mengambil harga token secara **bergilir (*round-robin*) setiap 1.2 detik per token**.
   - **Dampak**: Jika terdapat 10 token aktif di daftar pantauan, setiap token individu akan mendapatkan pembaruan harga terbaru setiap **~12 detik** sekali (meskipun WebSocket tetap memancarkan *tick* ke frontend setiap 1.2 detik).
2. **Jeda Inisialisasi Awal (Cold Start)**
   - Saat aplikasi baru dinyalakan (*cold start*), engine mengambil harga awal satu per satu dengan jeda `1.1 detik` antar-token agar aman dari *rate limit*. Seluruh 10 token awal akan terisi lengkap di memori dalam waktu sekitar `10–12 detik` setelah aplikasi menyala.
3. **Likuiditas Fallback untuk Token Baru**
   - Apabila suatu token mikro/baru belum memiliki data likuiditas yang terindeks pada response `/defi/price`, sistem menetapkan *safe default liquidity* sebesar `$100,000 USD` untuk mencegah kegagalan pembagian nol (*division by zero*) saat menghitung *slippage*.

---

## Tech Stack

- **Bahasa Pemrograman**: Java 21 (LTS)
- **Framework Utama**: Spring Boot 4.1.2 (`spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-websocket`)
- **HTTP Client**: Spring `RestClient` (Synchronous Fluent API)
- **Real-Time Messaging**: Spring STOMP over WebSocket (dengan dukungan SockJS fallback)
- **Database**: PostgreSQL + Hibernate/JPA
- **Utility**: Project Lombok
- **Build Tool**: Gradle (Groovy DSL)

---

## Prasyarat & Instalasi

### 1. Prasyarat
- **JDK 21** atau lebih baru
- **PostgreSQL** (berjalan di port `5432`)
- **Akun & API Key Birdeye** (Gratis — daftar di [bds.birdeye.so](https://bds.birdeye.so))

### 2. Persiapan Database PostgreSQL
Buat database dan user di PostgreSQL (atau sesuaikan dengan kredensial lokal Anda):
```sql
CREATE DATABASE simeme_db;
CREATE USER simeme WITH ENCRYPTED PASSWORD 'simeme';
GRANT ALL PRIVILEGES ON DATABASE simeme_db TO simeme;
```

### 3. Konfigurasi `application.properties`
File `src/main/resources/application.properties` sengaja dimasukkan ke dalam `.gitignore` agar API Key dan kredensial database tidak terekspos ke repositori publik.

Salin file template yang sudah disediakan:
```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```
Lalu masukkan API Key Birdeye Anda di `src/main/resources/application.properties` (atau lewat *Environment Variable* `BIRDEYE_API_KEY`):
```properties
birdeye.api.key=${BIRDEYE_API_KEY:MASUKKAN_API_KEY_BIRDEYE_ANDA}
```

### 4. Menjalankan Aplikasi
Gunakan Gradle Wrapper untuk menjalankan server di `http://localhost:8080`:

```bash
# Linux / macOS
./gradlew bootRun

# Windows (PowerShell / CMD)
.\gradlew.bat bootRun
```

---

## Dokumentasi REST API

Base URL: `http://localhost:8080`

### 1. Market & Token Endpoints (`/api/tokens`)

#### a. Ambil Semua Token Aktif
- **Endpoint**: `GET /api/tokens/featured` *(atau `GET /api/tokens`)*
- **Response (`200 OK`)**:
  ```json
  [
    {
      "contractAddress": "6p6xgHyF7AeE6TZkSmFsko444wqoP15icUSqi2jfGiPN",
      "symbol": "TRUMP",
      "name": "OFFICIAL TRUMP",
      "chain": "solana",
      "decimals": 6,
      "logoUri": null,
      "livePriceUsd": 1.6668309494597944,
      "liquidityUsd": 2543120.50,
      "priceUpdatedAt": "2026-10-29T08:00:00Z"
    }
  ]
  ```

#### b. Tambah Token Baru dengan Paste Contract Address (CA)
- **Endpoint**: `POST /api/tokens` *(atau `POST /api/tokens/add`)*
- **Request Body**:
  ```json
  {
    "contractAddress": "6p6xgHyF7AeE6TZkSmFsko444wqoP15icUSqi2jfGiPN"
  }
  ```
  *(Mendukung alias JSON key: `"contractAddress"`, `"ca"`, `"address"`, `"tokenAddress"`, `"mint"`).*
- **Response (`200 OK`)**: Mengembalikan objek `TokenMarketResponse` lengkap dengan harga live dari Birdeye dan metadata koin, serta langsung mem-broadcast ke WebSocket `/topic/prices`.

#### c. Ambil Detail Satu Token
- **Endpoint**: `GET /api/tokens/{contractAddress}`

---

### 2. Trading & Portfolio Endpoints (`/api/trade`)

#### a. Lihat Portofolio & Unrealized PnL User
- **Endpoint**: `GET /api/trade/portfolio/{username}` (User default: `trader_satu`)
- **Response (`200 OK`)**:
  ```json
  {
    "username": "trader_satu",
    "walletBalanceUsd": 900.00000000,
    "totalPortfolioValueUsd": 1005.42000000,
    "holdings": [
      {
        "contractAddress": "6p6xgHyF7AeE6TZkSmFsko444wqoP15icUSqi2jfGiPN",
        "symbol": "TRUMP",
        "amountHeld": 59.221450000000,
        "averageBuyPrice": 1.671680000000000000,
        "currentPrice": 1.780100000000000000,
        "currentValueUsd": 105.42000000,
        "unrealizedPnlUsd": 6.42000000,
        "unrealizedPnlPercentage": 6.48
      }
    ]
  }
  ```

#### b. Eksekusi Order Beli (`BUY`)
- **Endpoint**: `POST /api/trade/buy`
- **Request Body**:
  ```json
  {
    "username": "trader_satu",
    "contractAddress": "6p6xgHyF7AeE6TZkSmFsko444wqoP15icUSqi2jfGiPN",
    "amountUsd": 100
  }
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "status": "SUCCESS",
    "side": "BUY",
    "symbol": "TRUMP",
    "spentUsd": 100,
    "tokensReceived": 59.221450000000,
    "executionPrice": 1.671898123456789000,
    "feeUsd": 1.00000000
  }
  ```

#### c. Eksekusi Order Jual Berdasarkan Persentase (`SELL 1% - 100%`)
- **Endpoint**: `POST /api/trade/sell`
- **Request Body**:
  ```json
  {
    "username": "trader_satu",
    "contractAddress": "6p6xgHyF7AeE6TZkSmFsko444wqoP15icUSqi2jfGiPN",
    "sellPercentage": 50
  }
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "status": "SUCCESS",
    "side": "SELL",
    "symbol": "TRUMP",
    "netUsdReceived": 52.18290000,
    "tokensSold": 29.610725000000,
    "executionPrice": 1.774759700000000000,
    "realizedPnlUsd": 2.68290000
  }
  ```

---

## Dokumentasi WebSocket (STOMP)

- **Handshake Endpoint**: `http://localhost:8080/ws-simeme` (mendukung **SockJS** & STOMP)
- **Daftar Saluran Subscribe (Topics)**:
  1. **`/topic/prices`**: Menerima pancaran daftar harga seluruh token setiap kali scheduler selesai memperbarui harga atau saat ada token baru yang ditambahkan.
     ```json
     {
       "serverTime": "2026-10-29T08:05:12.110Z",
       "ticks": [
         {
           "contractAddress": "6p6xgHyF7AeE6TZkSmFsko444wqoP15icUSqi2jfGiPN",
           "symbol": "TRUMP",
           "name": "OFFICIAL TRUMP",
           "priceUsd": 1.6668309494597944,
           "liquidityUsd": 2543120.50
         }
       ]
     }
     ```
  2. **`/topic/portfolio/{username}`** (contoh: `/topic/portfolio/trader_satu`): Menerima pembaruan nilai portofolio dan *Unrealized PnL* secara otomatis setiap kali harga bergerak atau sesaat setelah order `BUY`/`SELL` dieksekusi.

---

## Struktur Proyek

```text
src/main/java/com/steph/simeme/
├── config/
│   ├── BirdeyeClientConfig.java       # Konfigurasi HTTP RestClient ke Birdeye API
│   ├── CorsConfig.java                # Konfigurasi CORS untuk akses Frontend
│   ├── DataSeeder.java                # Seeder 10 token awal & akun demo 'trader_satu'
│   └── WebSocketConfig.java           # Konfigurasi STOMP WebSocket Broker (/ws-simeme)
├── controller/
│   ├── TokenController.java           # Endpoint REST daftar token & tambah token via CA
│   └── TradingController.java         # Endpoint REST Buy, Sell, dan Portofolio
├── dto/
│   ├── AddTokenRequest.java           # Request DTO untuk tambah token via paste CA
│   ├── BirdeyePriceResponse.java      # Response DTO untuk GET /defi/price Birdeye
│   ├── BirdeyeTokenMetadataResponse.java # Response DTO metadata token Birdeye
│   ├── DexScreenerTokenResponse.java  # Response DTO fallback metadata DexScreener
│   ├── BuyOrderRequest.java           # Request DTO eksekusi beli
│   ├── SellOrderRequest.java          # Request DTO eksekusi jual (%)
│   ├── LivePriceTickDto.java          # Payload broadcast harga WebSocket
│   └── PortfolioResponse.java         # Payload portofolio & kalkulasi PnL
├── entity/
│   ├── User.java                      # Entitas pengguna
│   ├── Wallet.java                    # Entitas dompet USD (dengan @Version Optimistic Lock)
│   ├── Token.java                     # Entitas metadata & harga terakhir koin
│   ├── Position.java                  # Entitas kepemilikan koin aktif
│   └── TradeHistory.java              # Entitas riwayat transaksi & Realized PnL
├── repository/                        # Spring Data JPA Repositories
└── service/
    ├── BirdeyeMarketService.java      # Scheduler harga Birdeye & logika tambah token via CA
    ├── MarketBroadcastService.java    # Broadcaster STOMP WebSocket (/topic/prices & portfolio)
    ├── PriceCacheService.java         # Thread-safe In-Memory Cache (ConcurrentHashMap)
    └── TradingService.java            # Engine kalkulasi Slippage, Fee, Average Price & PnL
```
