# Virtual Threads Performance Benchmark Guide

## 📋 Giới thiệu

Hướng dẫn chi tiết về cách benchmark và so sánh hiệu năng giữa **Traditional Threads** và **Virtual Threads** trong Java 21+.

Virtual Threads là tính năng mới trong Java 21, giúp xử lý hàng triệu concurrent connections mà không cần nhiều platform threads.

---

## 🛠️ Chuẩn bị môi trường

### 1. Cài đặt Ubuntu trên WSL (Windows)

```bash
# Mở PowerShell as Administrator và chạy:
wsl --install

# Hoặc cài đặt Ubuntu cụ thể:
wsl --install -d Ubuntu-22.04

# Restart máy sau khi cài đặt
```

### 2. Cài đặt công cụ benchmark trong Ubuntu

```bash
# Mở Ubuntu terminal và chạy:
sudo apt update
sudo apt install wrk -y

# Verify cài đặt
wrk --version
```

### 3. Lấy IP của Windows host

```bash
# Trong Ubuntu WSL, chạy:
export WINDOWS_IP=$(ip route show | grep -i default | awk '{ print $3}')
echo $WINDOWS_IP

# Ví dụ output: 172.21.48.1
```

---

## 🔧 Setup Spring Boot Project

### 1. Tạo Controller để benchmark

**File: `BenchmarkController.java`**

```java
package com.example.virtualthreaddemo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/benchmark")
public class BenchmarkController {

    // Endpoint có blocking I/O nhẹ (100ms)
    @GetMapping("/light-io")
    public String lightIO() throws InterruptedException {
        Thread.sleep(100); // Giả lập DB query
        return "OK";
    }

    // Endpoint có blocking I/O nặng (500ms)
    @GetMapping("/heavy-io")
    public String heavyIO() throws InterruptedException {
        Thread.sleep(500); // Giả lập API call chậm
        return "OK";
    }

    // Endpoint kiểm tra thread type
    @GetMapping("/info")
    public String info() {
        Thread t = Thread.currentThread();
        return String.format(
                "Thread: %s, Virtual: %s, ID: %d",
                t.getName(),
                t.isVirtual(),
                t.threadId()
        );
    }
}
```

### 2. Cấu hình Virtual Threads

**File: `application.yml`**

```yaml
server:
  tomcat:
    threads:
      max: 200

spring:
  application:
    name: VIRTUAL-THREAD

  threads:
    virtual:
      enabled: true  # Đổi thành false để test Traditional Threads
```

**Để test Traditional Threads:**
```yaml
spring:
  threads:
    virtual:
      enabled: false  # TẮT Virtual Threads
```

**Để test Virtual Threads:**
```yaml
spring:
  threads:
    virtual:
      enabled: true  # BẬT Virtual Threads
```

---

## 🧪 Quy trình Test

### Phase 1: Test Traditional Threads

```bash
# 1. Mở Ubuntu WSL
wsl

# 2. Lấy IP Windows
export WINDOWS_IP=$(ip route show | grep -i default | awk '{ print $3}')
echo $WINDOWS_IP

# 3. Verify Traditional Threads đang chạy
curl http://$WINDOWS_IP:8080/benchmark/info
# Expected: "Virtual: false"

# 4. Benchmark với c=200 (low concurrency)
wrk -t12 -c200 -d10s http://$WINDOWS_IP:8080/benchmark/light-io

# 5. Benchmark với c=1000 (high concurrency)
wrk -t12 -c1000 -d10s http://$WINDOWS_IP:8080/benchmark/light-io
```

### Phase 2: Test Virtual Threads

```bash
# 1. Restart Spring Boot app với Virtual Threads BẬT
#    (Sửa application.yml: spring.threads.virtual.enabled: true)

# 2. Verify Virtual Threads đang chạy
curl http://$WINDOWS_IP:8080/benchmark/info
# Expected: "Virtual: true"

# 3. Benchmark với c=200
wrk -t12 -c200 -d10s http://$WINDOWS_IP:8080/benchmark/light-io

# 4. Benchmark với c=1000
wrk -t12 -c1000 -d10s http://$WINDOWS_IP:8080/benchmark/light-io
```

---

## 🚀 Quick Start - Chỉ cần copy & run

### Test nhanh (2 phút):

```bash
# 1. Mở Ubuntu WSL
wsl

# 2. Set IP và test Traditional
export WINDOWS_IP=$(ip route show | grep -i default | awk '{ print $3}')
curl http://$WINDOWS_IP:8080/benchmark/info
wrk -t12 -c200 -d10s http://$WINDOWS_IP:8080/benchmark/light-io
wrk -t12 -c1000 -d10s http://$WINDOWS_IP:8080/benchmark/light-io

# 3. Restart app với spring.threads.virtual.enabled: true

# 4. Test Virtual Threads
curl http://$WINDOWS_IP:8080/benchmark/info
wrk -t12 -c200 -d10s http://$WINDOWS_IP:8080/benchmark/light-io
wrk -t12 -c1000 -d10s http://$WINDOWS_IP:8080/benchmark/light-io
```

---

## 📊 Kết quả Benchmark

### Test Environment
- **Java Version**: 25
- **Spring Boot**: 4.0.1
- **OS**: Windows 11 + WSL Ubuntu
- **Endpoint**: 100ms sleep (giả lập I/O blocking)
- **Test Duration**: 10 giây mỗi test
- **Tool**: wrk

### Kết quả chi tiết

#### Test 1: Low Concurrency (c=200)

| Metric | Traditional Threads | Virtual Threads | So sánh |
|--------|---------------------|-----------------|---------|
| **Requests/sec** | 1,717.52 | 1,507.24 | Virtual **chậm hơn** 12% |
| **Latency (avg)** | 110.32ms | 121.23ms | Virtual chậm hơn 10ms |
| **Latency (max)** | 145.04ms | 160.78ms | - |
| **Timeouts** | 0 | 0 | Đều ổn định |
| **Total Requests** | 17,342 | 15,204 | Traditional nhiều hơn |

**💡 Kết luận c=200:**
- Virtual Threads **KHÔNG có lợi thế** khi connections thấp
- Traditional Threads thực sự **tốt hơn** vì không có overhead
- Lý do: 200 connections < 200 platform threads → Không bị bottleneck

---

#### Test 2: High Concurrency (c=1000) ⭐ QUAN TRỌNG NHẤT

| Metric | Traditional Threads | Virtual Threads | Cải thiện |
|--------|---------------------|-----------------|-----------|
| **Requests/sec** | 2,195.70 | **9,956.15** | **4.54x** 🚀 |
| **Latency (avg)** | 571.03ms | **125.54ms** | **4.55x nhanh hơn** 🔥 |
| **Latency (stdev)** | 107.39ms | 11.94ms | Ổn định hơn 9x |
| **Latency (max)** | 710.34ms | 203.04ms | 3.5x tốt hơn |
| **Timeouts** | 996 | 538 | Ít hơn 46% |
| **Total Requests** | 16,600 | **75,520** | **4.55x nhiều hơn** |
| **Transfer/sec** | 246.59KB | **1.09MB** | 4.54x |

**💥 Kết luận c=1000:**
- Virtual Threads **NHANH GẤP 4.5 LẦN!**
- Latency giảm từ **571ms → 125ms** (gần bằng baseline 100ms)
- Xử lý được **75,520 requests** trong 10s thay vì 16,600
- **Ít timeout hơn nhiều** (538 vs 996)

---

## 📈 Phân tích Chi tiết

### 1. Tại sao c=200 Virtual Threads CHẬM HƠN?

**Traditional Threads (c=200):**
```
200 connections / 200 platform threads = 1:1 mapping
✅ Mỗi connection có 1 dedicated thread
✅ Không có context switching overhead
✅ Performance tối ưu: 1,717 req/s
```

**Virtual Threads (c=200):**
```
200 virtual threads trên ~12 platform threads
❌ Overhead từ virtual thread scheduling
❌ Context switching giữa virtual threads
❌ Performance kém hơn: 1,507 req/s (-12%)
```

---

### 2. Tại sao c=1000 Virtual Threads NHANH GẤP 4.5 LẦN?

**Traditional Threads (c=1000):**
```
1000 connections / 200 platform threads = 5:1 ratio
❌ Mỗi thread phải xử lý 5 connections
❌ Thread bị BLOCK khi sleep(100ms)
❌ 4 connections khác phải ĐỢI trong hàng đợi
→ Latency tăng vọt: 571ms (từ 110ms)
→ Throughput thấp: 2,195 req/s
→ 996 requests timeout
```

**Virtual Threads (c=1000):**
```
1000 virtual threads, không giới hạn
✅ Mỗi connection có 1 virtual thread riêng
✅ Sleep(100ms) → virtual thread SUSPEND
✅ Platform thread NGAY LẬP TỨC xử lý virtual thread khác
✅ KHÔNG có thread nào bị lãng phí
→ Latency ổn định: 125ms (gần 100ms baseline)
→ Throughput cao: 9,956 req/s (4.5x) 🚀
→ Chỉ 538 timeouts
```

---

### 3. Breaking Point Analysis

```
┌─────────────────────────────────────────────────────────┐
│  Connections ≤ Thread Pool Size (≤200)                  │
│  → Traditional NHANH HƠN                                 │
│  → Virtual có overhead không cần thiết                   │
├─────────────────────────────────────────────────────────┤
│  Connections > Thread Pool Size (>500)                  │
│  → Virtual THẮNG ÁP ĐẢO (4-5x faster)                   │
│  → Traditional bị bottleneck nghiêm trọng               │
└─────────────────────────────────────────────────────────┘
```

**Biểu đồ Requests/sec:**
```
Connections:     200                         1000
              ┌───────┐                   ┌────────┐
Traditional:  │ 1,717 │                   │ 2,195  │
              └───────┘                   └────────┘
                                          
Virtual:      │ 1,507 │                   │ 9,956  │ ← 4.5x!
              └───────┘                   └────────┘
                ↓                             ↑
           Chậm hơn 12%              Nhanh gấp 4.5 lần! 🚀
```

**Biểu đồ Latency:**
```
Connections:     200                         1000
              ┌────────┐                 ┌──────────┐
Traditional:  │ 110ms  │                 │  571ms   │ ← Tăng 5x!
              └────────┘                 └──────────┘
                                          
Virtual:      │ 121ms  │                 │  125ms   │ ← Ổn định!
              └────────┘                 └──────────┘
```

---

## 🎯 Kết luận và Khuyến nghị

### Khi NÊN dùng Virtual Threads:

✅ **High concurrency** (>500-1000 concurrent connections)

✅ **Blocking I/O operations** (Database queries, API calls, File I/O)

✅ **Microservices** gọi nhiều external services

✅ **Web servers** với nhiều simultaneous requests

✅ **REST APIs** với high traffic

### Khi KHÔNG NÊN dùng Virtual Threads:

❌ **Low concurrency** (<200 connections)

❌ **CPU-intensive tasks** (không có I/O blocking)

❌ **Latency-critical** applications với very low concurrency

❌ **Legacy systems** chưa hỗ trợ Java 21+

---

## 🔑 Key Takeaways

1. **Virtual Threads KHÔNG phải lúc nào cũng tốt hơn**
    - c=200: Chậm hơn 12%
    - c=1000: Nhanh hơn 4.5x

2. **Breaking point ở ~500-1000 connections**
    - Dưới 500: Traditional tốt hơn hoặc ngang bằng
    - Trên 1000: Virtual thắng áp đảo

3. **Virtual Threads shine với blocking I/O**
    - Không block platform threads
    - Tận dụng tối đa CPU
    - Scale tốt với millions of threads

4. **Băng thông không phải vấn đề**
    - Mỗi request chiếm đúng 117 bytes (giống nhau)
    - Transfer/sec cao hơn vì xử lý NHIỀU requests hơn
    - Không "tốn mạng hơn", chỉ "làm việc nhiều hơn"

---

## 📚 Tài liệu tham khảo

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Spring Boot Virtual Threads Support](https://spring.io/blog/2023/09/09/all-together-now-spring-boot-3-2-graalvm-native-images-java-21-and-virtual)
- [wrk - Modern HTTP benchmarking tool](https://github.com/wg/wrk)

---

## 📝 Notes

- Test được thực hiện trên môi trường development, production có thể khác
- Kết quả có thể thay đổi tùy thuộc vào phần cứng và cấu hình
- Nên test với workload thực tế của ứng dụng để quyết định
- Virtual Threads vẫn là technology mới, cần cân nhắc kỹ trước khi áp dụng production

---

**Tác giả**: duclk  
**Ngày**: 04/01/2026  
**Java Version**: 25  
**Spring Boot**: 4.0.1
